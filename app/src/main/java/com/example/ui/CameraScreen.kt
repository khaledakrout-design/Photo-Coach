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
import com.example.guidance.GuidanceMode
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

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

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

                    // Bandeau d'état du Mode Automatique
                    if (settings.activeMode == GuidanceMode.AUTOMATIC) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CharcoalBlack.copy(alpha = 0.75f))
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(CyanAccent, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Observation en temps réel • Prêt pour analyse locale (Jalon 2)",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Partie inférieure : commandes du Mode Guidé + Zoom + Déclencheur
                Column(modifier = Modifier.fillMaxWidth()) {
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
