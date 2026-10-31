package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.detection.ConfidenceTier
import com.example.detection.DetectedObject
import com.example.detection.DetectionDiagnostics
import com.example.detection.DetectionDisplayMode
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Feuille diagnostique repliable en bas de l'écran (Jalon 2.2.1 - Exigence 4).
 * - État compact : modèle, nombre d'objets, FPS réel d'analyse, latence d'inférence.
 * - État étendu : télémétrie complète (moyenne, P95, FPS, mémoire), liste détaillée avec coordonnées normalisées,
 *   identifiants de piste et sélection immédiate du sujet.
 * - Préserve l'accès au déclencheur et aux commandes photographiques.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticBottomSheet(
    diagnostics: DetectionDiagnostics,
    detectedObjects: List<DetectedObject>,
    onSelectSubject: (DetectedObject) -> Unit,
    onUnlockSubject: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalBlack.copy(alpha = 0.92f))
            .border(1.dp, CyanAccent.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .animateContentSize()
            .testTag("collapsible_diagnostic_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Barre supérieure d'en-tête (Toujours visible et interactive)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { isExpanded = !isExpanded }
                    .testTag("diagnostic_sheet_toggle_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Indicateur et Métriques compactes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "DIAG",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "·",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${diagnostics.detectionCount} obj",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "·",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", diagnostics.fps)} FPS",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "·",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${diagnostics.lastInferenceTimeMs} ms",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bouton d'extension avec cible tactile confortable (>= 48dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CharcoalSurface.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Réduire" else "Détails",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (isExpanded) "Réduire le panneau diagnostic" else "Étendre le panneau diagnostic",
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Contenu étendu (Défilable verticalement, Jalon 2.2.1)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 8.dp)
                ) {
                    // 1. Grille des Métriques de Performance Profilées
                    Text(
                        text = "MÉTRIQUES DE PERFORMANCE (FENÊTRE 10s)",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DiagnosticBadge(
                            label = "Latence Moyenne",
                            value = "${String.format(Locale.US, "%.1f", diagnostics.avgInferenceTimeMs)} ms"
                        )
                        DiagnosticBadge(
                            label = "P95 Latence",
                            value = "${diagnostics.p95InferenceTimeMs} ms"
                        )
                        DiagnosticBadge(
                            label = "FPS Réel",
                            value = "${String.format(Locale.US, "%.1f", diagnostics.fps)} FPS"
                        )
                        DiagnosticBadge(
                            label = "Mémoire Vive",
                            value = "${diagnostics.memoryUsageMb} Mo"
                        )
                        DiagnosticBadge(
                            label = "Modèle Local",
                            value = diagnostics.modelName
                        )
                        DiagnosticBadge(
                            label = "Délégué Matériel",
                            value = diagnostics.hardwareDelegate
                        )
                    }

                    // Sujet principal actif
                    if (diagnostics.selectedSubjectLabel != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanAccent.copy(alpha = 0.12f))
                                .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Sujet actif : ${diagnostics.selectedSubjectLabel} (${((diagnostics.selectedSubjectConfidence ?: 0f) * 100).toInt()}%)",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            if (diagnostics.isLocked) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyanAccent)
                                        .clickable { onUnlockSubject() }
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = CharcoalBlack,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "VERROUILLÉ (LIBÉRER)",
                                        color = CharcoalBlack,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = "SUGGESTION AUTO",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // 2. Liste détaillée des objets détectés
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "OBJETS DÉTECTÉS (${detectedObjects.size})",
                        color = CyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (detectedObjects.isEmpty()) {
                        Text(
                            text = "Aucun objet actuellement suivi par le modèle local.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        detectedObjects.forEach { obj ->
                            val isSelected = obj.isSelectedSubject
                            val isLocked = isSelected && obj.isLocked

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CyanAccent.copy(alpha = 0.15f) else CharcoalSurface.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyanAccent else CharcoalBorder.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        if (isSelected && isLocked) {
                                            onUnlockSubject()
                                        } else {
                                            onSelectSubject(obj)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = obj.label,
                                            color = if (isSelected) CyanAccent else TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (obj.rawModelLabel.isNotBlank() && obj.rawModelLabel != obj.label) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(${obj.rawModelLabel})",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        obj.trackingId?.let { trackId ->
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "#$trackId",
                                                color = Color(0xFF64B5F6),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${obj.confidencePercent}%",
                                            color = when (obj.confidenceTier) {
                                                ConfidenceTier.HIGH -> Color(0xFF81C784)
                                                ConfidenceTier.MEDIUM -> Color(0xFFFFB74D)
                                                ConfidenceTier.LOW -> Color(0xFFE57373)
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Text(
                                        text = "Coord: [x:${String.format(Locale.US, "%.2f", obj.boundingBox.left)}..${String.format(Locale.US, "%.2f", obj.boundingBox.right)}, y:${String.format(Locale.US, "%.2f", obj.boundingBox.top)}..${String.format(Locale.US, "%.2f", obj.boundingBox.bottom)}] · Surf: ${String.format(Locale.US, "%.1f", obj.areaFraction * 100)}%",
                                        color = TextSecondary,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "Rôle: ${obj.relevanceRole.labelFr}",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 9.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isLocked) Color(0xFFFF9800) else if (isSelected) CyanAccent else CharcoalSurface)
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isLocked) "VERROUILLÉ" else if (isSelected) "SUJET ACTIF" else "CHOISIR",
                                        color = if (isSelected) CharcoalBlack else TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticBadge(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CharcoalSurface.copy(alpha = 0.7f))
            .border(1.dp, CharcoalBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            color = TextSecondary,
            fontSize = 9.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}
