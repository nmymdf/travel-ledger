package com.archiekuo.travelledger.photo

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.logic.OcrLine
import com.archiekuo.travelledger.logic.ReceiptGuess
import com.archiekuo.travelledger.logic.ReceiptParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A photo saved to app storage and what OCR made of it. */
data class ProcessedPhoto(
    val path: String,
    val type: String,
    val width: Int,
    val height: Int,
    val text: String,
    val guess: ReceiptGuess,
)

/** Camera/gallery photos: OCR on the full image, then a compressed copy in receipts/ or memories/. */
object PhotoProcessor {
    private const val OCR_EDGE = 2400
    private const val RECEIPT_EDGE = 1600
    private const val MEMORY_EDGE = 1200

    /** A fresh file + content Uri for the system camera to write into. */
    fun newCaptureUri(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun decode(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun recognizerFor(currencyHint: String?): TextRecognizerOptionsInterface = when (currencyHint) {
        "JPY" -> JapaneseTextRecognizerOptions.Builder().build()
        "KRW" -> KoreanTextRecognizerOptions.Builder().build()
        else -> ChineseTextRecognizerOptions.Builder().build() // also reads Latin text
    }

    /**
     * Recognizes text in the photo at [uri], decides receipt vs memory, and stores a compressed copy.
     * [mode] forces receipt or memory handling (null decides from the content).
     * [currencyHint] picks the OCR script (JPY → Japanese, KRW → Korean, otherwise Chinese+Latin).
     */
    suspend fun process(context: Context, uri: Uri, currencyHint: String?, mode: String? = null): ProcessedPhoto = withContext(Dispatchers.Default) {
        val full = withContext(Dispatchers.IO) { decode(context, uri, OCR_EDGE) }
        // "Take a photo" is a memory: skip recognition entirely.
        if (mode == PhotoType.MEMORY) {
            val stored = withContext(Dispatchers.IO) { store(context, full, PhotoType.MEMORY) }
            return@withContext ProcessedPhoto(stored.first, PhotoType.MEMORY, stored.second, stored.third, "", ReceiptGuess(false))
        }
        val lines = runCatching {
            val recognizer = TextRecognition.getClient(recognizerFor(currencyHint))
            try {
                val result = recognizer.process(InputImage.fromBitmap(full, 0)).await()
                result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) }
                    }
                }
            } finally {
                recognizer.close()
            }
        }.getOrDefault(emptyList())
        val parsed = ReceiptParser.parse(lines)
        // A photo taken with "拍收據" is a receipt even if the layout was hard to read.
        val guess = if (mode == PhotoType.RECEIPT && !parsed.isReceipt) {
            val all = lines.joinToString("\n") { it.text }
            ReceiptGuess(
                true, ReceiptParser.findStore(lines), ReceiptParser.findTotal(lines),
                ReceiptParser.findCurrency(all), ReceiptParser.findDate(all), ReceiptParser.findTime(all),
            )
        } else parsed
        val type = if (guess.isReceipt) PhotoType.RECEIPT else PhotoType.MEMORY
        val path = withContext(Dispatchers.IO) { store(context, full, type) }
        val text = lines.sortedBy { it.top }.joinToString("\n") { it.text }
        ProcessedPhoto(path.first, type, path.second, path.third, text, guess)
    }

    private fun store(context: Context, bitmap: Bitmap, type: String): Triple<String, Int, Int> {
        val edge = if (type == PhotoType.RECEIPT) RECEIPT_EDGE else MEMORY_EDGE
        val scale = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        val dir = File(context.filesDir, if (type == PhotoType.RECEIPT) "photos/receipts" else "photos/memories").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        return Triple(file.absolutePath, scaled.width, scaled.height)
    }

    /** Moves a stored photo to the folder for its (changed) type. Returns the new path. */
    fun moveToType(context: Context, path: String, type: String): String {
        val dir = File(context.filesDir, if (type == PhotoType.RECEIPT) "photos/receipts" else "photos/memories").apply { mkdirs() }
        val src = File(path)
        val dst = File(dir, src.name)
        if (src.absolutePath == dst.absolutePath) return path
        return if (src.renameTo(dst)) dst.absolutePath else path
    }

    /** Copies an image into the phone gallery under Pictures/旅帳. No permission needed on Android 10+. */
    suspend fun saveToGallery(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "旅帳_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/旅帳")
            }
            val resolver = context.contentResolver
            val dest = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            resolver.openOutputStream(dest)?.use { out -> resolver.openInputStream(source)?.use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }
}
