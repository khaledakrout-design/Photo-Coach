package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.detection.ConfidenceTier
import com.example.detection.DetectedObject
import com.example.detection.ObjectRelevanceRole
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Dialogue d'inspection diagnostique détaillée de tous les objets détectés dans la scène (Jalon 2.2).
 */
@Composable
fun DetectionDetailsDialog(
    detectedObjects: List<DetectedObject>,
    onSelectSubject: (DetectedObject) -> Unit,
    onUnlockSubject: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 580.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .testTag("detection_details_dialog"),
            colors = CardDefaults.cardColors(containerColor = CharcoalBlack)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INSPECTION DES DÉTECTIONS",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${detectedObjects.size} objet(s) recensé(s) par EfficientDet-Lite0",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (detectedObjects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucun objet détecté dans le champ de vision actuel.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(detectedObjects, key = { it.id }) { obj ->
                            DetectionObjectItemRow(
                                obj = obj,
                                onSelect = { onSelectSubject(obj) },
                                onUnlock = onUnlockSubject
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalSurface,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Fermer l'inspecteur",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectionObjectItemRow(
    obj: DetectedObject,
    onSelect: () -> Unit,
    onUnlock: () -> Unit
) {
    val isSelected = obj.isSelectedSubject
    val isLocked = obj.isLocked

    val roleBadgeColor = when (obj.relevanceRole) {
        ObjectRelevanceRole.PRIMARY_CANDIDATE -> CyanAccent
        ObjectRelevanceRole.SECONDARY_OBJECT -> Color(0xFF64B5F6)
        ObjectRelevanceRole.CONTEXT_OBJECT -> Color(0xFFFFB74D)
        ObjectRelevanceRole.BACKGROUND_OBJECT -> Color(0xFF9E9E9E)
        ObjectRelevanceRole.UNKNOWN -> Color(0xFF757575)
    }

    val roleLabel = when (obj.relevanceRole) {
        ObjectRelevanceRole.PRIMARY_CANDIDATE -> "CANDIDAT PRINCIPAL"
        ObjectRelevanceRole.SECONDARY_OBJECT -> "OBJET SECONDAIRE"
        ObjectRelevanceRole.CONTEXT_OBJECT -> "CONTEXTE"
        ObjectRelevanceRole.BACKGROUND_OBJECT -> "ARRIÈRE-PLAN"
        ObjectRelevanceRole.UNKNOWN -> "INDÉFINI"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CyanAccent else CharcoalBorder,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CharcoalSurface else CharcoalBlack.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = obj.label,
                        color = if (isSelected) CyanAccent else PureWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[COCO: ${obj.rawModelLabel}]",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Badge de rôle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(roleBadgeColor.copy(alpha = 0.2f))
                        .border(1.dp, roleBadgeColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = roleLabel,
                        color = roleBadgeColor,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Ligne métriques
            val trackInfo = obj.trackingId?.let { "Track #$it" } ?: "Non pisté"
            val tierLabel = when (obj.confidenceTier) {
                ConfidenceTier.HIGH -> "Élevée"
                ConfidenceTier.MEDIUM -> "Moyenne"
                ConfidenceTier.LOW -> "Faible"
            }
            Text(
                text = "Confiance : ${obj.confidencePercent}% ($tierLabel) • $trackInfo • Aire : ${String.format("%.1f", obj.areaFraction * 100)}%",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )

            val box = obj.boundingBox
            Text(
                text = "Rect : [L:${String.format("%.2f", box.left)} T:${String.format("%.2f", box.top)} R:${String.format("%.2f", box.right)} B:${String.format("%.2f", box.bottom)}]",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action de verrouillage / sélection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelected && isLocked) {
                    Button(
                        onClick = onUnlock,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800),
                            contentColor = CharcoalBlack
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Déverrouiller", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSelect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) CyanAccent else CharcoalBorder,
                            contentColor = CharcoalBlack
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSelected) "Sujet Actif (Verrouiller)" else "Choisir comme Sujet",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) CharcoalBlack else TextPrimary
                        )
                    }
                }
            }
        }
    }
}
