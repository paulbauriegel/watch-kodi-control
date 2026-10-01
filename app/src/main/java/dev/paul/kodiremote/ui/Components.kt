package dev.paul.kodiremote.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme

/** Round icon button with a haptic tick, sized for the watch face. */
@Composable
fun RemoteButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    primary: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val click = {
        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
        onClick()
    }
    val iconModifier = Modifier.size(size * 0.55f)
    if (primary) {
        FilledIconButton(onClick = click, modifier = modifier.size(size), enabled = enabled) {
            Icon(icon, contentDescription = description, modifier = iconModifier)
        }
    } else if (accent) {
        val colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        )
        FilledIconButton(onClick = click, modifier = modifier.size(size), enabled = enabled, colors = colors) {
            Icon(icon, contentDescription = description, modifier = iconModifier)
        }
    } else {
        FilledTonalIconButton(onClick = click, modifier = modifier.size(size), enabled = enabled) {
            Icon(icon, contentDescription = description, modifier = iconModifier)
        }
    }
}

/** Crown scroll distance (px) that makes one volume step. */
private const val PIXELS_PER_STEP = 50f

/**
 * Turns rotating-crown input into whole volume steps (clockwise = louder). Takes focus
 * whenever its pager page is the active one, so the crown works without tapping first.
 */
@Composable
fun Modifier.crownSteps(onSteps: (Int) -> Unit): Modifier {
    var accumulated by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    return this
        .onRotaryScrollEvent { event ->
            accumulated += event.verticalScrollPixels
            val steps = (accumulated / PIXELS_PER_STEP).toInt()
            if (steps != 0) {
                accumulated -= steps * PIXELS_PER_STEP
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSteps(steps)
            }
            true
        }
        .requestFocusOnHierarchyActive()
        .focusable()
}
