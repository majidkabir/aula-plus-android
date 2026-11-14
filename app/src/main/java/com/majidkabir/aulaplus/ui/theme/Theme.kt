package com.majidkabir.aulaplus.ui.theme

import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.majidkabir.aulaplus.R
import androidx.compose.ui.unit.sp
import com.majidkabir.aulaplus.AulaApp
import com.majidkabir.aulaplus.core.L

/** Selectable colour themes (same palettes as the iOS app) plus Material You on Android 12+. */
enum class AppTheme(val key: String, val brandLight: Color, val brandDark: Color, val second: Color) {
    APRICOT("Abrikos", Color(0xFFDE661C), Color(0xFFFF9E59), Color(0xFFFFC266)),
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
            APRICOT -> listOf(Palette.orange, Palette.brown)
            OCEAN, DYNAMIC -> listOf(Palette.indigo)
            FOREST -> listOf(Palette.green, Palette.mint, Palette.teal)
            SUNSET -> listOf(Palette.orange, Palette.brown)
            LAVENDER -> listOf(Palette.purple, Palette.indigo, Palette.pink)
            GRAPHITE -> listOf(Palette.brown)
        }
}

object ThemeStore {
    var theme by mutableStateOf(AppTheme.APRICOT)
        private set

    fun load() {
        theme = AppTheme.entries.firstOrNull { it.name == AulaApp.prefs.getString("theme", null) } ?: AppTheme.APRICOT
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
    val jade = Color(0xFF2BA38E)
    val violet = Color(0xFF7A5AF0)
    private val all = listOf(orange, teal, pink, purple, green, indigo, mint, brown, jade, violet)

    /** Same colour for the same name every time (people without a child colour). */
    fun stable(name: String): Color {
        val sum = name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
        return all[sum % all.size]
    }

    fun colors(): List<Color> = all.filterNot { it in ThemeStore.theme.clashing }
    fun forChild(id: Int, children: List<Int>): Color {
        val list = colors()
        val i = children.indexOf(id).takeIf { it >= 0 } ?: kotlin.math.abs(id)
        return list[i % list.size]
    }
}

/** Nunito: a rounded, friendly typeface (SIL OFL, bundled). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.Medium),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
    Font(R.font.nunito_black, FontWeight.Black),
)

private val Base = Typography()
private fun TextStyle.n(weight: FontWeight? = null) = copy(fontFamily = Nunito, fontWeight = weight ?: fontWeight)
private val AppTypography = Typography(
    displayLarge = Base.displayLarge.n(FontWeight.Black),
    displayMedium = Base.displayMedium.n(FontWeight.Black),
    displaySmall = Base.displaySmall.n(FontWeight.Black),
    headlineLarge = Base.headlineLarge.n(FontWeight.Black),
    headlineMedium = Base.headlineMedium.n(FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.n(FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.n(FontWeight.ExtraBold),
    titleMedium = Base.titleMedium.n(FontWeight.Bold),
    titleSmall = Base.titleSmall.n(FontWeight.Bold),
    bodyLarge = Base.bodyLarge.n(),
    bodyMedium = Base.bodyMedium.n(),
    bodySmall = Base.bodySmall.n(),
    labelLarge = Base.labelLarge.n(FontWeight.Bold),
    labelMedium = Base.labelMedium.n(FontWeight.SemiBold),
    labelSmall = Base.labelSmall.n(FontWeight.SemiBold),
)

/** Warm cream canvas (light) and warm near-black (dark). */
val LightBackground = Color(0xFFFFF6EC)
val DarkBackground = Color(0xFF16110D)
/** Soft warm shadow colour under cards. */
val CardShadow = Color(0xFF73471F)

@Composable
fun AulaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = ThemeStore.theme
    val inPreview = LocalInspectionMode.current
    val scheme = if (t == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= 31 && !inPreview) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        darkColorScheme(
            primary = t.brandDark, onPrimary = readableOn(t.brandDark),
            secondary = t.second, background = DarkBackground, surface = Color(0xFF251E19),
            surfaceVariant = Color(0xFF352B23), surfaceContainer = Color(0xFF251E19),
            surfaceContainerHigh = Color(0xFF352B23), onSurfaceVariant = Color(0xFFB9A594),
            outline = Color(0xFF6E5D50), outlineVariant = Color(0xFF3E332A),
            primaryContainer = t.brandDark.copy(alpha = 0.22f), onPrimaryContainer = t.brandDark,
        )
    } else {
        lightColorScheme(
            primary = t.brandLight, onPrimary = readableOn(t.brandLight), secondary = t.second,
            background = LightBackground, surface = Color.White, surfaceVariant = Color(0xFFF6E8D9),
            surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF6E8D9),
            onSurfaceVariant = Color(0xFF8E705A), onBackground = Color(0xFF3A2A1E), onSurface = Color(0xFF3A2A1E),
            outline = Color(0xFFC9B3A0), outlineVariant = Color(0xFFEFE0D0),
            primaryContainer = t.brandLight.copy(alpha = 0.14f), onPrimaryContainer = t.brandLight,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Default text/icon colour for content drawn straight on the background.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides scheme.onBackground,
            content = content,
        )
    }
}

val Body = TextStyle(fontSize = 16.sp)

/** White where it reads well on [c] (≥ 3:1, bold text), else near-black: the lifted dark-mode colours are too light for white. */
fun readableOn(c: Color): Color {
    val white = 1.05f / (c.luminance() + 0.05f)
    return if (white >= 3f) Color.White else Color(0xFF141414)
}
