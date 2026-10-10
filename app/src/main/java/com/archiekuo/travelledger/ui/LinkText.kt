@file:OptIn(ExperimentalLayoutApi::class)

package com.archiekuo.travelledger.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.archiekuo.travelledger.logic.FoundLink
import com.archiekuo.travelledger.logic.Links

/** Opens a web address, map link or phone number; says so instead of crashing when no app can. */
fun openLink(context: Context, uri: String) {
    val intent = Intent(if (uri.startsWith("tel:")) Intent.ACTION_DIAL else Intent.ACTION_VIEW, Uri.parse(uri))
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "找不到可以開啟的 App", Toast.LENGTH_SHORT).show() }
}

/** Text whose web addresses and phone numbers are underlined and open when tapped. */
@Composable
fun LinkText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor, context) {
        val links = Links.find(text)
        buildAnnotatedString {
            var at = 0
            for (l in links) {
                append(text.substring(at, l.start))
                // Open it ourselves rather than through LocalUriHandler, so phone numbers dial and a missing app can't crash.
                val listener = LinkInteractionListener { openLink(context, l.uri) }
                withLink(LinkAnnotation.Url(l.uri, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)), listener)) {
                    append(l.text)
                }
                at = l.end
            }
            append(text.substring(at))
        }
    }
    Text(annotated, modifier, style = style, color = color, onTextLayout = onTextLayout)
}

/**
 * An editor field whose links can be tapped: while not being edited, text with a web address or phone number
 * shows as [LinkText] (tap a link to open it, tap anywhere else to start editing); otherwise a plain text field.
 */
@Composable
fun LinkAwareInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
    /** Lines kept visible while typing, so longer text has room. */
    minLines: Int = 1,
) {
    val style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
    var editing by remember { mutableStateOf(false) }
    val hasLinks = remember(value) { Links.find(value).isNotEmpty() }
    if (hasLinks && !editing) {
        val context = LocalContext.current
        var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
        val links = remember(value) { Links.find(value) }
        Box(
            modifier.fillMaxWidth().heightIn(min = minHeight)
                // This box decides what a tap means, before the text sees it: on a link it opens the link,
                // anywhere else it starts editing.
                .pointerInput(value) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                        up.consume()
                        val hit = layout?.let { l -> linkAt(l, links, down.position) }
                        if (hit != null) openLink(context, hit.uri) else editing = true
                    }
                }
                .semantics { onClick(label = "編輯") { editing = true; true } },
        ) { LinkText(value, style = style, onTextLayout = { layout = it }) }
        return
    }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    BasicTextField(
        value, onChange,
        modifier.fillMaxWidth().heightIn(min = minHeight).focusRequester(focus).onFocusChanged {
            if (it.isFocused) hadFocus = true else if (hadFocus) { hadFocus = false; editing = false }
        },
        singleLine = singleLine, minLines = if (singleLine) 1 else minLines, textStyle = style, cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = ledger.textMuted)
                inner()
            }
        },
    )
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }
}

/** The link under [pos] in laid-out text, if the position is really on its characters (not just the same line). */
internal fun linkAt(layout: TextLayoutResult, links: List<FoundLink>, pos: Offset): FoundLink? {
    if (pos.y < 0 || pos.y > layout.size.height) return null
    val offset = layout.getOffsetForPosition(pos)
    return links.firstOrNull { l ->
        (maxOf(l.start, offset - 1)..minOf(l.end - 1, offset)).any { i ->
            layout.getBoundingBox(i).let { b -> pos.x >= b.left - 4 && pos.x <= b.right + 4 && pos.y >= b.top && pos.y <= b.bottom }
        }
    }
}

private fun label(l: FoundLink): String = if (l.isPhone) "撥打 ${l.text}" else {
    val host = Uri.parse(l.uri).host?.removePrefix("www.")
    when {
        host == null -> "開啟連結"
        host.startsWith("maps.") || host.startsWith("goo.gl") || host == "maps.app.goo.gl" -> "開啟地圖"
        else -> "開啟 $host"
    }
}

/**
 * Buttons under an editable field for each link in it: in an editor a tap on the text moves the cursor,
 * so links get their own "↗ 開啟" / "撥打" buttons. Shows nothing when there are no links.
 */
@Composable
fun LinkButtons(text: String, modifier: Modifier = Modifier) {
    val links = remember(text) { Links.find(text).distinctBy { it.uri }.take(3) }
    if (links.isEmpty()) return
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        links.forEach { l ->
            Row(
                Modifier.clip(RoundedCornerShape(50)).border(1.dp, cs.primary.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clickable { openLink(context, l.uri) }.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalContentColor provides cs.primary) {
                    Icon(if (l.isPhone) Icons.Rounded.Call else Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label(l), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
