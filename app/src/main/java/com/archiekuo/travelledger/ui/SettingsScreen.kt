package com.archiekuo.travelledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onCategories: () -> Unit,
    onMethods: () -> Unit,
    version: String,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar("設定", onBack = onBack) },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeader("外觀")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { m ->
                    OptionCard(
                        m.label, m == settings.theme, Modifier.weight(1f),
                        icon = when (m) {
                            ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
                            ThemeMode.LIGHT -> Icons.Rounded.LightMode
                            ThemeMode.DARK -> Icons.Rounded.DarkMode
                        },
                    ) { onSettings(settings.copy(theme = m)) }
                }
            }

            SectionHeader("字體大小")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FontSize.entries.forEachIndexed { i, f ->
                    OptionCard(f.label, f == settings.fontSize, Modifier.weight(1f), sample = (16 + i * 4)) {
                        onSettings(settings.copy(fontSize = f))
                    }
                }
            }

            SectionHeader("照片")
            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.PhotoLibrary, Palette[1], size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("回憶照片自動存到相簿", style = MaterialTheme.typography.bodyLarge)
                        Text("收據不會存到相簿", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(settings.saveMemoriesToGallery, { onSettings(settings.copy(saveMemoriesToGallery = it)) })
                }
            }

            SectionHeader("記帳")
            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
                SettingRow(Icons.Rounded.Category, Palette[3], "分類管理", "新增、排序、自訂圖示與顏色", onCategories)
                HorizontalDivider(Modifier.padding(start = 68.dp), color = ledger.hairline)
                SettingRow(Icons.Rounded.CreditCard, Palette[0], "付款方式管理", "現金、信用卡、行動支付…", onMethods)
            }

            SectionHeader("關於")
            LedgerCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 2.dp)) {
                SettingRow(Icons.Rounded.CurrencyExchange, Palette[6], "結算幣別", "新台幣(TWD)", null)
                HorizontalDivider(Modifier.padding(start = 68.dp), color = ledger.hairline)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("旅帳", style = MaterialTheme.typography.bodyLarge)
                        Text("版本 $version · 資料只存在這支手機", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = ledger.hairline)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom,
                ) {
                    Text("作者 ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AuthorSignature(22)
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    icon: ImageVector? = null,
    sample: Int? = null,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.height(80.dp).clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerLowest)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) cs.primary else ledger.hairline, shape)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = if (selected) cs.primary else cs.onSurfaceVariant)
        if (sample != null) {
            Text("Aa", fontSize = sample.sp, fontWeight = FontWeight.SemiBold, color = if (selected) cs.primary else cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) cs.onPrimaryContainer else cs.onSurface)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, color: Color, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ledger.textMuted)
    }
}

/** The author's name in gold italic. */
@Composable
fun AuthorSignature(sizeSp: Int) {
    Text(
        "ArchieKUO",
        color = if (ledger.dark) Color(0xFFE6C463) else Color(0xFFB8901F),
        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold,
        fontSize = sizeSp.sp, letterSpacing = 0.5.sp, maxLines = 1,
    )
}
