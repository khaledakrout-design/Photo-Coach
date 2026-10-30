package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.camera.CameraViewModel
import com.example.guidance.GuidanceMode
import com.example.ui.CameraScreen
import com.example.ui.HomeScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class AppScreen {
    HOME,
    CAMERA,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val cameraViewModel: CameraViewModel = viewModel()
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    currentScreen = AppScreen.HOME
                }

                when (currentScreen) {
                    AppScreen.HOME -> {
                        HomeScreen(
                            onStartCamera = { mode ->
                                cameraViewModel.setGuidanceMode(mode)
                                currentScreen = AppScreen.CAMERA
                            },
                            onOpenSettings = {
                                currentScreen = AppScreen.SETTINGS
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AppScreen.CAMERA -> {
                        CameraScreen(
                            viewModel = cameraViewModel,
                            onNavigateHome = {
                                currentScreen = AppScreen.HOME
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    AppScreen.SETTINGS -> {
                        val currentSettings by cameraViewModel.settings.collectAsState()
                        SettingsScreen(
                            settings = currentSettings,
                            onToggleRuleOfThirds = { cameraViewModel.toggleRuleOfThirds() },
                            onToggleHorizonLevel = { cameraViewModel.toggleHorizonLevel() },
                            onNavigateBack = { currentScreen = AppScreen.HOME },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
