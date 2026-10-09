package com.archiekuo.travelledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.archiekuo.travelledger.logic.CoverArt
import com.archiekuo.travelledger.logic.CoverSpec
import com.archiekuo.travelledger.logic.CoverTheme
import com.archiekuo.travelledger.logic.Landmark
import java.time.LocalDate

/*
 * Illustrated trip covers, drawn on the device from the trip name (see CoverArt).
 * Scenes are authored in an 800×450 design space (same as the SVG samples in docs/covers) and scaled to fill,
 * cropping the top and bottom on wide cards, so keep the important parts in the middle band.
 */

private const val W = 800f
private const val H = 450f

private fun p(d: String): Path = PathParser().parsePathString(d).toPath()

private object Shapes {
    val maple = p("M0,-14 L3,-6 L9,-9 L7,-2 L14,0 L7,3 L9,9 L2,6 L1,14 L0,8 L-1,14 L-2,6 L-9,9 L-7,3 L-14,0 L-7,-2 L-9,-9 L-3,-6 Z")
    val petal = p("M0,-9 C6,-9 8,-2 0,9 C-8,-2 -6,-9 0,-9 Z")
    val plane = p("M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z")

    // Landmarks: base at (0,0), drawing upward.
    val seoulTower = p("M-2.5,-8 V-100 H2.5 V-8 Z M-8.5,-90 h17 v12 h-17 z M-5.5,-100 h11 v6 h-11 z M-1,-124 h2 v26 h-2 z M-13,0 L13,0 L7,-12 L-7,-12 Z")
    val tokyoTower = p("M-30,0 L-8,-120 L-4,-200 L0,-240 L4,-200 L8,-120 L30,0 Z")
    val tokyoBands = p("M-16,-75 L16,-75 L14,-65 L-14,-65 Z M-10,-122 L10,-122 L10,-114 L-10,-114 Z M-6,-170 L6,-170 L6,-162 L-6,-162 Z")
    val fuji = p("M-200,0 L-20,-160 Q0,-178 20,-160 L200,0 Z")
    val fujiSnow = p("M-58,-126 L-20,-160 Q0,-178 20,-160 L58,-126 L40,-116 L23,-127 L6,-112 L-11,-127 L-28,-114 Z")
    val osakaCastle = p(
        "M-46,0 L46,0 L38,-34 L-38,-34 Z" + // stone base
            " M-30,-34 H30 V-58 H-30 Z M-44,-58 Q0,-74 44,-58 L34,-66 H-34 Z" +
            " M-22,-66 H22 V-86 H-22 Z M-34,-86 Q0,-100 34,-86 L24,-94 H-24 Z" +
            " M-14,-94 H14 V-110 H-14 Z M-24,-110 Q0,-124 24,-110 L14,-118 H-14 Z M-2,-118 H2 V-130 H-2 Z",
    )
    val pagoda = p(
        "M-6,0 H6 V-150 H-6 Z" +
            " M-40,-20 Q0,-30 40,-20 L30,-30 H-30 Z M-34,-50 Q0,-60 34,-50 L25,-60 H-25 Z M-28,-80 Q0,-90 28,-80 L20,-90 H-20 Z" +
            " M-22,-110 Q0,-120 22,-110 L15,-120 H-15 Z M-16,-140 Q0,-150 16,-140 L10,-150 H-10 Z" +
            " M-24,0 H24 V-20 H-24 Z M-20,-30 H20 V-50 H-20 Z M-16,-60 H16 V-80 H-16 Z M-12,-90 H12 V-110 H-12 Z M-8,-120 H8 V-140 H-8 Z M-1.5,-150 H1.5 V-176 H-1.5 Z",
    )
    val torii = p("M-36,0 H-28 V-70 H-36 Z M28,0 H36 V-70 H28 Z M-46,-70 H46 V-62 H-46 Z M-56,-84 Q0,-78 56,-84 L52,-94 Q0,-88 -52,-94 Z M-4,-62 H4 V-80 H-4 Z")
    val taipei101 = p(
        "M-14,0 H14 V-30 H-14 Z" +
            (0 until 8).joinToString("") { i -> val b = -30 - i * 22f; " M-12,$b L-16,${b - 22} H16 L12,$b Z" } +
            " M-8,-206 H8 V-220 H-8 Z M-1.5,-220 H1.5 V-256 H-1.5 Z",
    )
    val wat = p("M-40,0 H40 V-14 H-40 Z M-30,-14 H30 V-28 H-30 Z M-22,-28 Q-22,-70 0,-96 Q22,-70 22,-28 Z M-1.5,-96 H1.5 V-140 H-1.5 Z M-60,0 H-48 V-40 L-54,-60 L-60,-40 Z M48,0 H60 V-40 L54,-60 L48,-40 Z")
    val marinaBay = p("M-60,0 H-38 V-150 H-60 Z M-11,0 H11 V-150 H-11 Z M38,0 H60 V-150 H38 Z M-76,-150 H84 Q88,-160 76,-162 H-70 Q-80,-160 -76,-150 Z")
    val eiffel = p("M-46,0 Q-30,-40 -14,-100 L-6,-170 L-2,-236 H2 L6,-170 L14,-100 Q30,-40 46,0 H30 Q0,-56 -30,0 Z M-24,-70 H24 V-62 H-24 Z M-12,-138 H12 V-132 H-12 Z")
    val bigBen = p("M-16,0 H16 V-150 H-16 Z M-20,-150 H20 V-160 H-20 Z M-14,-160 L0,-212 L14,-160 Z M-1,-212 H1 V-226 H-1 Z")
    val skyline = p("M-90,0 V-80 H-60 V-120 H-36 V-60 H-10 V-170 L0,-186 L10,-170 V-90 H34 V-140 H60 V-70 H90 V0 Z")
}

/** Draws [path] (authored around its own origin) at [x],[y] with optional scale/rotation. */
private fun DrawScope.shape(path: Path, brush: Brush, x: Float, y: Float, s: Float = 1f, rot: Float = 0f) {
    withTransform({ translate(x, y); rotate(rot, Offset.Zero); scale(s, s, Offset.Zero) }) { drawPath(path, brush) }
}
private fun DrawScope.shape(path: Path, color: Color, x: Float, y: Float, s: Float = 1f, rot: Float = 0f) =
    shape(path, androidx.compose.ui.graphics.SolidColor(color), x, y, s, rot)

private fun vgrad(vararg stops: Pair<Float, Color>, from: Float = 0f, to: Float = H) =
    Brush.verticalGradient(colorStops = stops, startY = from, endY = to)

private fun DrawScope.sky(vararg stops: Pair<Float, Color>) = drawRect(vgrad(*stops), size = Size(W, H))
private fun DrawScope.hill(d: Path, c: Color) = drawPath(d, c)
private fun DrawScope.sun(x: Float, y: Float, r: Float, core: Color, glow: Color) {
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.85f), glow.copy(alpha = 0f)), Offset(x, y), r * 3f), r * 3f, Offset(x, y))
    drawCircle(core, r, Offset(x, y))
}
private fun DrawScope.cloud(x: Float, y: Float, s: Float, a: Float = 0.9f) {
    val c = Color.White.copy(alpha = a)
    drawOval(c, Offset(x - 60 * s, y - 18 * s), Size(120 * s, 36 * s))
    drawOval(c, Offset(x - 5 * s, y - 32 * s), Size(76 * s, 40 * s))
    drawOval(c, Offset(x - 58 * s, y - 24 * s), Size(56 * s, 28 * s))
}
private fun DrawScope.tree(x: Float, y: Float, s: Float, trunk: Color, blobs: List<Color>) {
    drawRect(trunk, Offset(x - 4 * s, y - 70 * s), Size(8 * s, 70 * s))
    val offs = listOf(Offset(0f, -82f) to 40f, Offset(-28f, -65f) to 28f, Offset(28f, -64f) to 30f, Offset(0f, -108f) to 26f)
    offs.forEachIndexed { i, (o, r) -> drawCircle(blobs[i % blobs.size], r * s, Offset(x + o.x * s, y + o.y * s)) }
}

// Shared hill outlines (design space).
private val farHills = p("M0,260 C80,215 140,225 210,245 C290,195 360,200 430,235 C510,190 600,200 680,240 C730,220 770,225 800,235 V450 H0 Z")
private val midHills = p("M0,300 C70,265 150,270 230,295 C320,255 400,262 470,290 C560,250 650,262 800,285 V450 H0 Z")
private val bump = p("M330,372 C400,340 450,300 510,290 C560,282 600,288 630,300 C670,315 710,335 760,352 C780,358 790,362 800,364 V450 H330 Z")
private val nearHills = p("M0,350 C90,320 190,325 290,350 C380,372 470,360 560,370 C650,380 730,365 800,372 V450 H0 Z")
private val ground = p("M0,405 C150,390 300,395 450,402 C600,410 700,398 800,402 V450 H0 Z")

private fun DrawScope.landmarks(list: List<Landmark>, x: Float, y: Float, color: Color, accent: Color, s: Float = 1f) {
    list.forEach { lm ->
        when (lm) {
            Landmark.FUJI -> Unit // drawn as part of the backdrop
            Landmark.SEOUL_TOWER -> shape(Shapes.seoulTower, color, x, y, s * 1.1f)
            Landmark.TOKYO_TOWER -> { shape(Shapes.tokyoTower, accent, x, y, s); shape(Shapes.tokyoBands, Color.White, x, y, s) }
            Landmark.OSAKA_CASTLE -> shape(Shapes.osakaCastle, color, x, y, s * 1.2f)
            Landmark.PAGODA -> shape(Shapes.pagoda, color, x, y, s * 1.2f)
            Landmark.TORII -> shape(Shapes.torii, accent, x, y, s * 1.2f)
            Landmark.TAIPEI_101 -> shape(Shapes.taipei101, color, x, y, s)
            Landmark.BANGKOK_TEMPLE -> shape(Shapes.wat, color, x, y, s * 1.2f)
            Landmark.MARINA_BAY -> shape(Shapes.marinaBay, color, x, y, s)
            Landmark.EIFFEL -> shape(Shapes.eiffel, color, x, y, s)
            Landmark.BIG_BEN -> shape(Shapes.bigBen, color, x, y, s * 1.1f)
            Landmark.CITY_SKYLINE -> shape(Shapes.skyline, color, x, y, s * 1.2f)
        }
    }
}

private fun DrawScope.fujiBackdrop(spec: CoverSpec, body: Color, x: Float = 320f, y: Float = 330f) {
    if (Landmark.FUJI !in spec.landmarks) return
    shape(Shapes.fuji, body, x, y)
    shape(Shapes.fujiSnow, Color.White.copy(alpha = 0.92f), x, y)
}

private fun DrawScope.autumn(spec: CoverSpec) {
    sky(0f to Color(0xFFFFE3B3), 0.55f to Color(0xFFFFB070), 1f to Color(0xFFF0784A))
    sun(560f, 170f, 42f, Color(0xFFFFF3D1), Color(0xFFFFE29A))
    hill(farHills, Color(0xFFF29A6B).copy(alpha = 0.75f))
    fujiBackdrop(spec, Color(0xFFD0603F))
    hill(midHills, Color(0xFFE36E45))
    hill(bump, Color(0xFFB9472F))
    landmarks(spec.landmarks, 580f, 296f, Color(0xFF7A2A1F), Color(0xFF7A2A1F))
    hill(nearHills, Color(0xFF8E2F22))
    tree(100f, 400f, 1f, Color(0xFF4A1A14), listOf(Color(0xFFD9452B), Color(0xFFC23A24), Color(0xFFE8603A), Color(0xFFF07A3C)))
    tree(730f, 405f, 0.8f, Color(0xFF4A1A14), listOf(Color(0xFFF0A13C), Color(0xFFE8853A), Color(0xFFD9682B), Color(0xFFF0A13C)))
    hill(ground, Color(0xFF5E1E17))
    listOf(Triple(250f, 120f, 20f), Triple(330f, 205f, -30f), Triple(180f, 210f, 55f), Triple(690f, 110f, -15f), Triple(410f, 150f, 70f), Triple(470f, 300f, 10f), Triple(760f, 250f, -50f))
        .zip(listOf(0xFFE2452B, 0xFFF59E2C, 0xFFC8341F, 0xFFF07A3C, 0xFFFFC14D, 0xFFE2452B, 0xFFFFC14D))
        .forEachIndexed { i, (t, c) -> shape(Shapes.maple, Color(c), t.first, t.second, listOf(1.3f, 0.9f, 1f, 1.1f, 0.7f, 0.8f, 0.75f)[i], t.third) }
}

private fun DrawScope.sakura(spec: CoverSpec) {
    sky(0f to Color(0xFFFFF0F4), 0.6f to Color(0xFFFFD3E0), 1f to Color(0xFFF7B6CB))
    sun(620f, 140f, 36f, Color(0xFFFFFBF2), Color(0xFFFFE6EE))
    hill(farHills, Color(0xFFF3C3D3))
    fujiBackdrop(spec, Color(0xFFB9A3D6))
    hill(midHills, Color(0xFFBFD9A8))
    hill(bump, Color(0xFF9CC48A))
    landmarks(spec.landmarks, 580f, 296f, Color(0xFF6D4C7D), Color(0xFFE0533D))
    hill(nearHills, Color(0xFF7FB06F))
    tree(110f, 400f, 1.05f, Color(0xFF6B4A3A), listOf(Color(0xFFFFB7CC), Color(0xFFFF9EBB), Color(0xFFFFC9D7), Color(0xFFFFDCE6)))
    tree(720f, 405f, 0.85f, Color(0xFF6B4A3A), listOf(Color(0xFFFF9EBB), Color(0xFFFFC9D7), Color(0xFFFFB7CC), Color(0xFFFFDCE6)))
    hill(ground, Color(0xFF5F9658))
    listOf(Triple(240f, 120f, 20f), Triple(330f, 200f, -40f), Triple(190f, 230f, 60f), Triple(680f, 110f, -10f), Triple(420f, 160f, 80f), Triple(470f, 300f, 15f), Triple(770f, 250f, -55f), Triple(520f, 90f, 30f))
        .forEachIndexed { i, t -> shape(Shapes.petal, if (i % 2 == 0) Color(0xFFFF9EBB) else Color(0xFFFFFFFF), t.first, t.second, 1.1f, t.third) }
}

private fun DrawScope.snow(spec: CoverSpec) {
    sky(0f to Color(0xFFB9DDF7), 0.7f to Color(0xFFE4F3FF), 1f to Color(0xFFF4FAFF))
    val far = p("M0,280 L120,170 L200,230 L300,140 L420,250 L520,160 L640,250 L720,190 L800,240 V450 H0 Z")
    hill(far, Color(0xFF9FB8D6))
    val caps = p("M90,197 L120,170 L150,198 L135,192 L120,204 L105,192 Z M268,170 L300,140 L333,170 L316,164 L300,176 L284,164 Z M488,190 L520,160 L552,190 L536,184 L520,196 L504,184 Z")
    hill(caps, Color.White)
    fujiBackdrop(spec, Color(0xFF8AA4C8))
    hill(midHills, Color(0xFFDDEBF7))
    hill(bump, Color(0xFFEFF6FC))
    landmarks(spec.landmarks, 580f, 296f, Color(0xFF3E5675), Color(0xFFD84A3A))
    hill(nearHills, Color(0xFFFFFFFF))
    val pine = p("M0,0 L-18,0 L-6,-18 L-14,-18 L-4,-34 L-10,-34 L0,-52 L10,-34 L4,-34 L14,-18 L6,-18 L18,0 Z")
    listOf(80f to 380f, 120f to 390f, 690f to 392f, 740f to 384f).forEach { (x, y) -> shape(pine, Color(0xFF2F6B6B), x, y, 1.6f) }
    hill(ground, Color(0xFFF5FAFF))
    val flakes = listOf(60f to 60f, 150f to 120f, 240f to 40f, 350f to 100f, 450f to 50f, 520f to 130f, 610f to 70f, 700f to 140f, 770f to 50f, 280f to 200f, 660f to 220f)
    flakes.forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.9f), 4f, Offset(x, y)) }
}

private fun DrawScope.beach() {
    sky(0f to Color(0xFF3FA9F5), 1f to Color(0xFFBDEBFF))
    sun(640f, 90f, 38f, Color(0xFFFFF9DD), Color(0xFFFFF2B0))
    cloud(170f, 85f, 1f); cloud(430f, 55f, 0.7f)
    drawRect(vgrad(0f to Color(0xFF1E88C8), 0.5f to Color(0xFF22B8C8), 1f to Color(0xFF6FE0D2), from = 215f, to = H), Offset(0f, 215f), Size(W, H - 215f))
    drawPath(p("M330,222 C360,190 420,186 460,210 C470,216 480,220 490,222 Z"), Color(0xFF2E8B57))
    drawPath(p("M560,222 C580,205 615,203 640,222 Z"), Color(0xFF3AA36A))
    val wave = Stroke(2.4f, cap = StrokeCap.Round)
    listOf(60f to 260f, 520f to 280f, 250f to 300f, 640f to 330f).forEach { (x, y) ->
        drawPath(p("M$x,$y q15,-6 30,0 t30,0"), Color.White.copy(alpha = 0.55f), style = wave)
    }
    drawPath(p("M300,120 q8,-8 16,0 q8,-8 16,0 M345,140 q6,-6 12,0 q6,-6 12,0"), Color(0xFF355C7D), style = wave)
    val sand = p("M0,330 C160,318 300,345 430,370 C560,395 700,385 800,372 V450 H0 Z")
    drawPath(sand, vgrad(0f to Color(0xFFFFF2D2), 1f to Color(0xFFF2D7A2), from = 320f, to = H))
    drawPath(p("M0,330 C160,318 300,345 430,370 C560,395 700,385 800,372"), Color.White.copy(alpha = 0.8f), style = Stroke(5f))
    // palm
    drawPath(p("M120,400 C130,340 150,270 190,210"), Color(0xFF8A5A2B), style = Stroke(12f, cap = StrokeCap.Round))
    val fronds = p(
        "M190,210 C230,190 270,200 295,230 C260,215 230,220 190,210 Z M190,210 C220,230 240,260 245,295 C225,265 210,240 190,210 Z" +
            " M190,210 C160,185 120,185 90,205 C125,200 155,203 190,210 Z M190,210 C165,230 145,260 138,290 C158,260 172,235 190,210 Z" +
            " M190,210 C200,170 235,150 265,155 C232,165 210,185 190,210 Z",
    )
    drawPath(fronds, Color(0xFF1F9D55))
    drawCircle(Color(0xFF6B4423), 7f, Offset(186f, 218f)); drawCircle(Color(0xFF6B4423), 6f, Offset(196f, 222f))
    // umbrella
    drawLine(Color.White, Offset(600f, 390f), Offset(592f, 320f), 4f)
    drawPath(p("M540,328 Q592,270 644,312 Z"), Color(0xFFFF6B6B))
    drawPath(p("M540,328 Q560,300 592,292 L592,320 Z"), Color(0xFFFFD166))
}

private fun DrawScope.food(spec: CoverSpec) {
    sky(0f to Color(0xFF2A2F72), 0.5f to Color(0xFF7A5AB8), 0.82f to Color(0xFFF29BB2), 1f to Color(0xFFFFC9A8))
    listOf(90f to 60f, 210f to 35f, 420f to 50f, 610f to 30f, 740f to 75f, 330f to 95f).forEach { (x, y) -> drawCircle(Color.White.copy(alpha = 0.75f), 1.6f, Offset(x, y)) }
    drawCircle(Color(0xFFFFF4D6), 22f, Offset(650f, 120f))
    fujiBackdrop(spec, Color(0xFF7E66B6))
    landmarks(spec.landmarks, 590f, 362f, Color(0xFF3A2F6B), Color(0xFFE8432B))
    val city = p(
        "M0,315 H70 V290 H110 V330 H160 V300 H200 V340 H270 V310 H315 V335 H380 V295 H432 V325 H500 V345 H555 V450 H0 Z" +
            " M630,300 H680 V330 H738 V305 H800 V450 H630 Z",
    )
    drawPath(city, Color(0xFF2B2350))
    listOf(75f to 305f, 90f to 320f, 172f to 318f, 290f to 330f, 395f to 312f, 410f to 340f, 645f to 318f, 750f to 325f, 215f to 360f, 455f to 350f, 700f to 350f)
        .forEach { (x, y) -> drawRect(Color(0xFFFFD27A).copy(alpha = 0.85f), Offset(x, y), Size(6f, 6f)) }
    drawRect(Color(0xFF1C1738), Offset(0f, 420f), Size(W, 30f))
    // lanterns, kept low enough to survive the card crop
    drawPath(p("M0,70 Q200,110 400,82 Q600,58 800,92"), Color(0xFF1C1738).copy(alpha = 0.6f), style = Stroke(2f))
    listOf(Triple(120f, 96f, 1f), Triple(270f, 96f, 0.85f), Triple(470f, 76f, 0.85f), Triple(720f, 86f, 0.9f)).forEach { (x, y, s) ->
        val brush = Brush.horizontalGradient(listOf(Color(0xFFC8261E), Color(0xFFF0503A), Color(0xFFC8261E)), x - 16 * s, x + 16 * s)
        drawRoundRect(brush, Offset(x - 16 * s, y), Size(32 * s, 42 * s), androidx.compose.ui.geometry.CornerRadius(15 * s))
        drawRect(Color(0xFF2B1E1E), Offset(x - 12 * s, y - 2 * s), Size(24 * s, 5 * s))
        drawRect(Color(0xFF2B1E1E), Offset(x - 12 * s, y + 40 * s), Size(24 * s, 5 * s))
    }
    // ramen bowl
    drawPath(p("M116,366 H184 Q182,398 150,402 Q118,398 116,366 Z"), Color(0xFFF7F1E8))
    drawLine(Color(0xFFC8261E), Offset(116f, 366f), Offset(184f, 366f), 4f)
    drawPath(p("M140,358 q4,-8 0,-14 M152,356 q4,-8 0,-14 M164,358 q4,-8 0,-14"), Color.White.copy(alpha = 0.85f), style = Stroke(2.4f, cap = StrokeCap.Round))
    drawPath(p("M168,348 L190,328 M174,350 L196,332"), Color(0xFFE5C38A), style = Stroke(3f, cap = StrokeCap.Round))
}

private fun DrawScope.city(spec: CoverSpec) {
    sky(0f to Color(0xFF6BB7F2), 0.7f to Color(0xFFBFE4FF), 1f to Color(0xFFE6F5FF))
    sun(150f, 110f, 34f, Color(0xFFFFFBE6), Color(0xFFFFF1B8))
    cloud(560f, 90f, 0.9f); cloud(300f, 60f, 0.6f, 0.8f)
    fujiBackdrop(spec, Color(0xFF8FA9D8))
    landmarks(spec.landmarks, 590f, 372f, Color(0xFF34558F), Color(0xFFE8432B))
    val city = p("M0,330 H60 V300 H110 V340 H170 V310 H220 V350 H300 V320 H350 V345 H420 V305 H470 V335 H540 V355 H560 V450 H0 Z M640,330 H700 V310 H760 V340 H800 V450 H640 Z")
    drawPath(city, Color(0xFF5F86C4))
    drawRect(Color(0xFF4A6FAE), Offset(0f, 410f), Size(W, 40f))
    shape(Shapes.plane, Color.White, 660f, 170f, 2.2f, 45f)
}

private fun DrawScope.generic() {
    sky(0f to Color(0xFF8FC8F5), 0.6f to Color(0xFFCDE8FF), 1f to Color(0xFFFFE9D6))
    sun(610f, 130f, 36f, Color(0xFFFFFBE8), Color(0xFFFFE9B8))
    cloud(200f, 90f, 1f); cloud(480f, 70f, 0.7f, 0.85f)
    hill(farHills, Color(0xFFB7D7C8))
    hill(midHills, Color(0xFF8FC1A4))
    hill(nearHills, Color(0xFF6BA587))
    hill(ground, Color(0xFF4F8B6E))
    drawPath(p("M120,250 C220,180 330,160 450,170"), Color.White.copy(alpha = 0.85f), style = Stroke(3f, cap = StrokeCap.Round, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
    shape(Shapes.plane, Color.White, 470f, 140f, 2.6f, 70f)
}

/** Draws the illustrated cover for [spec], scaled to fill and centre-cropped. */
@Composable
fun CoverArtwork(spec: CoverSpec, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = maxOf(size.width / W, size.height / H)
        withTransform({
            translate((size.width - W * s) / 2f, (size.height - H * s) / 2f)
            scale(s, s, Offset.Zero)
        }) {
            when (spec.theme) {
                CoverTheme.AUTUMN -> autumn(spec)
                CoverTheme.SAKURA -> sakura(spec)
                CoverTheme.SNOW -> snow(spec)
                CoverTheme.BEACH -> beach()
                CoverTheme.FOOD -> food(spec)
                CoverTheme.CITY -> city(spec)
                CoverTheme.GENERIC -> generic()
            }
        }
    }
}

@Composable
fun CoverArtwork(name: String, startDate: Long?, modifier: Modifier = Modifier) {
    val spec = remember(name, startDate) { CoverArt.pick(name, startDate?.let { LocalDate.ofEpochDay(it) }) }
    CoverArtwork(spec, modifier)
}
