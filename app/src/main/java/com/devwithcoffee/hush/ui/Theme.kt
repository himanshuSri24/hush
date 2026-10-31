package com.devwithcoffee.hush.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devwithcoffee.hush.engine.AppNames

object C {
    val bg = Color(0xFF0D1017)
    val surface = Color(0xFF151A24)
    val surfaceHi = Color(0xFF1C2230)
    val line = Color(0xFF262D3D)
    val moon = Color(0xFF9AA8FF)
    val moonSoft = Color(0xFF2A3160)
    val amber = Color(0xFFFFB86B)
    val amberSoft = Color(0xFF3A2C1C)
    val text = Color(0xFFE8EAF2)
    val muted = Color(0xFF8B93A7)
    val ok = Color(0xFF7FD6A4)
    val bad = Color(0xFFFF8A80)
}

private val scheme = darkColorScheme(
    primary = C.moon, onPrimary = C.bg,
    primaryContainer = C.moonSoft, onPrimaryContainer = C.text,
    secondary = C.amber, onSecondary = C.bg,
    secondaryContainer = C.moonSoft, onSecondaryContainer = C.moon,
    background = C.bg, onBackground = C.text,
    surface = C.bg, onSurface = C.text,
    surfaceVariant = C.surface, onSurfaceVariant = C.muted,
    surfaceContainer = C.surface, surfaceContainerHigh = C.surfaceHi,
    surfaceContainerHighest = C.surfaceHi, surfaceContainerLow = C.surface,
    outline = C.line, outlineVariant = C.line, error = C.bad,
)

@Composable
fun HushTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = scheme,
        typography = base.copy(
            displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
            headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(text.uppercase(), style = TextStyle(fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold), color = C.muted)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = C.muted.copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.surface)
            .border(BorderStroke(1.dp, accent?.copy(alpha = 0.45f) ?: C.line), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun AppIcon(pkg: String, size: Dp = 36.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { AppNames.icon(ctx, pkg) }
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    else Initials(AppNames.label(ctx, pkg), size, C.surfaceHi)
}

@Composable
fun Initials(name: String, size: Dp = 40.dp, bg: Color = C.moonSoft, fg: Color = C.moon) {
    val letters = name.split(" ").map { w -> w.filter { it.isLetterOrDigit() } }.filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(letters, color = fg, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.36f).sp)
    }
}

@Composable
fun Pill(text: String, color: Color = C.moon, bg: Color = C.moonSoft) {
    Text(
        text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
