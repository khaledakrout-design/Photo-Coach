package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detection.DetectionDisplayMode
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.TextSecondary

/**
 * Contrôle segmenté horizontal pour basculer entre le mode ESSENTIEL et DIAGNOSTIC (Jalon 2.2).
 * Conçu pour éviter les retours à la ligne intempestifs et offrir une cible tactile confortable (>= 48dp).
 */
@Composable
fun DetectionModeSegmentedControl(
    selectedMode: DetectionDisplayMode,
    onModeSelected: (DetectionDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        DetectionDisplayMode.ESSENTIAL to "ESSENTIEL",
        DetectionDisplayMode.DIAGNOSTIC to "DIAGNOSTIC"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalBlack.copy(alpha = 0.88f))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
            .padding(3.dp)
            .testTag("detection_mode_segmented_control")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            modes.forEach { (mode, label) ->
                val isSelected = selectedMode == mode
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) CyanAccent else Color.Transparent,
                    animationSpec = tween(durationMillis = 180),
                    label = "segment_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) CharcoalBlack else TextSecondary,
                    animationSpec = tween(durationMillis = 180),
                    label = "segment_text"
                )

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .widthIn(min = 96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onModeSelected(mode)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag(
                            if (mode == DetectionDisplayMode.ESSENTIAL) "detection_mode_segment_essential"
                            else "detection_mode_segment_diagnostic"
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
