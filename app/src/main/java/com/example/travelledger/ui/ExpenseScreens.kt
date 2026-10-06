@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.travelledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.travelledger.data.Category
import com.example.travelledger.data.HOME_CURRENCY
import com.example.travelledger.data.PaymentMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ExpenseEditScreen(
    state: EditState?,
    categories: List<Category>,
    methods: List<PaymentMethod>,
    isNew: Boolean,
    keypadOpen: Boolean,
    onKeypadOpen: (Boolean) -> Unit,
    onEdit: ((EditState) -> EditState) -> Unit,
    onKey: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                if (isNew) "記一筆" else "編輯支出", onBack = onBack, closeIcon = true,
                actions = { ConfirmButton(state?.canSave == true, onSave) },
            )
        },
    ) { pad ->
        val s = state ?: return@Scaffold
        Box(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = if (keypadOpen) 440.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FieldBox(
                    "日期", onClick = { pickDate = true },
                    trailing = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { Text(fmtDateWithWeekday(s.date), style = MaterialTheme.typography.bodyLarge) }

                AmountCard(s, onAmountClick = { onKeypadOpen(true) }, onCurrencyClick = { pickCurrency = true }, onEdit = onEdit)

                SectionHeader("分類")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 5,
                ) {
                    categories.forEach { c ->
                        CategoryOption(c, s.categoryId == c.id, Modifier.weight(1f)) { onEdit { it.copy(categoryId = c.id) } }
                    }
                    repeat((5 - categories.size % 5) % 5) { Spacer(Modifier.weight(1f)) }
                }

                SectionHeader("付款方式")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { m ->
                        SelectPill(
                            m.name, s.paymentId == m.id, { onEdit { it.copy(paymentId = m.id) } },
                            leading = {
                                Icon(
                                    paymentIcon(m.name), null, Modifier.size(18.dp),
                                    tint = if (s.paymentId == m.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }

                FieldBox("備註(選填)") {
                    FieldInput(s.note, { v -> onEdit { it.copy(note = v) } }, "例如:一蘭拉麵 本店")
                }

                if (!isNew) {
                    TextButton({ confirmDelete = true }, Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("刪除這筆支出", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(
                keypadOpen, Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it }, exit = slideOutVertically { it },
            ) {
                KeypadPanel(
                    amount = s.amount, currency = s.currency,
                    preview = if (s.currency != HOME_CURRENCY) s.homeAmount?.let { "≈ ${fmtMoney(it)}" } else null,
                    onKey = onKey, onDone = { onKeypadOpen(false) },
                )
            }
        }

        if (pickCurrency) {
            CurrencyDialog(s.currency, { pickCurrency = false }) { onCurrency(it); pickCurrency = false }
        }
        if (pickDate) {
            val st = rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.ofEpochDay(s.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton({
                        st.selectedDateMillis?.let { ms ->
                            val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                            onEdit { it.copy(date = day) }
                        }
                        pickDate = false
                    }) { Text("確定") }
                },
                dismissButton = { TextButton({ pickDate = false }) { Text("取消") } },
            ) { DatePicker(st) }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                icon = { Icon(Icons.Rounded.DeleteOutline, null) },
                title = { Text("刪除這筆支出?") },
                confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("刪除", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun AmountCard(
    s: EditState,
    onAmountClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onEdit: ((EditState) -> EditState) -> Unit,
) {
    LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAmountClick).padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("金額", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    fmtTyped(s.amount), style = MaterialTheme.typography.displaySmall,
                    color = if (s.amount.isEmpty()) ledger.textMuted else MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape).clickable(onClick = onCurrencyClick)
                    .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(currencyLabel(s.currency), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = ledger.hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.currency == HOME_CURRENCY) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = ledger.success)
                Spacer(Modifier.width(6.dp))
                Text("新台幣,不需換算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("匯率", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                val style = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface)
                BasicTextField(
                    s.rate, { v -> onEdit { it.copy(rate = v.filter { c -> c.isDigit() || c == '.' }) } },
                    Modifier.width(96.dp).clip(RoundedCornerShape(8.dp)).background(ledger.field).padding(horizontal = 8.dp, vertical = 6.dp),
                    singleLine = true, textStyle = style, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    decorationBox = { inner ->
                        if (s.rate.isEmpty()) Text("輸入", style = style, color = ledger.textMuted)
                        inner()
                    },
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text("= 新台幣", style = CaptionStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        s.homeAmount?.let { fmtMoney(it) } ?: if (s.rateValue == null) "請輸入匯率" else "NT$ 0",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (s.rateValue == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryOption(c: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val style = categoryStyle(c.name, c.icon, c.color)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape)
            .background(if (selected) style.color.copy(alpha = if (ledger.dark) 0.18f else 0.10f) else MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) style.color else ledger.hairline, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTile(style.icon, style.color, size = 36.dp, corner = 11.dp)
        Spacer(Modifier.height(6.dp))
        Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
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
