package com.majidkabir.aulaplus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.L

/** Selectable colour themes (same palettes as the iOS app) plus Material You on Android 12+. */
enum class AppTheme(val key: String, val brandLight: Color, val brandDark: Color, val second: Color) {
    OCEAN("Hav", Color(0xFF2966ED), Color(0xFF5C94FF), Color(0xFF33BDF2)),
    FOREST("Skov", Color(0xFF1A855C), Color(0xFF42C285), Color(0xFF8CCC4D)),
    SUNSET("Solnedgang", Color(0xFFE65438), Color(0xFFFF805C), Color(0xFFFCA838)),
    LAVENDER("Lavendel", Color(0xFF7352E0), Color(0xFFA88FFF), Color(0xFFE673CC)),
    GRAPHITE("Grafit", Color(0xFF3D4A5E), Color(0xFFB8C4D6), Color(0xFF8094AD)),
    DYNAMIC("Material You", Color(0xFF2966ED), Color(0xFF5C94FF), Color(0xFF33BDF2));

    val label: String get() = L.t(key)

    /** Child colours that would be confused with this theme's brand colour. */
    val clashing: List<Color>
        get() = when (this) {
            OCEAN, DYNAMIC -> listOf(Palette.indigo)
            FOREST -> listOf(Palette.green, Palette.mint, Palette.teal)
            SUNSET -> listOf(Palette.orange, Palette.brown)
            LAVENDER -> listOf(Palette.purple, Palette.indigo, Palette.pink)
            GRAPHITE -> listOf(Palette.brown)
        }
}

object ThemeStore {
    var theme by mutableStateOf(AppTheme.OCEAN)
        private set

    fun load() {
        theme = AppTheme.entries.firstOrNull { it.name == AulaApp.prefs.getString("theme", null) } ?: AppTheme.OCEAN
    }

    fun set(t: AppTheme) {
        theme = t
        if (AulaApp.isReady) AulaApp.prefs.edit().putString("theme", t.name).apply()
        com.majidkabir.aulaplus.widget.WidgetWriter.update()
    }

    /** Used by screenshot tests. */
    fun preview(t: AppTheme) { theme = t }
}

/** Child colours. Brand blue is never one of them. */
object Palette {
    val orange = Color(0xFFFF9500)
    val teal = Color(0xFF30B0C7)
    val pink = Color(0xFFFF2D55)
    val purple = Color(0xFFAF52DE)
    val green = Color(0xFF34C759)
    val indigo = Color(0xFF5856D6)
    val mint = Color(0xFF00C7BE)
    val brown = Color(0xFFA2845E)
    private val all = listOf(orange, teal, pink, purple, green, indigo, mint, brown)

    fun colors(): List<Color> = all.filterNot { it in ThemeStore.theme.clashing }
    fun forChild(id: Int, children: List<Int>): Color {
        val list = colors()
        val i = children.indexOf(id).takeIf { it >= 0 } ?: kotlin.math.abs(id)
        return list[i % list.size]
    }
}

private val Base = Typography()
private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

val LightBackground = Color(0xFFF3F4F8)
val DarkBackground = Color(0xFF0C0D10)

@Composable
fun AulaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = ThemeStore.theme
    val inPreview = LocalInspectionMode.current
    val scheme = if (t == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= 31 && !inPreview) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        darkColorScheme(
            primary = t.brandDark, onPrimary = if (t == AppTheme.GRAPHITE) Color.Black else Color.White,
            secondary = t.second, background = DarkBackground, surface = Color(0xFF1C1C1F),
            surfaceVariant = Color(0xFF26262B), surfaceContainer = Color(0xFF1C1C1F),
            surfaceContainerHigh = Color(0xFF26262B), onSurfaceVariant = Color(0xFFA1A1AA),
            primaryContainer = t.brandDark.copy(alpha = 0.22f), onPrimaryContainer = t.brandDark,
        )
    } else {
        lightColorScheme(
            primary = t.brandLight, onPrimary = Color.White, secondary = t.second,
            background = LightBackground, surface = Color.White, surfaceVariant = Color(0xFFEDEEF2),
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFEDEEF2),
            onSurfaceVariant = Color(0xFF6B6F7A),
            primaryContainer = t.brandLight.copy(alpha = 0.14f), onPrimaryContainer = t.brandLight,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

val Body = TextStyle(fontSize = 16.sp)
