@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.travelledger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class NamedItem(val id: Long, val name: String)

/** Editable, reorderable list used for both categories and payment methods. */
@Composable
fun ManageListScreen(
    title: String,
    items: List<NamedItem>,
    protectedName: String?,
    deleteHint: String,
    onBack: () -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<NamedItem?>(null) }
    var deleting by remember { mutableStateOf<NamedItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton({ adding = true }) { Icon(Icons.Default.Add, "新增") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 88.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                ListItem(
                    headlineContent = { Text(item.name) },
                    trailingContent = {
                        Row {
                            IconButton({ onMove(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Default.KeyboardArrowUp, "上移")
                            }
                            IconButton({ onMove(index, index + 1) }, enabled = index < items.lastIndex) {
                                Icon(Icons.Default.KeyboardArrowDown, "下移")
                            }
                            IconButton({ renaming = item }) { Icon(Icons.Default.Edit, "改名") }
                            IconButton({ deleting = item }, enabled = item.name != protectedName) {
                                Icon(Icons.Default.Delete, "刪除")
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (adding) {
        NameDialog("新增", "", { adding = false }) { onAdd(it); adding = false }
    }
    renaming?.let { item ->
        NameDialog("改名", item.name, { renaming = null }) { onRename(item.id, it); renaming = null }
    }
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("刪除「${item.name}」?") },
            text = { Text(deleteHint) },
            confirmButton = { TextButton({ onDelete(item.id); deleting = null }) { Text("刪除") } },
            dismissButton = { TextButton({ deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text("名稱") }, singleLine = true) },
        confirmButton = { TextButton({ onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text("確定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    )
}
