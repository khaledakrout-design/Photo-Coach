package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.LockOpen
import com.example.detection.DetectionDisplayMode
import com.example.ui.components.DetectionDetailsDialog
import com.example.ui.components.DetectionModeSegmentedControl
import com.example.ui.components.DiagnosticBottomSheet
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.camera.CameraViewModel
import com.example.detection.PerceptionStateEnum
import com.example.guidance.GuidanceMode
import com.example.ui.components.DetectionDiagnosticsHUD
import com.example.ui.components.DetectionOverlay
import com.example.ui.components.HorizonLevelOverlay
import com.example.ui.components.PhotoViewerDialog
import com.example.ui.components.RuleOfThirdsOverlay
import com.example.ui.components.SubjectSelectionOverlay
import com.example.ui.theme.CharcoalBlack
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalDark
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanAccentSubtle
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Écran principal du viseur photographique CameraX.
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val hardwareState by viewModel.hardwareState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val subjectState by viewModel.subjectState.collectAsState()
    val orientationState by viewModel.orientationState.collectAsState()
    val perceptionState by viewModel.perceptionState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showDetectionDetailsDialog by remember { mutableStateOf(false) }

    // Gestion du retour système
    BackHandler {
        if (uiState.activeViewerUri != null) {
            viewModel.closeViewer()
        } else if (uiState.isSettingsOpen) {
            viewModel.setSettingsOpen(false)
        } else {
            onNavigateHome()
        }
    }

    // Gestion de la permission caméra
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val currentPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(currentPermission)
        if (!currentPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gestion des capteurs d'orientation
    DisposableEffect(lifecycleOwner) {
        viewModel.startOrientationTracking()
        onDispose {
            viewModel.stopOrientationTracking()
        }
    }

    // Sélecteur Photo Picker Android standard
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onGalleryImageSelected(uri)
    }

    // Disparition automatique des messages de retour
    LaunchedEffect(uiState.feedbackMessage) {
        if (uiState.feedbackMessage != null) {
            delay(3000)
            viewModel.dismissFeedback()
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsScreen(
            settings = settings,
            onToggleRuleOfThirds = { viewModel.toggleRuleOfThirds() },
            onToggleHorizonLevel = { viewModel.toggleHorizonLevel() },
            onToggleDiagnostics = { viewModel.toggleDiagnostics() },
            onToggleDetectionDisplayMode = { viewModel.toggleDetectionDisplayMode() },
            onUpdateConfidence = { h, m -> viewModel.updateConfidenceThresholds(h, m) },
            onResetConfidence = { viewModel.resetConfidenceThresholds() },
            onNavigateBack = { viewModel.setSettingsOpen(false) }
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBlack)
            .testTag("camera_screen_root")
    ) {
        if (!uiState.hasCameraPermission) {
            // État permission non accordée
            CameraPermissionDeniedView(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onNavigateHome = onNavigateHome
            )
        } else {
            // Flux du viseur de caméra réel
            CameraPreview(
                viewModel = viewModel,
                onPreviewViewReady = { previewViewRef = it },
                modifier = Modifier.fillMaxSize()
            )

            // Grille de la règle des tiers
            if (settings.showRuleOfThirds) {
                RuleOfThirdsOverlay()
            }

            // Niveau d'horizon gyroscopique
            if (settings.showHorizonLevel) {
                HorizonLevelOverlay(orientation = orientationState)
            }

            // Superposition de détection visuelle locale réelle (boîtes englobantes & sujets réels)
            DetectionOverlay(
                perceptionState = perceptionState,
                onSubjectSelected = { obj ->
                    val viewW = previewViewRef?.width?.toFloat() ?: 1f
                    val viewH = previewViewRef?.height?.toFloat() ?: 1f
                    viewModel.handlePreviewTouch(
                        x = obj.normalizedCenterX * viewW,
                        y = obj.normalizedCenterY * viewH,
                        viewWidth = viewW,
                        viewHeight = viewH,
                        previewView = previewViewRef
                    )
                },
                onUnlockSubject = { viewModel.unlockSubject() }
            )

            // Superposition de sélection manuelle tactile du sujet
            SubjectSelectionOverlay(
                selectionState = subjectState,
                onTapPoint = { x, y, width, height ->
                    viewModel.handlePreviewTouch(x, y, width, height, previewViewRef)
                },
                onClearSelection = { viewModel.clearSubjectSelection() }
            )

            // Indicateur de chargement de l'appareil
            if (hardwareState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CharcoalBlack.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Initialisation de l'appareil photo...",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Affichage d'erreur matériel le cas échéant
            if (hardwareState.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CharcoalBlack.copy(alpha = 0.85f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = hardwareState.errorMessage ?: "Erreur caméra",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                previewViewRef?.let {
                                    viewModel.bindCameraPreview(lifecycleOwner, it)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CharcoalBlack)
                        ) {
                            Text("Réessayer")
                        }
                    }
                }
            }

            // Éléments d'interface utilisateur superposés
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Partie supérieure : barre d'outils + sélecteur de mode
                Column(modifier = Modifier.fillMaxWidth()) {
                    CameraTopBar(
                        hardwareState = hardwareState,
                        settings = settings,
                        onCycleFlash = { viewModel.cycleFlash() },
                        onToggleGrid = { viewModel.toggleRuleOfThirds() },
                        onToggleHorizon = { viewModel.toggleHorizonLevel() },
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onNavigateBack = onNavigateHome
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ModeSelector(
                            activeMode = settings.activeMode,
                            onModeSelected = { viewModel.setGuidanceMode(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sélecteur de Mode d'Affichage Horizontal Segmenté (Jalon 2.2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        DetectionModeSegmentedControl(
                            selectedMode = perceptionState.displayMode,
                            onModeSelected = { mode ->
                                if (perceptionState.displayMode != mode) {
                                    viewModel.toggleDetectionDisplayMode()
                                }
                            }
                        )
                    }

                    // Indicateur d'état compact de la caméra (Jalon 1.2 & Jalon 2.1 & Jalon 2.2)
                    val statusDotColor = when (perceptionState.state) {
                        PerceptionStateEnum.INITIALIZING -> Color.Gray
                        PerceptionStateEnum.ACTIVE -> CyanAccent
                        PerceptionStateEnum.OBJECT_FOUND -> CyanAccent
                        PerceptionStateEnum.NO_OBJECT -> CyanAccent.copy(alpha = 0.65f)
                        PerceptionStateEnum.LOW_CONFIDENCE -> Color(0xFFFFB300)
                        PerceptionStateEnum.SUBJECT_LOST -> Color(0xFFFF9800)
                        PerceptionStateEnum.ERROR -> ErrorRed
                    }

                    val compactStatusText = when (perceptionState.state) {
                        PerceptionStateEnum.INITIALIZING -> PerceptionStateEnum.INITIALIZING.defaultMessageFr
                        PerceptionStateEnum.ACTIVE -> "CAMÉRA ACTIVE · ANALYSE VISUELLE LOCALE"
                        PerceptionStateEnum.OBJECT_FOUND -> perceptionState.statusText
                        PerceptionStateEnum.NO_OBJECT -> PerceptionStateEnum.NO_OBJECT.defaultMessageFr
                        PerceptionStateEnum.LOW_CONFIDENCE -> PerceptionStateEnum.LOW_CONFIDENCE.defaultMessageFr
                        PerceptionStateEnum.SUBJECT_LOST -> perceptionState.statusText
                        PerceptionStateEnum.ERROR -> PerceptionStateEnum.ERROR.defaultMessageFr
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 4.dp, start = 12.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Badge Statut de Sélection du Sujet & Perception (Jalon 2.2.1)
                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(CharcoalBlack.copy(alpha = 0.88f))
                                .border(
                                    1.dp,
                                    if (perceptionState.state == PerceptionStateEnum.SUBJECT_LOST) Color(0xFFFF9800)
                                    else if (perceptionState.isLocked) CyanAccent
                                    else CharcoalBorder,
                                    RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    if (perceptionState.state == PerceptionStateEnum.SUBJECT_LOST) {
                                        viewModel.unlockSubject()
                                    } else if (perceptionState.displayMode == DetectionDisplayMode.DIAGNOSTIC) {
                                        showDetectionDetailsDialog = true
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("camera_status_compact_indicator"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(statusDotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = compactStatusText,
                                color = if (perceptionState.state == PerceptionStateEnum.SUBJECT_LOST) Color(0xFFFF9800) else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )

                            // Bouton horizontal compact Libérer (Jalon 2.2.1 - Exigence 1)
                            if (perceptionState.isLocked) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Row(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(CyanAccent.copy(alpha = 0.20f))
                                        .border(1.dp, CyanAccent, RoundedCornerShape(20.dp))
                                        .clickable { viewModel.unlockSubject() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("unlock_subject_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Libérer le sujet",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Libérer",
                                        color = CyanAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Partie inférieure : Feuille diagnostique repliable + commandes du Mode Guidé + Zoom + Déclencheur
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Feuille diagnostique repliable en bas d'écran (Jalon 2.2.1 - Exigence 4)
                    if (settings.showDiagnostics || perceptionState.displayMode == DetectionDisplayMode.DIAGNOSTIC) {
                        DiagnosticBottomSheet(
                            diagnostics = diagnostics,
                            detectedObjects = perceptionState.detectedObjects,
                            onSelectSubject = { viewModel.selectSubject(it) },
                            onUnlockSubject = { viewModel.unlockSubject() }
                        )
                    }

                    // Zoom quick pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ZoomControlPill(
                            currentZoom = hardwareState.currentZoomRatio,
                            onZoomSelected = { viewModel.setZoom(it) }
                        )
                    }

                    // Panneau spécifique du Mode Guidé
                    if (settings.activeMode == GuidanceMode.GUIDED) {
                        GuidedModeControlsPanel(
                            settings = settings,
                            onCategorySelected = { viewModel.setPhotographyCategory(it) },
                            onIntentionSelected = { viewModel.setCreativeIntention(it) },
                            onGuidanceTypeSelected = { viewModel.setGuidanceType(it) }
                        )
                    }

                    // Déclencheur, galerie et switch caméra
                    CameraBottomControls(
                        isCapturing = uiState.isCapturing,
                        lastPhotoUri = uiState.lastCapturedUri,
                        onCaptureClick = { viewModel.capturePhoto() },
                        onGalleryClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onFlipCameraClick = {
                            previewViewRef?.let {
                                viewModel.toggleCameraLens(lifecycleOwner, it)
                            }
                        }
                    )
                }
            }

            // Bannière de feedback temporaire
            AnimatedVisibility(
                visible = uiState.feedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                uiState.feedbackMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(CharcoalBlack.copy(alpha = 0.9f))
                            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = msg,
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Dialogue d'aperçu de photo capturée ou importée
            PhotoViewerDialog(
                photoUri = uiState.activeViewerUri,
                onDismiss = { viewModel.closeViewer() }
            )

            // Dialogue d'inspection diagnostique de tous les objets détectés (Jalon 2.2)
            if (showDetectionDetailsDialog) {
                DetectionDetailsDialog(
                    detectedObjects = perceptionState.detectedObjects,
                    onSelectSubject = {
                        viewModel.selectSubject(it)
                        showDetectionDetailsDialog = false
                    },
                    onUnlockSubject = {
                        viewModel.unlockSubject()
                        showDetectionDetailsDialog = false
                    },
                    onDismiss = { showDetectionDetailsDialog = false }
                )
            }
        }
    }
}

@Composable
private fun CameraPermissionDeniedView(
    onRequestPermission: () -> Unit,
    onNavigateHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CharcoalSurface)
                .border(1.5.dp, CyanAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Accès à l'appareil photo requis",
            color = PureWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "PHOTO COACH nécessite l'autorisation d'accéder à la caméra pour afficher le viseur en temps réel, gérer le niveau d'horizon et capturer vos clichés.",
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanAccent,
                contentColor = CharcoalBlack
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("grant_camera_permission_button")
        ) {
            Text(
                text = "Autoriser l'appareil photo",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onNavigateHome,
            colors = ButtonDefaults.buttonColors(
                containerColor = CharcoalSurface,
                contentColor = TextPrimary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("permission_back_home_button")
        ) {
            Text("Retour à l'accueil")
        }
    }
}
