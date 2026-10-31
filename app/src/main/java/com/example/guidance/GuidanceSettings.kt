package com.example.guidance

import com.example.detection.ConfidenceFilterSettings
import com.example.detection.DetectionDisplayMode

/**
 * État et préférences de cadrage conservés durant toute la session.
 */
data class GuidanceSettings(
    val activeMode: GuidanceMode = GuidanceMode.AUTOMATIC,
    val category: PhotographyCategory = PhotographyCategory.PRODUCT,
    val intention: CreativeIntention = CreativeIntention.BALANCED,
    val guidanceType: GuidanceType = GuidanceType.CONTINUOUS,
    val showRuleOfThirds: Boolean = true,
    val showHorizonLevel: Boolean = true,
    val showDiagnostics: Boolean = false,
    val displayMode: DetectionDisplayMode = DetectionDisplayMode.ESSENTIAL,
    val confidenceSettings: ConfidenceFilterSettings = ConfidenceFilterSettings()
)
