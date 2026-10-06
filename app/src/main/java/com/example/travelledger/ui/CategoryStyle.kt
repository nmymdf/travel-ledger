package com.example.travelledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** Category/payment visual: an icon tinted with one palette color. */
data class IconStyle(val icon: ImageVector, val color: Color)

val Palette = listOf(
    Color(0xFF3B82F6), // blue
    Color(0xFFEC4899), // pink
    Color(0xFF8B5CF6), // violet
    Color(0xFFF97316), // orange
    Color(0xFF14B8A6), // teal
    Color(0xFFEAB308), // amber
    Color(0xFF22C55E), // green
    Color(0xFFEF4444), // red
    Color(0xFF06B6D4), // cyan
    Color(0xFF64748B), // slate
)

/** Icons offered when creating a category; keys are stored in the database. */
val CategoryIcons: List<Pair<String, ImageVector>> = listOf(
    "bus" to Icons.Rounded.DirectionsBus,
    "train" to Icons.Rounded.Train,
    "flight" to Icons.Rounded.Flight,
    "taxi" to Icons.Rounded.LocalTaxi,
    "hotel" to Icons.Rounded.Hotel,
    "food" to Icons.Rounded.Restaurant,
    "ramen" to Icons.Rounded.RamenDining,
    "cafe" to Icons.Rounded.LocalCafe,
    "bar" to Icons.Rounded.LocalBar,
    "shopping" to Icons.Rounded.ShoppingBag,
    "grocery" to Icons.Rounded.LocalGroceryStore,
    "gift" to Icons.Rounded.CardGiftcard,
    "camera" to Icons.Rounded.PhotoCamera,
    "ticket" to Icons.Rounded.ConfirmationNumber,
    "spa" to Icons.Rounded.Spa,
    "beach" to Icons.Rounded.BeachAccess,
    "medical" to Icons.Rounded.MedicalServices,
    "phone" to Icons.Rounded.SimCard,
    "luggage" to Icons.Rounded.Luggage,
    "more" to Icons.Rounded.MoreHoriz,
)

private val iconByKey = CategoryIcons.toMap()

private fun guessCategory(name: String): Pair<String, Int> = when {
    listOf("交通", "車", "巴士", "地鐵").any { it in name } -> "bus" to 0
    listOf("住", "宿", "飯店", "旅館").any { it in name } -> "hotel" to 1
    listOf("購", "買", "伴手").any { it in name } -> "shopping" to 2
    listOf("機票", "飛").any { it in name } -> "flight" to 8
    listOf("飲料", "咖啡", "茶").any { it in name } -> "cafe" to 5
    listOf("吃", "食", "餐", "飯").any { it in name } -> "food" to 3
    listOf("景點", "觀光", "拍").any { it in name } -> "camera" to 4
    listOf("門票", "票").any { it in name } -> "ticket" to 6
    listOf("酒").any { it in name } -> "bar" to 7
    listOf("其他", "雜").any { it in name } -> "more" to 9
    else -> "more" to (name.hashCode().mod(Palette.size))
}

fun categoryStyle(name: String?, icon: String?, color: Int?): IconStyle {
    if (name == null) return IconStyle(Icons.Rounded.MoreHoriz, Palette.last())
    val (gIcon, gColor) = guessCategory(name)
    val key = icon?.takeIf { it.isNotEmpty() && it in iconByKey } ?: gIcon
    val idx = color?.takeIf { it in Palette.indices } ?: gColor
    return IconStyle(iconByKey.getValue(key), Palette[idx])
}

fun paymentIcon(name: String?): ImageVector = when {
    name == null -> Icons.Rounded.AccountBalanceWallet
    "現金" in name -> Icons.Rounded.Payments
    "卡" in name -> Icons.Rounded.CreditCard
    listOf("行動", "Pay", "pay", "支付").any { it in name } -> Icons.Rounded.PhoneAndroid
    else -> Icons.Rounded.AccountBalanceWallet
}

/** Gradient pairs for trips without a cover photo, picked by name. */
private val CoverGradients = listOf(
    Color(0xFF5B57F2) to Color(0xFFC06CDB),
    Color(0xFF1D4ED8) to Color(0xFF22D3EE),
    Color(0xFFEC4899) to Color(0xFFFB923C),
    Color(0xFF047857) to Color(0xFF84CC16),
    Color(0xFF1E293B) to Color(0xFF6366F1),
    Color(0xFFB45309) to Color(0xFFFBBF24),
)

fun coverGradient(seed: String): Pair<Color, Color> = CoverGradients[seed.hashCode().mod(CoverGradients.size)]

fun avatarColor(name: String): Color = Palette[name.hashCode().mod(Palette.size - 1)]
