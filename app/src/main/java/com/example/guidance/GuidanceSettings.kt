package com.example.guidance

/**
 * État et préférences de cadrage conservés durant toute la session.
 */
data class GuidanceSettings(
    val activeMode: GuidanceMode = GuidanceMode.AUTOMATIC,
    val category: PhotographyCategory = PhotographyCategory.PRODUCT,
    val intention: CreativeIntention = CreativeIntention.BALANCED,
    val guidanceType: GuidanceType = GuidanceType.CONTINUOUS,
    val showRuleOfThirds: Boolean = true,
    val showHorizonLevel: Boolean = true
)
