package com.example.guidance

/**
 * Mode de prise de vue principal dans PHOTO COACH.
 */
enum class GuidanceMode(val titleFr: String, val shortDescFr: String) {
    AUTOMATIC(
        titleFr = "Automatique",
        shortDescFr = "Observation continue de la scène"
    ),
    GUIDED(
        titleFr = "Guidé",
        shortDescFr = "Cadrage selon catégorie & intention"
    )
}

/**
 * Catégories de photographie proposées en mode Guidé.
 */
enum class PhotographyCategory(val labelFr: String, val descriptionFr: String) {
    PRODUCT(
        labelFr = "Produit",
        descriptionFr = "Packshot, objets commerciaux, détails matières"
    ),
    FOOD(
        labelFr = "Culinaire",
        descriptionFr = "Plats gourmands, boissons, textures"
    ),
    FLAT_LAY(
        labelFr = "Flat Lay",
        descriptionFr = "Vue plongeante zénithale à 90°, disposition géométrique"
    ),
    STILL_LIFE(
        labelFr = "Nature Morte",
        descriptionFr = "Compositions artistiques d'objets inanimés"
    )
}

/**
 * Intentions créatives proposées en mode Guidé.
 */
enum class CreativeIntention(val labelFr: String, val descriptionFr: String) {
    MINIMALIST(
        labelFr = "Minimaliste",
        descriptionFr = "Espace négatif généreux, sujet épuré"
    ),
    BALANCED(
        labelFr = "Équilibré",
        descriptionFr = "Harmonie classique, règle des tiers naturelle"
    ),
    DYNAMIC(
        labelFr = "Dynamique",
        descriptionFr = "Lignes de force diagonales, tension visuelle"
    ),
    SYMMETRICAL(
        labelFr = "Symétrique",
        descriptionFr = "Alignement axial strict, équilibre géométrique"
    ),
    EDITORIAL(
        labelFr = "Éditorial",
        descriptionFr = "Style magazine contemporain, cadrage narratif"
    )
}

/**
 * Style d'instructions pour le futur moteur de guidage.
 */
enum class GuidanceType(val labelFr: String, val shortDescFr: String) {
    CONTINUOUS(
        labelFr = "Continu",
        shortDescFr = "Mise à jour en temps réel des repères"
    ),
    STEP_BY_STEP(
        labelFr = "Pas à pas",
        shortDescFr = "Une correction d'angle ou d'axe à la fois"
    )
}
