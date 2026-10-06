@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.travelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.travelledger.data.HOME_CURRENCY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun ExpenseEditScreen(vm: ExpenseEditViewModel, isNew: Boolean, onBack: () -> Unit) {
    val s = vm.state
    val categories by vm.categories.collectAsStateWithLifecycle()
    val methods by vm.methods.collectAsStateWithLifecycle()
    var pickCurrency by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isNew) "記一筆" else "編輯支出") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = { if (!isNew) TextButton({ confirmDelete = true }) { Text("刪除") } },
        )
    }) { pad ->
        if (s == null) return@Scaffold
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Amount + currency
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickCurrency = true }) { Text(s.currency) }
                Spacer(Modifier.width(12.dp))
                Text(
                    s.amount.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
            if (s.currency != HOME_CURRENCY) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        s.rate, { v -> vm.edit { it.copy(rate = v.filter { c -> c.isDigit() || c == '.' }) } },
                        label = { Text("匯率(1 ${s.currency} = ? $HOME_CURRENCY)") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        isError = s.rate.isNotEmpty() && s.rateValue == null,
                    )
                }
            }
            Text(
                s.homeAmount?.let { "≈ ${fmtMoney(it)}" } ?: if (s.currency != HOME_CURRENCY && s.rateValue == null) "請輸入匯率" else "≈ $HOME_CURRENCY 0",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text("分類", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { c ->
                    FilterChip(s.categoryId == c.id, { vm.edit { it.copy(categoryId = c.id) } }, label = { Text(c.name) })
                }
            }
            Text("付款方式", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                methods.forEach { m ->
                    FilterChip(s.paymentId == m.id, { vm.edit { it.copy(paymentId = m.id) } }, label = { Text(m.name) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton({ pickDate = true }) { Text(fmtDate(s.date)) }
                OutlinedTextField(
                    s.note, { v -> vm.edit { it.copy(note = v) } },
                    label = { Text("備註(選填)") }, singleLine = true, modifier = Modifier.weight(1f),
                )
            }

            NumberKeypad(vm::key, Modifier.fillMaxWidth())
            Button({ vm.save(onBack) }, Modifier.fillMaxWidth().height(52.dp), enabled = s.canSave) { Text("儲存") }
            Spacer(Modifier.height(16.dp))
        }

        if (pickCurrency) {
            CurrencyDialog(s.currency, { pickCurrency = false }) { vm.setCurrency(it); pickCurrency = false }
        }
        if (pickDate) {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = LocalDate.ofEpochDay(s.date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { pickDate = false },
                confirmButton = {
                    TextButton({
                        state.selectedDateMillis?.let { ms ->
                            val day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                            vm.edit { it.copy(date = day) }
                        }
                        pickDate = false
                    }) { Text("確定") }
                },
                dismissButton = { TextButton({ pickDate = false }) { Text("取消") } },
            ) { DatePicker(state) }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("刪除這筆支出?") },
                confirmButton = { TextButton({ confirmDelete = false; vm.delete(onBack) }) { Text("刪除") } },
                dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
            )
        }
    }
}

@Composable
private fun CurrencyDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇幣別") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COMMON_CURRENCIES.forEach { c ->
                        FilterChip(c == current, { onPick(c) }, label = { Text(c) })
                    }
                }
                OutlinedTextField(
                    custom, { custom = it.uppercase().filter { c -> c.isLetter() }.take(3) },
                    label = { Text("其他代碼(3 碼)") }, singleLine = true,
                )
            }
        },
        confirmButton = { TextButton({ onPick(custom) }, enabled = custom.length == 3) { Text("使用") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
