package com.example.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.camera.CameraHardwareState
import com.example.camera.FlashSetting
import com.example.guidance.GuidanceSettings
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Barre d'outils supérieure du viseur : flash, grille des tiers, niveau, réglages, retour.
 * Prend en compte les insets de la barre d'état et des encoches d'écran.
 */
@Composable
fun CameraTopBar(
    hardwareState: CameraHardwareState,
    settings: GuidanceSettings,
    onCycleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleHorizon: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .displayCutoutPadding()
            .background(CharcoalBlack.copy(alpha = 0.70f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton retour à l'accueil
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .size(40.dp)
                .background(CharcoalSurface.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, CharcoalBorder, CircleShape)
                .testTag("camera_back_to_home_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour à l'accueil",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bouton Flash
            IconButton(
                onClick = onCycleFlash,
                modifier = Modifier
                    .size(40.dp)
                    .background(CharcoalSurface.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, CharcoalBorder, CircleShape)
                    .testTag("flash_toggle_button")
            ) {
                val icon = when (hardwareState.flashSetting) {
                    FlashSetting.OFF -> Icons.Default.FlashOff
                    FlashSetting.AUTO -> Icons.Default.FlashAuto
                    FlashSetting.ON -> Icons.Default.FlashOn
                }
                val tint = if (hardwareState.flashSetting != FlashSetting.OFF) CyanAccent else TextSecondary
                Icon(
                    imageVector = icon,
                    contentDescription = "Contrôle du flash (${hardwareState.flashSetting.labelFr})",
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bouton Grille des tiers
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (settings.showRuleOfThirds) CyanAccentSubtle else CharcoalSurface.copy(alpha = 0.85f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (settings.showRuleOfThirds) CyanAccent else CharcoalBorder,
                        CircleShape
                    )
                    .testTag("toggle_grid_button")
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = "Grille de composition des tiers",
                    tint = if (settings.showRuleOfThirds) CyanAccent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bouton Niveau d'horizon
            IconButton(
                onClick = onToggleHorizon,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (settings.showHorizonLevel) CyanAccentSubtle else CharcoalSurface.copy(alpha = 0.85f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (settings.showHorizonLevel) CyanAccent else CharcoalBorder,
                        CircleShape
                    )
                    .testTag("toggle_horizon_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Height,
                    contentDescription = "Niveau d'horizon électronique",
                    tint = if (settings.showHorizonLevel) CyanAccent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Bouton Paramètres
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .background(CharcoalSurface.copy(alpha = 0.85f), CircleShape)
                    .border(1.dp, CharcoalBorder, CircleShape)
                    .testTag("open_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Paramètres de prise de vue",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Sélecteur rapide de zoom (1x, 2x, etc.).
 */
@Composable
fun ZoomControlPill(
    currentZoom: Float,
    onZoomSelected: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalBlack.copy(alpha = 0.75f))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("zoom_control_pill"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(1.0f, 2.0f, 3.0f).forEach { zoomTarget ->
            val isSelected = kotlin.math.abs(currentZoom - zoomTarget) < 0.25f
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) CyanAccent else Color.Transparent)
                    .clickable { onZoomSelected(zoomTarget) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${zoomTarget.toInt()}x",
                    color = if (isSelected) CharcoalBlack else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Barre de contrôle inférieure : galerie, déclencheur principal, inversion caméra.
 * Intègre les insets de barre de navigation Android pour éviter tout masquage.
 */
@Composable
fun CameraBottomControls(
    isCapturing: Boolean,
    lastPhotoUri: Uri?,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFlipCameraClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CharcoalBlack.copy(alpha = 0.90f))
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bouton d'accès à la galerie / import
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CharcoalSurface)
                .border(1.dp, CharcoalBorder, CircleShape)
                .clickable { onGalleryClick() }
                .testTag("gallery_import_button"),
            contentAlignment = Alignment.Center
        ) {
            if (lastPhotoUri != null) {
                AsyncImage(
                    model = lastPhotoUri,
                    contentDescription = "Dernière photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "Ouvrir la galerie",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Déclencheur photographique principal
        val shutterScale by animateFloatAsState(
            targetValue = if (isCapturing) 0.88f else 1.0f,
            label = "shutterScale"
        )

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(shutterScale)
                .clip(CircleShape)
                .border(3.5.dp, CyanAccent, CircleShape)
                .padding(5.dp)
                .background(CharcoalBlack, CircleShape)
                .clickable(enabled = !isCapturing) { onCaptureClick() }
                .testTag("camera_shutter_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    color = CyanAccent,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(PureWhite, CircleShape)
                )
            }
        }

        // Bouton inversion d'objectif
        IconButton(
            onClick = onFlipCameraClick,
            modifier = Modifier
                .size(52.dp)
                .background(CharcoalSurface, CircleShape)
                .border(1.dp, CharcoalBorder, CircleShape)
                .testTag("flip_camera_button")
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Changer d'objectif",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
