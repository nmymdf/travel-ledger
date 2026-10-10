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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.LinkAnnotation
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
) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        val links = Links.find(text)
        buildAnnotatedString {
            var at = 0
            for (l in links) {
                append(text.substring(at, l.start))
                withLink(LinkAnnotation.Url(l.uri, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
                    append(l.text)
                }
                at = l.end
            }
            append(text.substring(at))
        }
    }
    val handler = remember(context) { object : UriHandler { override fun openUri(uri: String) = openLink(context, uri) } }
    CompositionLocalProvider(LocalUriHandler provides handler) {
        Text(annotated, modifier, style = style, color = color)
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
