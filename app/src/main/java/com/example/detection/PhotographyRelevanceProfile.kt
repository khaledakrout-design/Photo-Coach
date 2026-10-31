package com.example.detection

import com.example.guidance.CreativeIntention
import com.example.guidance.PhotographyCategory

/**
 * Système de pertinence photographique intelligente (Jalon 2.2 - Part A).
 * Assigne à chaque classe d'objet un rôle adapté au contexte de prise de vue :
 * PRIMARY_CANDIDATE, SECONDARY_OBJECT, CONTEXT_OBJECT, BACKGROUND_OBJECT ou UNKNOWN.
 *
 * Utilise EXCLUSIVEMENT les classes réelles du modèle EfficientDet-Lite0 (COCO 80 classes).
 */
object PhotographyRelevanceProfile {

    // Surfaces et objets structurels considérés par défaut comme arrière-plan ou décor
    private val BACKGROUND_CLASSES = setOf(
        "dining table", "bed", "couch", "tv", "toilet"
    )

    // Mobilier et équipements fixes considérés comme contexte
    private val CONTEXT_CLASSES = setOf(
        "chair", "bench", "refrigerator", "sink", "microwave", "oven", "toaster"
    )

    /**
     * Détermine le rôle de pertinence d'un objet en fonction du contexte photographique.
     */
    fun determineRole(
        rawModelLabel: String,
        category: PhotographyCategory?,
        isAutomaticMode: Boolean
    ): ObjectRelevanceRole {
        val lower = rawModelLabel.lowercase().trim()

        if (isAutomaticMode || category == null) {
            return when {
                lower in BACKGROUND_CLASSES -> ObjectRelevanceRole.BACKGROUND_OBJECT
                lower in CONTEXT_CLASSES -> ObjectRelevanceRole.CONTEXT_OBJECT
                lower in setOf(
                    "bottle", "cup", "wine glass", "sandwich", "cake", "pizza",
                    "laptop", "cell phone", "book", "vase", "handbag", "bowl",
                    "apple", "banana", "orange", "donut", "mouse", "keyboard",
                    "clock", "scissors", "teddy bear"
                ) -> ObjectRelevanceRole.PRIMARY_CANDIDATE
                else -> ObjectRelevanceRole.SECONDARY_OBJECT
            }
        }

        return when (category) {
            PhotographyCategory.FOOD -> when {
                lower in setOf(
                    "sandwich", "cake", "pizza", "hot dog", "donut",
                    "apple", "banana", "orange", "broccoli", "carrot",
                    "bowl", "cup", "bottle", "wine glass", "fork", "spoon", "knife"
                ) -> ObjectRelevanceRole.PRIMARY_CANDIDATE
                lower in BACKGROUND_CLASSES -> ObjectRelevanceRole.BACKGROUND_OBJECT
                lower in CONTEXT_CLASSES -> ObjectRelevanceRole.CONTEXT_OBJECT
                else -> ObjectRelevanceRole.SECONDARY_OBJECT
            }

            PhotographyCategory.PRODUCT -> when {
                lower in setOf(
                    "bottle", "cup", "wine glass", "cell phone", "laptop",
                    "mouse", "keyboard", "handbag", "backpack", "suitcase",
                    "book", "clock", "vase", "remote", "hair drier", "toothbrush",
                    "scissors", "teddy bear"
                ) -> ObjectRelevanceRole.PRIMARY_CANDIDATE
                lower in BACKGROUND_CLASSES -> ObjectRelevanceRole.BACKGROUND_OBJECT
                lower in CONTEXT_CLASSES -> ObjectRelevanceRole.CONTEXT_OBJECT
                else -> ObjectRelevanceRole.SECONDARY_OBJECT
            }

            PhotographyCategory.STILL_LIFE -> when {
                lower in setOf(
                    "vase", "bottle", "cup", "wine glass", "bowl", "book",
                    "potted plant", "clock", "apple", "banana", "orange"
                ) -> ObjectRelevanceRole.PRIMARY_CANDIDATE
                lower in BACKGROUND_CLASSES -> ObjectRelevanceRole.BACKGROUND_OBJECT
                lower in CONTEXT_CLASSES -> ObjectRelevanceRole.CONTEXT_OBJECT
                else -> ObjectRelevanceRole.SECONDARY_OBJECT
            }

            PhotographyCategory.FLAT_LAY -> when {
                lower in setOf(
                    "sandwich", "cake", "cup", "bottle", "bowl", "fork", "spoon",
                    "cell phone", "book", "handbag", "scissors", "apple", "donut", "clock"
                ) -> ObjectRelevanceRole.PRIMARY_CANDIDATE
                lower in BACKGROUND_CLASSES -> ObjectRelevanceRole.BACKGROUND_OBJECT
                lower in CONTEXT_CLASSES -> ObjectRelevanceRole.CONTEXT_OBJECT
                else -> ObjectRelevanceRole.SECONDARY_OBJECT
            }
        }
    }

    /**
     * Accesseur contextuel pour validation et tests unitaires.
     */
    fun forContext(category: PhotographyCategory?, mode: com.example.guidance.GuidanceMode): PhotographyRelevanceProfileContext {
        return PhotographyRelevanceProfileContext(category, mode != com.example.guidance.GuidanceMode.GUIDED)
    }

    class PhotographyRelevanceProfileContext(
        val category: PhotographyCategory?,
        val isAutomaticMode: Boolean
    ) {
        fun getRole(rawModelLabel: String): ObjectRelevanceRole {
            return determineRole(rawModelLabel, category, isAutomaticMode)
        }
    }
}
