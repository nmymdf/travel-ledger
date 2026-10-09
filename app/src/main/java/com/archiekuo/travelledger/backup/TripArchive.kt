package com.archiekuo.travelledger.backup

import androidx.room.withTransaction
import com.archiekuo.travelledger.data.AppDatabase
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.Expense
import com.archiekuo.travelledger.data.Member
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.Photo
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.PlanItem
import com.archiekuo.travelledger.data.Trip
import com.archiekuo.travelledger.data.TripCurrencyRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * One zip file for both jobs:
 *  - a full backup (kind "backup") that restores every trip on a new phone, and
 *  - one trip shared with companions (kind "trip"), imported on their phone as a read-only copy.
 *
 * Layout: manifest.json (what it is), data.json (trips and the category/payment names they use),
 * files/… (photos and covers, only when asked for). Rows refer to each other by position, never by
 * database id, so the file can be imported into any database.
 */
object TripArchive {
    const val FORMAT = "travelledger"
    const val VERSION = 1
    const val KIND_TRIP = "trip"
    const val KIND_BACKUP = "backup"

    data class TripInfo(val uuid: String, val name: String, val startDate: Long, val endDate: Long, val expenseCount: Int, val totalHome: Double)

    /** What the file holds, read before asking the user to confirm. */
    data class Summary(
        val kind: String,
        val sharedBy: String?,
        val exportedAt: Long,
        val trips: List<TripInfo>,
        val photoCount: Int,
    )

    sealed interface Result {
        /** [tripIds] are the local ids of the trips written (in file order). */
        data class Imported(val kind: String, val tripIds: List<Long>, val updated: Int) : Result
        data class Failed(val message: String) : Result
    }

    class BadArchive(message: String) : Exception(message)

    // ───────────────────────────── export ─────────────────────────────

    /**
     * Writes [tripIds] to [out]. For a shared trip, [sharedBy] is the sender's name shown on the
     * companion's copy; a trip that was itself shared with us keeps its original sender.
     */
    suspend fun export(
        db: AppDatabase,
        out: OutputStream,
        kind: String,
        tripIds: List<Long>,
        includePhotos: Boolean,
        sharedBy: String? = null,
        appVersion: String = "",
        now: Long = System.currentTimeMillis(),
    ): Int = withContext(Dispatchers.IO) {
        val categories = db.lookupDao().getCategories()
        val methods = db.lookupDao().getPaymentMethods()
        val files = mutableListOf<Pair<String, File>>()
        fun attach(path: String?, folder: String): String? {
            if (!includePhotos || path == null) return null
            val f = File(path)
            if (!f.isFile) return null
            val name = "files/$folder/${files.size}-${f.name}"
            files += name to f
            return name
        }

        val trips = JSONArray()
        for (id in tripIds) {
            val trip = db.tripDao().getTrip(id) ?: continue
            val members = db.tripDao().getMembers(id)
            val memberIndex = members.withIndex().associate { (i, m) -> m.id to i }
            val plans = db.planDao().forTrip(id)
            val planIndex = plans.withIndex().associate { (i, p) -> p.id to i }
            val expenses = db.expenseDao().forTrip(id)
            trips.put(JSONObject().apply {
                put("uuid", trip.uuid.ifBlank { UUID.randomUUID().toString() })
                put("name", trip.name)
                put("startDate", trip.startDate)
                put("endDate", trip.endDate)
                put("homeCurrency", trip.homeCurrency)
                putOpt("budget", trip.budget)
                put("splitEnabled", trip.splitEnabled)
                put("createdAt", trip.createdAt)
                putOpt("coverTheme", trip.coverTheme)
                putOpt("cover", attach(trip.coverPath, "covers"))
                // A backup remembers which trips were someone else's; a share names the sender.
                putOpt("sharedBy", if (kind == KIND_TRIP) trip.sharedBy ?: sharedBy else trip.sharedBy)
                putOpt("sharedAt", if (kind == KIND_TRIP) trip.sharedAt.takeIf { trip.sharedBy != null } ?: now else trip.sharedAt)
                put("members", JSONArray(members.map { it.name }))
                put("rates", JSONArray(db.tripDao().getRates(id).map { r ->
                    JSONObject().put("currency", r.currency).put("rate", r.rate).put("updatedAt", r.updatedAt).put("source", r.source)
                }))
                put("plans", JSONArray(plans.map { p ->
                    JSONObject().apply {
                        put("title", p.title)
                        putOpt("category", categories.firstOrNull { it.id == p.categoryId }?.name)
                        putOpt("date", p.date)
                        putOpt("minuteOfDay", p.minuteOfDay)
                        put("status", p.status)
                        put("reservation", p.reservation)
                        put("reservationNote", p.reservationNote)
                        put("location", p.location)
                        putOpt("estCost", p.estCost)
                        put("note", p.note)
                        put("createdAt", p.createdAt)
                    }
                }))
                put("expenses", JSONArray(expenses.map { e ->
                    JSONObject().apply {
                        put("date", e.date)
                        putOpt("minuteOfDay", e.minuteOfDay)
                        put("title", e.title)
                        put("amount", e.amount)
                        put("currency", e.currency)
                        put("rate", e.rate)
                        put("homeAmount", e.homeAmount)
                        putOpt("category", categories.firstOrNull { it.id == e.categoryId }?.name)
                        putOpt("payment", methods.firstOrNull { it.id == e.paymentMethodId }?.name)
                        putOpt("payer", e.payerId?.let { memberIndex[it] })
                        put("note", e.note)
                        put("ocrText", e.ocrText)
                        put("createdAt", e.createdAt)
                        putOpt("plan", e.planItemId?.let { planIndex[it] })
                        put("photos", JSONArray(db.photoDao().forExpense(e.id).mapNotNull { ph ->
                            attach(ph.path, if (ph.type == PhotoType.RECEIPT) "receipts" else "memories")?.let { name ->
                                JSONObject().put("file", name).put("type", ph.type).put("width", ph.width).put("height", ph.height).put("createdAt", ph.createdAt)
                            }
                        }))
                    }
                }))
            })
        }

        val data = JSONObject().apply {
            put("categories", JSONArray(categories.map { c ->
                JSONObject().put("name", c.name).put("sortOrder", c.sortOrder).put("icon", c.icon).put("color", c.color)
            }))
            put("payments", JSONArray(methods.map { m -> JSONObject().put("name", m.name).put("sortOrder", m.sortOrder) }))
            put("trips", trips)
        }
        val manifest = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("kind", kind)
            put("exportedAt", now)
            putOpt("sharedBy", if (kind == KIND_TRIP) sharedBy else null)
            put("appVersion", appVersion)
            put("photoCount", files.size)
            put("trips", JSONArray((0 until trips.length()).map { i ->
                val t = trips.getJSONObject(i)
                val ex = t.getJSONArray("expenses")
                JSONObject().put("uuid", t.getString("uuid")).put("name", t.getString("name"))
                    .put("startDate", t.getLong("startDate")).put("endDate", t.getLong("endDate"))
                    .put("expenseCount", ex.length())
                    .put("totalHome", (0 until ex.length()).sumOf { ex.getJSONObject(it).getDouble("homeAmount") })
            }))
        }

        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString(1).toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("data.json")); zip.write(data.toString().toByteArray()); zip.closeEntry()
            for ((name, file) in files) {
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        trips.length()
    }

    // ───────────────────────────── read ─────────────────────────────

    private fun readJson(zip: ZipFile, name: String): JSONObject {
        val entry = zip.getEntry(name) ?: throw BadArchive("這不是旅帳的檔案")
        return JSONObject(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
    }

    private fun openManifest(zip: ZipFile): JSONObject {
        val m = readJson(zip, "manifest.json")
        if (m.optString("format") != FORMAT) throw BadArchive("這不是旅帳的檔案")
        if (m.optInt("version") > VERSION) throw BadArchive("這個檔案來自較新版的旅帳,請先更新 App")
        return m
    }

    /** Reads only the manifest, for the confirmation dialog. */
    fun readSummary(file: File): Summary = try {
        ZipFile(file).use { zip ->
            val m = openManifest(zip)
            val trips = m.optJSONArray("trips") ?: JSONArray()
            Summary(
                kind = m.optString("kind", KIND_BACKUP),
                sharedBy = m.optStringOrNull("sharedBy"),
                exportedAt = m.optLong("exportedAt"),
                trips = (0 until trips.length()).map { i ->
                    val t = trips.getJSONObject(i)
                    TripInfo(t.getString("uuid"), t.getString("name"), t.getLong("startDate"), t.getLong("endDate"), t.optInt("expenseCount"), t.optDouble("totalHome", 0.0))
                },
                photoCount = m.optInt("photoCount"),
            )
        }
    } catch (e: BadArchive) {
        throw e
    } catch (e: Exception) {
        throw BadArchive("這不是旅帳的檔案")
    }

    // ───────────────────────────── import ─────────────────────────────

    /**
     * Imports [file] into [db], copying photos under [filesDir].
     *  - A shared trip becomes a read-only copy; sharing it again replaces that copy (matched by uuid).
     *    Our own trip coming back to us is refused rather than overwritten.
     *  - A backup restores every trip: one already on the phone (same uuid) is replaced, others are added.
     * Category and payment names missing on this phone are added.
     */
    suspend fun import(db: AppDatabase, file: File, filesDir: File): Result = withContext(Dispatchers.IO) {
        try {
            ZipFile(file).use { zip -> importZip(db, zip, filesDir) }
        } catch (e: BadArchive) {
            Result.Failed(e.message ?: "無法讀取檔案")
        } catch (e: Exception) {
            Result.Failed("檔案損壞,無法匯入")
        }
    }

    private suspend fun importZip(db: AppDatabase, zip: ZipFile, filesDir: File): Result {
        val manifest = openManifest(zip)
        val kind = manifest.optString("kind", KIND_BACKUP)
        val data = readJson(zip, "data.json")
        val trips = data.getJSONArray("trips")

        if (kind == KIND_TRIP) {
            for (i in 0 until trips.length()) {
                val existing = db.tripDao().findByUuid(trips.getJSONObject(i).getString("uuid"))
                if (existing != null && !existing.readOnly) {
                    return Result.Failed("「${existing.name}」是你自己記的旅程,不需要匯入")
                }
            }
        }

        // Copy files first (outside the transaction); remember them so a failure leaves nothing behind.
        val copied = mutableMapOf<String, String>()
        fun fileFor(name: String?): String? {
            if (name == null) return null
            copied[name]?.let { return it }
            val entry = zip.getEntry(name) ?: return null
            if (entry.isDirectory || name.contains("..")) return null
            val folder = when {
                name.startsWith("files/covers/") -> "covers"
                name.startsWith("files/receipts/") -> "photos/receipts"
                else -> "photos/memories"
            }
            val dir = File(filesDir, folder).apply { mkdirs() }
            val dst = File(dir, "${UUID.randomUUID()}.jpg")
            zip.getInputStream(entry).use { input -> dst.outputStream().use { input.copyTo(it) } }
            copied[name] = dst.absolutePath
            return dst.absolutePath
        }

        val oldFiles = mutableListOf<String>()
        val ids = mutableListOf<Long>()
        var updated = 0
        try {
            // Resolve all files up front so the transaction only touches the database.
            for (i in 0 until trips.length()) {
                val t = trips.getJSONObject(i)
                fileFor(t.optStringOrNull("cover"))
                val ex = t.getJSONArray("expenses")
                for (j in 0 until ex.length()) {
                    val photos = ex.getJSONObject(j).optJSONArray("photos") ?: continue
                    for (k in 0 until photos.length()) fileFor(photos.getJSONObject(k).getString("file"))
                }
            }

            db.withTransaction {
                val categoryIds = mergeCategories(db, data.optJSONArray("categories"))
                val paymentIds = mergePayments(db, data.optJSONArray("payments"))
                val fallbackSharedBy = manifest.optStringOrNull("sharedBy")?.ifBlank { null } ?: "同伴"
                for (i in 0 until trips.length()) {
                    val t = trips.getJSONObject(i)
                    val uuid = t.getString("uuid")
                    val existing = db.tripDao().findByUuid(uuid)
                    val sharedBy = if (kind == KIND_TRIP) t.optStringOrNull("sharedBy") ?: fallbackSharedBy else t.optStringOrNull("sharedBy")
                    val trip = Trip(
                        id = existing?.id ?: 0,
                        name = t.getString("name"),
                        startDate = t.getLong("startDate"),
                        endDate = t.getLong("endDate"),
                        homeCurrency = t.optString("homeCurrency", "TWD"),
                        budget = t.optDoubleOrNull("budget"),
                        splitEnabled = t.optBoolean("splitEnabled"),
                        createdAt = t.optLong("createdAt", System.currentTimeMillis()),
                        coverPath = copied[t.optStringOrNull("cover")],
                        coverTheme = t.optStringOrNull("coverTheme"),
                        uuid = uuid,
                        sharedBy = sharedBy,
                        sharedAt = if (sharedBy != null) t.optLongOrNull("sharedAt") ?: manifest.optLong("exportedAt") else null,
                    )
                    val tripId = if (existing != null) {
                        existing.coverPath?.let(oldFiles::add)
                        oldFiles += db.photoDao().pathsForTrip(existing.id)
                        db.tripDao().clearContents(existing.id)
                        db.tripDao().updateTrip(trip)
                        updated++
                        existing.id
                    } else {
                        db.tripDao().insertTrip(trip)
                    }
                    ids += tripId
                    writeContents(db, tripId, t, categoryIds, paymentIds, copied)
                }
            }
        } catch (e: Exception) {
            copied.values.forEach { File(it).delete() }
            throw e
        }
        oldFiles.forEach { File(it).delete() }
        return Result.Imported(kind, ids, updated)
    }

    private suspend fun mergeCategories(db: AppDatabase, list: JSONArray?): Map<String, Long> {
        val dao = db.lookupDao()
        val have = dao.getCategories().associateBy { it.name }.toMutableMap()
        var next = (have.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        if (list != null) for (i in 0 until list.length()) {
            val c = list.getJSONObject(i)
            val name = c.getString("name")
            if (name !in have) {
                val id = dao.insertCategory(Category(name = name, sortOrder = next++, icon = c.optString("icon"), color = c.optInt("color", -1)))
                have[name] = Category(id = id, name = name)
            }
        }
        return have.mapValues { it.value.id }
    }

    private suspend fun mergePayments(db: AppDatabase, list: JSONArray?): Map<String, Long> {
        val dao = db.lookupDao()
        val have = dao.getPaymentMethods().associateBy { it.name }.toMutableMap()
        var next = (have.values.maxOfOrNull { it.sortOrder } ?: -1) + 1
        if (list != null) for (i in 0 until list.length()) {
            val name = list.getJSONObject(i).getString("name")
            if (name !in have) {
                val id = dao.insertPaymentMethod(PaymentMethod(name = name, sortOrder = next++))
                have[name] = PaymentMethod(id = id, name = name)
            }
        }
        return have.mapValues { it.value.id }
    }

    private suspend fun writeContents(
        db: AppDatabase, tripId: Long, t: JSONObject,
        categoryIds: Map<String, Long>, paymentIds: Map<String, Long>, copied: Map<String, String>,
    ) {
        val names = t.getJSONArray("members").let { a -> (0 until a.length()).map { a.getString(it) } }
        val memberIds = names.map { db.tripDao().insertMember(Member(tripId = tripId, name = it)) }
        val rates = t.getJSONArray("rates")
        for (i in 0 until rates.length()) {
            val r = rates.getJSONObject(i)
            db.tripDao().upsertRate(TripCurrencyRate(tripId, r.getString("currency"), r.getDouble("rate"), r.optLong("updatedAt"), r.optString("source", "manual")))
        }
        val plans = t.getJSONArray("plans")
        val planIds = (0 until plans.length()).map { i ->
            val p = plans.getJSONObject(i)
            db.planDao().insert(
                PlanItem(
                    tripId = tripId,
                    title = p.getString("title"),
                    categoryId = p.optStringOrNull("category")?.let(categoryIds::get),
                    date = p.optLongOrNull("date"),
                    minuteOfDay = p.optLongOrNull("minuteOfDay")?.toInt(),
                    status = p.optString("status", "TODO"),
                    reservation = p.optString("reservation", "NONE"),
                    reservationNote = p.optString("reservationNote"),
                    location = p.optString("location"),
                    estCost = p.optDoubleOrNull("estCost"),
                    note = p.optString("note"),
                    createdAt = p.optLong("createdAt"),
                )
            )
        }
        val expenses = t.getJSONArray("expenses")
        for (i in 0 until expenses.length()) {
            val e = expenses.getJSONObject(i)
            val id = db.expenseDao().insert(
                Expense(
                    tripId = tripId,
                    date = e.getLong("date"),
                    amount = e.getDouble("amount"),
                    currency = e.getString("currency"),
                    rate = e.getDouble("rate"),
                    homeAmount = e.getDouble("homeAmount"),
                    categoryId = e.optStringOrNull("category")?.let(categoryIds::get),
                    paymentMethodId = e.optStringOrNull("payment")?.let(paymentIds::get),
                    payerId = e.optLongOrNull("payer")?.toInt()?.let(memberIds::getOrNull),
                    note = e.optString("note"),
                    createdAt = e.optLong("createdAt"),
                    title = e.optString("title"),
                    minuteOfDay = e.optLongOrNull("minuteOfDay")?.toInt(),
                    ocrText = e.optString("ocrText"),
                    planItemId = e.optLongOrNull("plan")?.toInt()?.let(planIds::getOrNull),
                )
            )
            val photos = e.optJSONArray("photos") ?: continue
            for (k in 0 until photos.length()) {
                val ph = photos.getJSONObject(k)
                val path = copied[ph.getString("file")] ?: continue
                db.photoDao().insert(
                    Photo(
                        expenseId = id, type = ph.optString("type", PhotoType.MEMORY), path = path,
                        width = ph.optInt("width"), height = ph.optInt("height"), createdAt = ph.optLong("createdAt"),
                    )
                )
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (isNull(key)) null else optDouble(key)
}
