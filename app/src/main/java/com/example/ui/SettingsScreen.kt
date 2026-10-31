package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.guidance.GuidanceSettings
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentSubtle
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

/**
 * Écran des paramètres épuré et centré sur l'expérience photographique.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: GuidanceSettings,
    onToggleRuleOfThirds: () -> Unit,
    onToggleHorizonLevel: () -> Unit,
    onToggleDiagnostics: () -> Unit = {},
    onToggleDetectionDisplayMode: () -> Unit = {},
    onUpdateConfidence: (Float, Float) -> Unit = { _, _ -> },
    onResetConfidence: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAboutSection by remember { mutableStateOf(false) }
    var currentHighThreshold by remember(settings.confidenceSettings.highThreshold) {
        mutableStateOf(settings.confidenceSettings.highThreshold)
    }
    var currentMedThreshold by remember(settings.confidenceSettings.mediumThreshold) {
        mutableStateOf(settings.confidenceSettings.mediumThreshold)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PARAMÈTRES",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CharcoalBlack
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = CharcoalBlack,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Section 1 : Aides au cadrage
            Text(
                text = "AIDES AU CADRAGE DANS LE VISEUR",
                color = CyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Switch Grille des tiers
            SettingToggleItem(
                icon = Icons.Default.GridOn,
                title = "Grille de la règle des tiers",
                subtitle = "Repères à 33% et 66% avec croix sur les 4 points de force",
                checked = settings.showRuleOfThirds,
                onCheckedChange = { onToggleRuleOfThirds() },
                testTag = "setting_switch_rule_of_thirds"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Switch Niveau d'horizon
            SettingToggleItem(
                icon = Icons.Default.Height,
                title = "Niveau d'horizon électronique",
                subtitle = "Horizon gyroscopique dynamique (cyan à ±1.0°) et cible Flat Lay zénithale",
                checked = settings.showHorizonLevel,
                onCheckedChange = { onToggleHorizonLevel() },
                testTag = "setting_switch_horizon"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2 : Perception de scène locale & Précision (Jalon 2.1)
            Text(
                text = "PERCEPTION DE SCÈNE LOCALE (JALON 2.1)",
                color = CyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Bascule Mode Essentiel vs Diagnostic
            val isDiagnosticMode = settings.displayMode == com.example.detection.DetectionDisplayMode.DIAGNOSTIC
            SettingToggleItem(
                icon = Icons.Default.Speed,
                title = "Mode Diagnostic de perception",
                subtitle = if (isDiagnosticMode) "Actif : Toutes détections, latence ms, FPS, mémoire et pourcentages" else "Inactif : Mode Essentiel épuré avec sujet principal en cyan et étiquettes compactes",
                checked = isDiagnosticMode,
                onCheckedChange = { onToggleDetectionDisplayMode() },
                testTag = "setting_switch_display_mode"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Switch Télémétrie diagnostique HUD
            SettingToggleItem(
                icon = Icons.Default.Speed,
                title = "Bannière télémétrique HUD",
                subtitle = "Afficher la latence (ms), le taux de rafraîchissement (FPS) et la mémoire",
                checked = settings.showDiagnostics,
                onCheckedChange = { onToggleDiagnostics() },
                testTag = "setting_switch_diagnostics"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Contrôle interactif des seuils de confiance (Développeur)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalSurface)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RÉGLAGE DES SEUILS (DIAGNOSTIC)",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Ajustez les seuils de filtrage visuel du viseur sans modifier les probabilités brutes calculées par le modèle :",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Seuil Haute Confiance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Haute confiance (Viseur net) : ${(currentHighThreshold * 100).toInt()}%",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CharcoalDark)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    val newVal = (currentHighThreshold - 0.05f).coerceAtLeast(currentMedThreshold + 0.05f)
                                    currentHighThreshold = newVal
                                    onUpdateConfidence(newVal, currentMedThreshold)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("-5%", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CharcoalDark)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    val newVal = (currentHighThreshold + 0.05f).coerceAtMost(0.95f)
                                    currentHighThreshold = newVal
                                    onUpdateConfidence(newVal, currentMedThreshold)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+5%", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Seuil Moyenne Confiance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Moyenne confiance (Contours adoucis) : ${(currentMedThreshold * 100).toInt()}%",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CharcoalDark)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    val newVal = (currentMedThreshold - 0.05f).coerceAtLeast(0.20f)
                                    currentMedThreshold = newVal
                                    onUpdateConfidence(currentHighThreshold, newVal)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("-5%", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CharcoalDark)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable {
                                    val newVal = (currentMedThreshold + 0.05f).coerceAtMost(currentHighThreshold - 0.05f)
                                    currentMedThreshold = newVal
                                    onUpdateConfidence(currentHighThreshold, newVal)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+5%", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bouton réinitialisation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CharcoalDark)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            currentHighThreshold = 0.70f
                            currentMedThreshold = 0.45f
                            onResetConfidence()
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Rétablir seuils par défaut (70% / 45%)",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Explication des seuils de confiance (Jalon 2.1 Section 1)
            InfoItemCard(
                icon = Icons.Default.CenterFocusStrong,
                title = "Gestion intelligente de la confiance",
                description = "• Haute confiance (≥ ${(currentHighThreshold * 100).toInt()}%) : Boîtes nettes et étiquettes priorisées.\n• Moyenne confiance (${(currentMedThreshold * 100).toInt()}% à ${(currentHighThreshold * 100).toInt() - 1}%) : Contours adoucis sans surcharge.\n• Faible confiance (< ${(currentMedThreshold * 100).toInt()}%) : Filtré automatiquement en mode Essentiel."
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoItemCard(
                icon = Icons.Default.CenterFocusStrong,
                title = "Verrouillage de sujet (🔒) & Hystérésis",
                description = "Touchez n'importe quel objet pour le verrouiller. Un mécanisme d'hystérésis (Δ=0.12) empêche le papillonnement automatique entre deux sujets d'importance équivalente."
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2 : Prise de vue & Stockage
            Text(
                text = "PRISE DE VUE & STOCKAGE",
                color = CyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            InfoItemCard(
                icon = Icons.Default.CameraAlt,
                title = "Enregistrement haute résolution",
                description = "Les clichés sont sauvegardés directement dans la galerie Android sous le dossier Pictures/PhotoCoach."
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoItemCard(
                icon = Icons.Default.CenterFocusStrong,
                title = "Mise au point & Mesure tactile",
                description = "Touchez n'importe quel point du viseur pour verrouiller l'autofocus matériel et définir un point focal de composition."
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoItemCard(
                icon = Icons.Default.PhotoLibrary,
                title = "Import galerie",
                description = "Le sélecteur de photos système permet d'importer et d'analyser vos photographies existantes."
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3 : À propos (Section pliable et propre)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalSurface)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                    .clickable { showAboutSection = !showAboutSection }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "À propos de PHOTO COACH",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Version 1.1 • Moteur CameraX Natif",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = if (showAboutSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Déplier",
                        tint = TextSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showAboutSection) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalDark)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "ARCHITECTURE & TRANSPARENCE TECHNIQUE",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Moteur CameraX natif sur Android (flux vidéo temps réel 60 FPS).\n" +
                               "• Télémétrie inertielle SensorManager avec filtrage passe-bas.\n" +
                               "• Jalon 02 actif : Moteur de vision MediaPipe Tasks Vision avec modèle EfficientDet-Lite0 quantifié int8 embarqué en local (100% sur l'appareil, 0 API cloud).\n" +
                               "• Suivi temporel des objets, lissage des coordonnées et sélection heuristique du sujet principal.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalSurface)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) CyanAccent else TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CharcoalBlack,
                checkedTrackColor = CyanAccent,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = CharcoalDark
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun InfoItemCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalSurface)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}
