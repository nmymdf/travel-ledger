package com.archiekuo.travelledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.data.PhotoType

/** Full-screen photo viewer: swipe between photos, switch receipt/memory, delete, save to gallery. */
@Composable
fun PhotoViewer(
    photos: List<PhotoItem>,
    start: Int,
    onClose: () -> Unit,
    onType: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    onSave: (Int) -> Unit,
    /** False for a trip shared with us: only saving to the gallery is offered. */
    editable: Boolean = true,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var saved by remember { mutableStateOf(setOf<Int>()) }
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0E)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Rounded.Close, "關閉", tint = Color.White) }
            Text(
                "${pager.currentPage + 1} / ${photos.size}", color = Color.White, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            val img by rememberLocalImage(photos[page].path, targetPx = 1600)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: CircularProgressIndicator(color = Color.White)
            }
        }
        val i = pager.currentPage.coerceIn(0, photos.lastIndex)
        val current = photos[i]
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (editable) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeOption("收據", Icons.Rounded.Receipt, current.type == PhotoType.RECEIPT, Modifier.weight(1f)) { onType(i, PhotoType.RECEIPT) }
                TypeOption("回憶", Icons.Rounded.Image, current.type == PhotoType.MEMORY, Modifier.weight(1f)) { onType(i, PhotoType.MEMORY) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (editable) ViewerAction(Icons.Rounded.DeleteOutline, "刪除") { onDelete(i) }
                ViewerAction(if (i in saved) Icons.Rounded.CheckCircle else Icons.Rounded.Download, if (i in saved) "已存相簿" else "存到相簿") {
                    onSave(i); saved = saved + i
                }
            }
        }
    }
}

@Composable
private fun TypeOption(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(50.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
