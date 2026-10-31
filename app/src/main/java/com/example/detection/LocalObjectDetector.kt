package com.example.detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import java.io.Closeable
import java.io.IOException

/**
 * Encapsule le détecteur d'objets MediaPipe Tasks Vision avec le modèle local EfficientDet-Lite0 quantifié.
 * Fonctionne 100% sur l'appareil (on-device), sans aucun appel réseau ni API distante.
 */
class LocalObjectDetector(
    private val context: Context,
    private val modelAssetName: String = "efficientdet_lite0.tflite",
    private val scoreThreshold: Float = 0.30f,
    private val maxResults: Int = 6
) : Closeable {

    companion object {
        private const val TAG = "LocalObjectDetector"

        // Dictionnaire de traduction française des 80 classes standard COCO du modèle EfficientDet-Lite0
        val COCO_LABELS_FR: Map<String, String> = mapOf(
            "person" to "Personne",
            "bicycle" to "Vélo",
            "car" to "Voiture",
            "motorcycle" to "Moto",
            "airplane" to "Avion",
            "bus" to "Bus",
            "train" to "Train",
            "truck" to "Camion",
            "boat" to "Bateau",
            "traffic light" to "Feu tricolore",
            "fire hydrant" to "Borne d'incendie",
            "stop sign" to "Panneau stop",
            "parking meter" to "Horodateur",
            "bench" to "Banc",
            "bird" to "Oiseau",
            "cat" to "Chat",
            "dog" to "Chien",
            "horse" to "Cheval",
            "sheep" to "Mouton",
            "cow" to "Vache",
            "elephant" to "Éléphant",
            "bear" to "Ours",
            "zebra" to "Zèbre",
            "giraffe" to "Girafe",
            "backpack" to "Sac à dos",
            "umbrella" to "Parapluie",
            "handbag" to "Sac à main",
            "tie" to "Cravate",
            "suitcase" to "Valise",
            "frisbee" to "Frisbee",
            "skis" to "Skis",
            "snowboard" to "Snowboard",
            "sports ball" to "Ballon de sport",
            "kite" to "Cerf-volant",
            "baseball bat" to "Batte de baseball",
            "baseball glove" to "Gant de baseball",
            "skateboard" to "Skateboard",
            "surfboard" to "Planche de surf",
            "tennis racket" to "Raquette de tennis",
            "bottle" to "Bouteille",
            "wine glass" to "Verre à pied",
            "cup" to "Tasse",
            "fork" to "Fourchette",
            "knife" to "Couteau",
            "spoon" to "Cuillère",
            "bowl" to "Bol",
            "banana" to "Banane",
            "apple" to "Pomme",
            "sandwich" to "Sandwich",
            "orange" to "Orange",
            "broccoli" to "Brocoli",
            "carrot" to "Carotte",
            "hot dog" to "Hot-dog",
            "pizza" to "Pizza",
            "donut" to "Donut",
            "cake" to "Gâteau",
            "chair" to "Chaise",
            "couch" to "Canapé",
            "potted plant" to "Plante en pot",
            "bed" to "Lit",
            "dining table" to "Table",
            "toilet" to "Toilettes",
            "tv" to "Téléviseur",
            "laptop" to "Ordinateur portable",
            "mouse" to "Souris",
            "remote" to "Télécommande",
            "keyboard" to "Clavier",
            "cell phone" to "Téléphone",
            "microwave" to "Micro-ondes",
            "oven" to "Four",
            "toaster" to "Grille-pain",
            "sink" to "Évier",
            "refrigerator" to "Réfrigérateur",
            "book" to "Livre",
            "clock" to "Horloge",
            "vase" to "Vase",
            "scissors" to "Ciseaux",
            "teddy bear" to "Peluche",
            "hair drier" to "Sèche-cheveux",
            "toothbrush" to "Brosse à dents"
        )

        fun getFrenchLabel(rawLabel: String): String {
            return COCO_LABELS_FR[rawLabel.lowercase().trim()] ?: rawLabel
        }
    }

    private var objectDetector: ObjectDetector? = null
    var isInitialized: Boolean = false
        private set
    var lastError: String? = null
        private set

    /**
     * Initialise le détecteur MediaPipe Tasks Vision sur CPU (optimal pour compatibilité Galaxy S9).
     */
    @Synchronized
    fun initialize(): Boolean {
        if (isInitialized && objectDetector != null) return true

        return try {
            // Vérifier que le fichier modèle existe dans les assets
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(modelAssetName)) {
                val errorMsg = "Fichier modèle '$modelAssetName' introuvable dans assets"
                Log.e(TAG, errorMsg)
                lastError = errorMsg
                return false
            }

            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(modelAssetName)
                .setDelegate(Delegate.CPU) // Baseline fiable et stable pour tous appareils Android

            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setMaxResults(maxResults)
                .setScoreThreshold(scoreThreshold)
                .build()

            objectDetector = ObjectDetector.createFromOptions(context, options)
            isInitialized = true
            lastError = null
            Log.i(TAG, "Moteur MediaPipe ObjectDetector initialisé avec succès ($modelAssetName)")
            true
        } catch (e: Exception) {
            val errorMsg = "Échec d'initialisation MediaPipe: ${e.message}"
            Log.e(TAG, errorMsg, e)
            lastError = errorMsg
            isInitialized = false
            objectDetector = null
            false
        }
    }

    /**
     * Exécute l'inférence sur un Bitmap déjà redressé (orientation correcte).
     * Retourne la liste des objets avec leurs coordonnées normalisées (0f .. 1f) et la durée d'inférence en ms.
     */
    @Synchronized
    fun detectObjects(bitmap: Bitmap): DetectionInferenceResult {
        val detector = objectDetector
        if (!isInitialized || detector == null) {
            return DetectionInferenceResult(
                objects = emptyList(),
                inferenceTimeMs = 0L,
                error = lastError ?: "Détecteur non initialisé"
            )
        }

        val startTime = SystemClock.elapsedRealtime()
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = detector.detect(mpImage)
            val duration = SystemClock.elapsedRealtime() - startTime

            val imgWidth = bitmap.width.toFloat()
            val imgHeight = bitmap.height.toFloat()

            if (imgWidth <= 0f || imgHeight <= 0f) {
                return DetectionInferenceResult(emptyList(), duration)
            }

            val detectedList = mutableListOf<DetectedObject>()

            for ((index, detection) in result.detections().withIndex()) {
                val topCategory = detection.categories().maxByOrNull { it.score() }
                val rawLabel = topCategory?.categoryName() ?: "Objet"
                val score = topCategory?.score() ?: 0f

                // Traduction française élégante si disponible dans COCO
                val frenchLabel = getFrenchLabel(rawLabel)

                val box = detection.boundingBox()

                // Normalisation des coordonnées de la boîte englobante (0.0f .. 1.0f)
                val normLeft = (box.left / imgWidth).coerceIn(0f, 1f)
                val normTop = (box.top / imgHeight).coerceIn(0f, 1f)
                val normRight = (box.right / imgWidth).coerceIn(0f, 1f)
                val normBottom = (box.bottom / imgHeight).coerceIn(0f, 1f)

                // Rejeter les boîtes dégénérées (largeur ou hauteur nulle)
                if (normRight <= normLeft || normBottom <= normTop) continue

                val normalizedBox = RectF(normLeft, normTop, normRight, normBottom)

                detectedList.add(
                    DetectedObject(
                        id = "det_${System.currentTimeMillis()}_$index",
                        label = frenchLabel,
                        rawModelLabel = rawLabel.lowercase().trim(),
                        confidence = score,
                        boundingBox = normalizedBox
                    )
                )
            }

            DetectionInferenceResult(
                objects = detectedList,
                inferenceTimeMs = duration
            )
        } catch (e: Exception) {
            val duration = SystemClock.elapsedRealtime() - startTime
            Log.e(TAG, "Erreur durant l'inférence locale: ${e.message}", e)
            DetectionInferenceResult(
                objects = emptyList(),
                inferenceTimeMs = duration,
                error = e.localizedMessage
            )
        }
    }

    override fun close() {
        try {
            objectDetector?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Erreur fermeture détecteur: ${e.message}")
        } finally {
            objectDetector = null
            isInitialized = false
        }
    }
}

data class DetectionInferenceResult(
    val objects: List<DetectedObject>,
    val inferenceTimeMs: Long,
    val error: String? = null
)
