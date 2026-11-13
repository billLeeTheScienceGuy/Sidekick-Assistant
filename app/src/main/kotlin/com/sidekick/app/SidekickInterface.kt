package com.sidekick.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

@Composable
internal fun SidekickTheme(appearance: AppearanceMode = AppearanceMode.SYSTEM, design: InterfaceDesign = InterfaceDesign.CALM, baseTypography: Typography = Typography(), content: @Composable () -> Unit) {
    val dark = appearance == AppearanceMode.DARK || appearance == AppearanceMode.SYSTEM && isSystemInDarkTheme()
    val baseColors = if (dark) darkColorScheme(
        primary = Color(0xFFA1D7C9), onPrimary = Color(0xFF00382E), primaryContainer = Color(0xFF174E43), onPrimaryContainer = Color(0xFFBEF2E2),
        background = Color(0xFF10191F), surface = Color(0xFF172229), onSurface = Color(0xFFE1E9EF),
        secondary = Color(0xFFB0CBD0), secondaryContainer = Color(0xFF304C53), onSecondaryContainer = Color(0xFFD3E8EB),
        surfaceContainerLow = Color(0xFF172229), surfaceContainer = Color(0xFF1D2B33), surfaceContainerHigh = Color(0xFF25343C), surfaceContainerHighest = Color(0xFF30414A),
        surfaceVariant = Color(0xFF26373F), onSurfaceVariant = Color(0xFFB8C8CF), outline = Color(0xFF8B9BA3),
    ) else lightColorScheme(
        primary = Color(0xFF23695A), onPrimary = Color.White, primaryContainer = Color(0xFFD9EEE5), onPrimaryContainer = Color(0xFF123E33),
        background = Color(0xFFF3F6F8), surface = Color.White, onSurface = Color(0xFF172832),
        secondary = Color(0xFF476569), secondaryContainer = Color(0xFFDAE8EB), onSecondaryContainer = Color(0xFF143A41),
        surfaceContainerLow = Color(0xFFF4F7F8), surfaceContainer = Color(0xFFE8EEF1), surfaceContainerHigh = Color(0xFFE2E9ED), surfaceContainerHighest = Color(0xFFDDE5E9),
        surfaceVariant = Color(0xFFE8EFF1), onSurfaceVariant = Color(0xFF475C66), outline = Color(0xFF6B7E86),
    )
    val colors = when (design) {
        InterfaceDesign.CALM -> baseColors
        InterfaceDesign.CLASSIC -> if (dark) baseColors.copy(primary = Color(0xFFA9CAFF), onPrimary = Color(0xFF07305E), primaryContainer = Color(0xFF214973), onPrimaryContainer = Color(0xFFD5E6FF), secondaryContainer = Color(0xFF30475D), onSecondaryContainer = Color(0xFFD6E6F8))
            else baseColors.copy(primary = Color(0xFF245C94), primaryContainer = Color(0xFFDCEBFF), onPrimaryContainer = Color(0xFF123B64), secondaryContainer = Color(0xFFDFEAF5), onSecondaryContainer = Color(0xFF203D57))
        InterfaceDesign.WARM -> if (dark) baseColors.copy(primary = Color(0xFFF0C28E), onPrimary = Color(0xFF432A0D), primaryContainer = Color(0xFF654322), onPrimaryContainer = Color(0xFFFFDEB8), background = Color(0xFF211B16), surface = Color(0xFF2A231C), secondaryContainer = Color(0xFF514335), onSecondaryContainer = Color(0xFFF6DFC5))
            else baseColors.copy(primary = Color(0xFF82531E), primaryContainer = Color(0xFFF6E5CC), onPrimaryContainer = Color(0xFF50320E), background = Color(0xFFFAF6EF), surface = Color(0xFFFFFCF6), secondaryContainer = Color(0xFFF0E4D2), onSecondaryContainer = Color(0xFF493A27))
    }
    val corners = when (design) { InterfaceDesign.CALM -> 24; InterfaceDesign.CLASSIC -> 10; InterfaceDesign.WARM -> 28 }
    val typography = baseTypography.let { type -> if (design != InterfaceDesign.WARM) type else type.copy(
        headlineMedium = type.headlineMedium.copy(fontFamily = FontFamily.Serif), titleLarge = type.titleLarge.copy(fontFamily = FontFamily.Serif)) }
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            val controls = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            controls.isAppearanceLightStatusBars = !dark
            controls.isAppearanceLightNavigationBars = !dark
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.background.toArgb()
        }
    }
    MaterialTheme(colorScheme = colors, typography = typography, shapes = Shapes(
        small = RoundedCornerShape((corners / 2).dp), medium = RoundedCornerShape((corners * 2 / 3).dp), large = RoundedCornerShape(corners.dp),
    ), content = content)
}

/** Navigation uses the current app window, including split-screen, rather than a device model. */
@Composable
internal fun SidekickFrame(selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit, content: @Composable () -> Unit) {
    val labels = listOf("Capture", "Planner", "Summary")
    val tags = listOf("capture-tab", "planner-tab", "summary-tab")
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        val rail = maxWidth >= 600.dp
        val keyboard = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Scaffold(containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("SideKick", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = onSettings, modifier = Modifier.testTag("settings").heightIn(min = 48.dp)) { Text("Settings") }
                    }
                }
            }, bottomBar = {
                if (!rail && !keyboard) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.testTag("bottom-navigation")) {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = selected == index, onClick = { onSelect(index) },
                            icon = { DestinationSymbol(index) }, label = { Text(label) }, modifier = Modifier.testTag(tags[index]))
                    }
                }
            }) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (rail) NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag("navigation-rail")) {
                    Spacer(Modifier.height(12.dp))
                    labels.forEachIndexed { index, label ->
                        NavigationRailItem(selected = selected == index, onClick = { onSelect(index) },
                            icon = { DestinationSymbol(index) }, label = { Text(label) }, modifier = Modifier.testTag(tags[index]))
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) { content() }
                }
            }
        }
    }
}

/** Small original line symbols; labels carry the navigation accessibility semantics. */
@Composable private fun DestinationSymbol(index: Int) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, point(x1, y1), point(x2, y2), 1.8f * u, StrokeCap.Round)
        if (index == 0) {
            drawRoundRect(color, point(9f, 2f), Size(6f * u, 12f * u), CornerRadius(3f * u), style = Stroke(1.8f * u))
            drawArc(color, 0f, 180f, false, point(5f, 9f), Size(14f * u, 10f * u), style = Stroke(1.8f * u))
            line(12f, 19f, 12f, 22f); line(8f, 22f, 16f, 22f)
        } else {
            drawRoundRect(color, point(3f, 4f), Size(18f * u, 17f * u), CornerRadius(3f * u), style = Stroke(1.8f * u))
            if (index == 1) { line(3f, 9f, 21f, 9f); line(8f, 2f, 8f, 6f); line(16f, 2f, 16f, 6f) }
            for (row in 0..2) line(7f, 12f + row * 3f, if (row == 2) 14f else 17f, 12f + row * 3f)
        }
    }
}

@Composable
internal fun QuietLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
