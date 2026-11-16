package com.thinktwice.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.thinktwice.app.R

object TT {
    // Valores de partida aproximados; ajustar tras compararlos visualmente con la referencia.
    val Bg = Color(0xFF080909)
    val Surface = Color(0xFF141616)
    val Card = Color(0xFF222525)
    val Text = Color(0xFFF5F6F5)
    val Text2 = Color(0xFFA4ABA7)
    val Green = Color(0xFF80D7A0)
    val Pink = Color(0xFFEB536F)
    val Track = Color(0xFF1A1C1C)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Interletreado -85 (milésimas de em) en textos importantes y en el nombre de la app. */
private const val TIGHT = -0.085f

object TTType {
    val Hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = TIGHT.em, color = TT.Text)
    val Big = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = TIGHT.em, color = TT.Text)
    val Title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = TIGHT.em, color = TT.Text)
    val Section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = TIGHT.em, color = TT.Text)
    val Body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 15.sp, color = TT.Text)
    val BodyMed = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TT.Text)
    val Label = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TT.Text2)
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = TT.Text2)
}

@Composable
fun ThinkTwiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TT.Green, onPrimary = TT.Bg, background = TT.Bg, onBackground = TT.Text,
            surface = TT.Surface, onSurface = TT.Text, surfaceVariant = TT.Card, onSurfaceVariant = TT.Text2,
            secondary = TT.Pink, error = TT.Pink,
            surfaceContainerHigh = TT.Surface, surfaceContainer = TT.Surface, surfaceContainerHighest = TT.Card,
        ),
        content = content,
    )
}

@Composable
fun TText(text: String, style: TextStyle = TTType.Body, modifier: Modifier = Modifier, color: Color = Color.Unspecified, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(text, modifier = modifier, style = style, color = if (color == Color.Unspecified) style.color else color, textAlign = align, maxLines = maxLines)
}

@Composable
fun TCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(RoundedCornerShape(22.dp)).background(TT.Card).let {
        if (onClick != null) it.clickable(onClick = onClick) else it
    }
    Column(m.padding(16.dp), content = content)
}

/** Botón blanco con texto oscuro (acciones principales de paneles). Alto mínimo 52 dp. */
@Composable
fun WhiteButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TT.Bg,
            disabledContainerColor = Color(0xFF3A3D3D), disabledContentColor = TT.Text2),
    ) { Text(text, style = TTType.Section, color = if (enabled) TT.Bg else TT.Text2) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = TTType.BodyMed, color = TT.Text2)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (selected) Color.White else TT.Card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text((if (selected) "✓ " else "") + text, style = TTType.BodyMed, color = if (selected) TT.Bg else TT.Text) }
}

@Composable
fun DashedEmpty(text: String, modifier: Modifier = Modifier) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
    Box(modifier.fillMaxWidth().heightIn(min = 120.dp).padding(2.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(color = Color(0xFF3A3D3D), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(44f),
                style = Stroke(width = 3f, pathEffect = dash))
        }
        TText(text, TTType.Small, Modifier.padding(24.dp), align = TextAlign.Center)
    }
}

/** Círculos concéntricos oscuros y sutiles detrás de la zona principal. */
@Composable
fun ConcentricBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        listOf(0.52f, 0.78f, 1.05f).forEach { f ->
            drawCircle(color = Color(0xFF111313), radius = size.minDimension * f / 2 * 1.35f, center = c, style = Stroke(width = 2.5f))
        }
    }
}

/**
 * Indicador circular. Cada segmento es (valor, color); lo que falta hasta `max` queda como pista.
 * `description` se expone a TalkBack: el significado no depende solo del color.
 */
@Composable
fun Ring(
    segments: List<Pair<Float, Color>>, max: Float, size: Dp, stroke: Dp,
    description: String, modifier: Modifier = Modifier, center: @Composable () -> Unit,
) {
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(TT.Track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(sw))
            var start = -90f
            val gap = if (segments.count { it.first > 0 } > 1) 4f else 0f
            segments.forEach { (v, col) ->
                if (v > 0f && max > 0f) {
                    val sweep = (v / max * 360f).coerceAtMost(360f)
                    drawArc(col, start, (sweep - gap).coerceAtLeast(1f), false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
                    start += sweep
                }
            }
        }
        center()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    TText(text, TTType.Section, modifier.padding(top = 22.dp, bottom = 10.dp))

@Composable
fun CircleIconButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(TT.Card).clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

fun Modifier.hairline(): Modifier = this.border(1.dp, Color(0xFF2E3131), RoundedCornerShape(22.dp))

/**
 * Logo de thinktwice, EXACTAMENTE la imagen aportada (drawable-nodpi/logo_thinktwice.png), sin redibujar.
 * El archivo tiene fondo negro puro (#000) y la interfaz usa #080909: para que no se vea un rectángulo, se pinta con
 * mezcla "Screen" (el negro puro deja el fondo intacto y los píxeles del logo se mantienen). Solo cambia el tamaño.
 */
@Composable
fun LogoMark(modifier: Modifier = Modifier, description: String = "Logo de thinktwice") {
    val img = ImageBitmap.imageResource(R.drawable.logo_thinktwice)
    androidx.compose.foundation.Canvas(modifier.semantics { contentDescription = description }) {
        // Se conserva la proporción original (776 x 654): se ajusta al recuadro y se centra, sin deformar.
        val k = minOf(size.width / img.width, size.height / img.height)
        val w = (img.width * k).toInt().coerceAtLeast(1)
        val h = (img.height * k).toInt().coerceAtLeast(1)
        drawImage(
            img,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(w, h),
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            blendMode = androidx.compose.ui.graphics.BlendMode.Screen,
        )
    }
}
