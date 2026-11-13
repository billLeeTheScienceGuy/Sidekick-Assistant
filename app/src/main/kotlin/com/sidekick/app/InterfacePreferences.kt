package com.sidekick.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType

enum class InterfaceDesign(val label: String) { CALM("Calm"), CLASSIC("Classic"), WARM("Warm") }
enum class NoteTextSize(val label: String, val factor: Float) {
    SMALL("Smaller", .9f), PHONE("Phone default", 1f), LARGE("Larger", 1.15f), EXTRA_LARGE("Largest", 1.3f)
}
enum class InterfaceSize(val label: String, val factor: Float) {
    PHONE("Phone default", 1f), LARGE("Larger", 1.1f), EXTRA_LARGE("Largest", 1.25f)
}

internal val LocalNoteTextFactor = staticCompositionLocalOf { 1f }

@Composable
internal fun SidekickAppTheme(settings: GuidanceSettings, content: @Composable () -> Unit) {
    val appearance by settings.appearance.collectAsState()
    val design by settings.interfaceDesign.collectAsState()
    val text by settings.noteTextSize.collectAsState()
    val size by settings.interfaceSize.collectAsState()
    val base = LocalDensity.current
    // Compose text may snapshot Density and discard custom conversion overrides.
    // Keep the system fontScale and compensate each typography role using the
    // original forward/inverse font conversion instead of dividing fontScale.
    val density = remember(base, size) { Density(base.density * size.factor, base.fontScale) }
    val typography = remember(base, text, size) {
        fun adjust(value: TextUnit): TextUnit = if (value.type != TextUnitType.Sp) value else
            with(base) { ((value * text.factor).toDp() / size.factor).toSp() }
        fun adjust(style: TextStyle) = style.copy(fontSize = adjust(style.fontSize), lineHeight = adjust(style.lineHeight), letterSpacing = adjust(style.letterSpacing))
        val t = Typography()
        t.copy(displayLarge = adjust(t.displayLarge), displayMedium = adjust(t.displayMedium), displaySmall = adjust(t.displaySmall),
            headlineLarge = adjust(t.headlineLarge), headlineMedium = adjust(t.headlineMedium), headlineSmall = adjust(t.headlineSmall),
            titleLarge = adjust(t.titleLarge), titleMedium = adjust(t.titleMedium), titleSmall = adjust(t.titleSmall),
            bodyLarge = adjust(t.bodyLarge), bodyMedium = adjust(t.bodyMedium), bodySmall = adjust(t.bodySmall),
            labelLarge = adjust(t.labelLarge), labelMedium = adjust(t.labelMedium), labelSmall = adjust(t.labelSmall))
    }
    CompositionLocalProvider(LocalDensity provides density, LocalNoteTextFactor provides text.factor) {
        SidekickTheme(appearance, design, typography, content)
    }
}
