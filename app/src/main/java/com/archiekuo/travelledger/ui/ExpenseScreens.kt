@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.archiekuo.travelledger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.Category
import com.archiekuo.travelledger.data.PaymentMethod
import com.archiekuo.travelledger.data.PhotoType
import com.archiekuo.travelledger.data.TitleSuggestion
import com.archiekuo.travelledger.logic.ReceiptGuess
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Everything the expense editor can ask for; defaults make previews and snapshots easy. */
data class ExpenseActions(
    val edit: ((EditState) -> EditState) -> Unit = {},
    val key: (String) -> Unit = {},
    val currency: (String) -> Unit = {},
    val pickSuggestion: (TitleSuggestion) -> Unit = {},
    val takeReceipt: () -> Unit = {},
    val takePhoto: () -> Unit = {},
    val pickPhoto: () -> Unit = {},
    val removePhoto: (Int) -> Unit = {},
    val setPhotoType: (Int, String) -> Unit = { _, _ -> },
    val savePhotoToGallery: (Int) -> Unit = {},
    val applyReceipt: () -> Unit = {},
    val dismissReceipt: () -> Unit = {},
    val save: () -> Unit = {},
    val delete: () -> Unit = {},
    val back: () -> Unit = {},
)

@Composable
fun ExpenseEditScreen(
    state: EditState?,
    isNew: Boolean,
    categories: List<Category>,
    methods: List<PaymentMethod>,
    photos: List<PhotoItem>,
    suggestions: List<TitleSuggestion>,
    currencies: List<String>,
    receipt: ReceiptGuess?,
    processing: Boolean,
    actions: ExpenseActions,
    viewingPhoto: Int? = null,
) {
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var editRate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf(viewingPhoto) }
    val imeVisible = WindowInsets.isImeVisible

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                AppTopBar(
                    if (isNew) "記一筆" else "編輯", onBack = actions.back, closeIcon = true,
                    actions = {
                        if (state != null) DateTimeChip(state) { pickDate = true }
                        if (!isNew) IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "刪除") }
                    },
                )
            },
        ) { pad ->
            val s = state ?: return@Scaffold
            Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NameRow(s, suggestions, actions)
                    PhotoButtons(photos.size, processing, actions)
                    if (photos.isNotEmpty()) PhotoStrip(photos, onOpen = { viewing = it })
                    receipt?.let { ReceiptCard(it, actions) }
                    CategoryRow(categories, s.categoryId) { id -> actions.edit { it.copy(categoryId = id) } }
                    PaymentRow(methods, s.paymentId) { id -> actions.edit { it.copy(paymentId = id) } }
                    NoteField(s, actions)
                }
                if (!imeVisible) {
                    KeypadPanel(
                        amount = s.amount, currency = s.currency, currencies = currencies,
                        rate = s.rate, homeAmount = s.homeAmount, canSave = s.canSave,
                        onKey = actions.key, onCurrency = actions.currency,
                        onOtherCurrency = { pickCurrency = true }, onEditRate = { editRate = true },
                        onSave = actions.save,
                    )
                }
            }

            if (pickCurrency) CurrencyDialog(s.currency, { pickCurrency = false }) { actions.currency(it); pickCurrency = false }
            if (editRate) {
                TextInputDialog(
                    "1 ${s.currency} = ? 新台幣", "匯率", initial = s.rate, decimal = true,
                    onDismiss = { editRate = false },
                ) { v -> actions.edit { it.copy(rate = v) }; editRate = false }
            }
            if (pickDate) {
                DayPickerDialog("日期", s.date, confirmText = "下一步:時間", onDismiss = { pickDate = false }) { day ->
                    actions.edit { it.copy(date = day) }
                    pickDate = false
                    pickTime = true
                }
            }
            if (pickTime) {
                val m = s.minuteOfDay ?: 12 * 60
                val st = rememberTimePickerState(m / 60, m % 60, is24Hour = true)
                AlertDialog(
                    onDismissRequest = { pickTime = false },
                    title = { Text("時間") },
                    text = { TimePicker(st) },
                    confirmButton = {
                        TextButton({ actions.edit { it.copy(minuteOfDay = st.hour * 60 + st.minute) }; pickTime = false }) { Text("確定") }
                    },
                    dismissButton = {
                        TextButton({ actions.edit { it.copy(minuteOfDay = null) }; pickTime = false }) { Text("不記時間") }
                    },
                )
            }
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    icon = { Icon(Icons.Rounded.DeleteOutline, null) },
                    title = { Text("刪除這筆支出?") },
                    text = { Text("照片也會一併刪除。") },
                    confirmButton = { TextButton({ confirmDelete = false; actions.delete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
                )
            }
        }

        viewing?.let { index ->
            if (index in photos.indices) {
                PhotoViewer(
                    photos, index,
                    onClose = { viewing = null },
                    onType = actions.setPhotoType,
                    onDelete = { i -> actions.removePhoto(i); viewing = null },
                    onSave = actions.savePhotoToGallery,
                )
            }
        }
    }
}

@Composable
private fun DateTimeChip(s: EditState, onClick: () -> Unit) {
    Row(
        Modifier.padding(end = 4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, ledger.hairline, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Schedule, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(fmtShortDateTime(s.date, s.minuteOfDay), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NameRow(s: EditState, suggestions: List<TitleSuggestion>, actions: ExpenseActions) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().height(56.dp).clip(shape).background(cs.surfaceContainerLowest)
                .border(if (focused) 1.5.dp else 1.dp, if (focused) cs.primary else ledger.hairline, shape)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Storefront, null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            val style = MaterialTheme.typography.titleMedium.copy(color = cs.onSurface)
            val focus = LocalFocusManager.current
            BasicTextField(
                s.title, { v -> actions.edit { it.copy(title = v) } }, Modifier.weight(1f),
                singleLine = true, textStyle = style, cursorBrush = SolidColor(cs.primary),
                interactionSource = interaction,
                // "完成" closes the keyboard so the calculator comes back right away.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                decorationBox = { inner ->
                    if (s.title.isEmpty()) Text("店家或項目名稱", style = style.copy(fontWeight = FontWeight.Normal), color = ledger.textMuted)
                    inner()
                },
            )
            if (s.title.isNotEmpty()) {
                IconButton({ actions.edit { it.copy(title = "") } }, Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Cancel, "清除", Modifier.size(20.dp), tint = ledger.textMuted)
                }
            }
        }
        if (focused) {
            val q = s.title.trim()
            val shown = suggestions.asSequence()
                .filter { q.isEmpty() || (it.title.contains(q, ignoreCase = true) && it.title != q) }
                .take(8).toList()
            if (shown.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.title }) { sg ->
                        val focus = LocalFocusManager.current
                        SuggestionChip(onClick = { actions.pickSuggestion(sg); focus.clearFocus() }, label = { Text(sg.title, style = MaterialTheme.typography.bodyMedium) })
                    }
                }
            }
        }
    }
}

/** 拍收據 (read it) · 拍照 (memory, no reading) · 相簿 (decide from the photo). */
@Composable
private fun PhotoButtons(count: Int, processing: Boolean, actions: ExpenseActions) {
    val full = count >= MAX_PHOTOS
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoButton("拍收據", Icons.Rounded.Receipt, !full && !processing, Modifier.weight(1f), actions.takeReceipt)
            PhotoButton("拍照", Icons.Rounded.PhotoCamera, !full && !processing, Modifier.weight(1f), actions.takePhoto)
            PhotoButton("相簿", Icons.Rounded.PhotoLibrary, !full && !processing, Modifier.weight(1f), actions.pickPhoto)
        }
        when {
            processing -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("處理照片中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            full -> Text("已滿 $MAX_PHOTOS 張", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun PhotoButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(46.dp).clip(shape).background(if (enabled) cs.primaryContainer else cs.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (enabled) cs.primary else ledger.textMuted)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) cs.onPrimaryContainer else ledger.textMuted, maxLines = 1)
    }
}

@Composable
internal fun PhotoStrip(photos: List<PhotoItem>, onOpen: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        photos.forEachIndexed { i, p ->
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpen(i) }) {
                val img by rememberLocalImage(p.path, targetPx = 240)
                img?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    ?: Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
                PhotoTypeBadge(p.type, Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
        }
    }
}

@Composable
fun PhotoTypeBadge(type: String, modifier: Modifier = Modifier) {
    val receipt = type == PhotoType.RECEIPT
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (receipt) Icons.Rounded.Receipt else Icons.Rounded.Image, null, Modifier.size(12.dp), tint = Color.White)
        Spacer(Modifier.width(3.dp))
        Text(if (receipt) "收據" else "回憶", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReceiptCard(g: ReceiptGuess, actions: ExpenseActions) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier.fillMaxWidth().clip(shape).background(cs.primaryContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = cs.primary)
            Spacer(Modifier.width(6.dp))
            Text("從收據讀到", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
        }
        g.store?.let { Text("店家:$it", style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer) }
        g.total?.let { Text("金額:${fmtAmount(it, g.currency ?: "")}".replace(": ", ":"), style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer) }
        g.date?.let { d ->
            Text("時間:${fmtShortDateTime(d.toEpochDay(), g.minuteOfDay)}", style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(actions.dismissReceipt) { Text("略過") }
            Spacer(Modifier.width(4.dp))
            Button(actions.applyReceipt, shape = RoundedCornerShape(12.dp)) { Text("套用") }
        }
    }
}

@Composable
fun CategoryRow(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(categories, key = { it.id }) { c ->
            val style = categoryStyle(c.name, c.icon, c.color)
            val on = c.id == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier.width(70.dp).clip(shape)
                    .background(if (on) style.color.copy(alpha = if (ledger.dark) 0.20f else 0.12f) else MaterialTheme.colorScheme.surfaceContainerLowest)
                    .border(if (on) 2.dp else 1.dp, if (on) style.color else ledger.hairline, shape)
                    .clickable { onSelect(c.id) }
                    .padding(vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconTile(style.icon, style.color, size = 36.dp, corner = 11.dp)
                Spacer(Modifier.height(5.dp))
                Text(
                    c.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(methods: List<PaymentMethod>, selected: Long?, onSelect: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    // With large text, drop the icons so three methods still fit on one line.
    val showIcon = androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.1f
    @Composable
    fun Option(m: PaymentMethod, modifier: Modifier) {
        val on = m.id == selected
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier.height(46.dp).clip(shape)
                .background(if (on) cs.primaryContainer else cs.surfaceContainerLowest)
                .border(if (on) 1.5.dp else 1.dp, if (on) cs.primary else ledger.hairline, shape)
                .clickable { onSelect(m.id) }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            if (showIcon) {
                Icon(paymentIcon(m.name), null, Modifier.size(19.dp), tint = if (on) cs.primary else cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Text(m.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, color = if (on) cs.onPrimaryContainer else cs.onSurface)
        }
    }
    if (methods.size <= 3) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { methods.forEach { Option(it, Modifier.weight(1f)) } }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(methods, key = { it.id }) { Option(it, Modifier.widthIn(min = 96.dp)) } }
    }
}

@Composable
private fun NoteField(s: EditState, actions: ExpenseActions) {
    if (!s.noteOpen) {
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable { actions.edit { it.copy(noteOpen = true) } }.padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text("加備註", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        FieldBox("備註") { FieldInput(s.note, { v -> actions.edit { it.copy(note = v) } }, "例如:點了特製拉麵") }
        LinkButtons(s.note)
    }
}

@Composable
fun CurrencyDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇幣別") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMMON_CURRENCIES.forEach { c -> SelectPill(c, c == current, { onPick(c) }) }
                }
                OutlinedTextField(
                    custom, { custom = it.uppercase().filter { c -> c.isLetter() }.take(3) },
                    label = { Text("其他幣別代碼(3 碼)") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = { TextButton({ onPick(custom) }, enabled = custom.length == 3) { Text("使用") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
