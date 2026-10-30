package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidance.GuidanceMode
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentSubtle
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Sélecteur de mode principal de prise de vue (Automatique / Guidé).
 */
@Composable
fun ModeSelector(
    activeMode: GuidanceMode,
    onModeSelected: (GuidanceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(CharcoalBlack.copy(alpha = 0.8f))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(24.dp))
            .padding(4.dp)
            .testTag("mode_selector_row"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GuidanceMode.values().forEach { mode ->
            val isSelected = mode == activeMode
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) CyanAccentSubtle else Color.Transparent,
                label = "modeBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) CyanAccent else TextSecondary,
                label = "modeText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("mode_tab_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.titleFr.uppercase(),
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.1.sp
                )
            }
        }
    }
}
