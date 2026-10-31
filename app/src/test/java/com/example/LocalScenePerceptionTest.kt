package com.example

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.detection.ConfidenceFilterSettings
import com.example.detection.ConfidenceTier
import com.example.detection.DetectionDisplayMode
import com.example.detection.DetectionLabelLayoutManager
import com.example.detection.DetectedObject
import com.example.detection.LocalObjectDetector
import com.example.detection.ObjectRelevanceRole
import com.example.detection.ObjectTracker
import com.example.detection.PerceptionStateEnum
import com.example.detection.PhotographyRelevanceProfile
import com.example.detection.SubjectSelectionEngine
import com.example.detection.SubjectVisualState
import com.example.guidance.GuidanceMode
import com.example.guidance.PhotographyCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalScenePerceptionTest {

    private val tracker = ObjectTracker(smoothingFactor = 0.60f, maxMissedFrames = 3)
    private val selectionEngine = SubjectSelectionEngine()

    @Test
    fun `model asset file exists in assets folder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = context.assets.list("") ?: emptyArray()
        assertTrue("Le fichier modèle efficientdet_lite0.tflite doit être présent dans assets/", assets.contains("efficientdet_lite0.tflite"))
    }

    @Test
    fun `bounding box normalization and coordinate clamping`() {
        val box = RectF(0.20f, 0.30f, 0.60f, 0.70f)
        val obj = DetectedObject(
            id = "test_1",
            label = "Tasse",
            confidence = 0.85f,
            boundingBox = box
        )

        assertEquals(0.40f, obj.normalizedCenterX, 0.001f)
        assertEquals(0.50f, obj.normalizedCenterY, 0.001f)
        assertEquals(0.40f, obj.normalizedWidth, 0.001f)
        assertEquals(0.40f, obj.normalizedHeight, 0.001f)
        assertEquals(0.16f, obj.areaFraction, 0.001f)
        assertEquals(85, obj.confidencePercent)
        assertEquals(ConfidenceTier.HIGH, obj.confidenceTier)
        assertEquals("Tasse · 85%", obj.displayLabel)
    }

    @Test
    fun `confidence tier categorization reflects thresholds accurately`() {
        val highObj = DetectedObject(id = "1", label = "A", confidence = 0.75f, boundingBox = RectF(0f, 0f, 0.1f, 0.1f))
        val medObj = DetectedObject(id = "2", label = "B", confidence = 0.55f, boundingBox = RectF(0f, 0f, 0.1f, 0.1f))
        val lowObj = DetectedObject(id = "3", label = "C", confidence = 0.35f, boundingBox = RectF(0f, 0f, 0.1f, 0.1f))

        assertEquals(ConfidenceTier.HIGH, highObj.confidenceTier)
        assertEquals(ConfidenceTier.MEDIUM, medObj.confidenceTier)
        assertEquals(ConfidenceTier.LOW, lowObj.confidenceTier)

        val settings = ConfidenceFilterSettings(highThreshold = 0.80f, mediumThreshold = 0.50f)
        assertEquals(ConfidenceTier.MEDIUM, settings.classify(0.75f))
        assertEquals(ConfidenceTier.HIGH, settings.classify(0.85f))
        assertEquals(ConfidenceTier.LOW, settings.classify(0.40f))
    }

    @Test
    fun `object tracker calculates IoU correctly`() {
        val box1 = RectF(0f, 0f, 0.5f, 0.5f) // area = 0.25
        val box2 = RectF(0f, 0f, 0.5f, 0.5f) // identical -> IoU = 1.0
        val box3 = RectF(0.6f, 0.6f, 1f, 1f)  // non-overlapping -> IoU = 0.0

        val iouSame = tracker.computeIoU(box1, box2)
        val iouDisjoint = tracker.computeIoU(box1, box3)

        assertEquals(1.0f, iouSame, 0.001f)
        assertEquals(0.0f, iouDisjoint, 0.001f)

        // Partiellement chevauchant : 0.25 x 0.5 d'intersection
        val box4 = RectF(0.25f, 0f, 0.75f, 0.5f)
        val iouOverlap = tracker.computeIoU(box1, box4)
        assertTrue("IoU partiel doit être compris entre 0 et 1", iouOverlap in 0.3f..0.4f)
    }

    @Test
    fun `object tracker suppresses duplicate detections with NMS`() {
        // Deux détections fortement chevauchantes du même objet physique
        val det1 = DetectedObject(
            id = "det_1",
            label = "Tasse",
            confidence = 0.88f,
            boundingBox = RectF(0.20f, 0.20f, 0.50f, 0.50f)
        )
        val det2 = DetectedObject(
            id = "det_2",
            label = "Tasse",
            confidence = 0.62f,
            boundingBox = RectF(0.22f, 0.21f, 0.51f, 0.51f) // IoU > 0.70 avec det1
        )
        val det3 = DetectedObject(
            id = "det_3",
            label = "Livre",
            confidence = 0.75f,
            boundingBox = RectF(0.60f, 0.60f, 0.90f, 0.90f) // Distinct et éloigné
        )

        val filtered = tracker.suppressDuplicates(listOf(det1, det2, det3), iouThreshold = 0.50f)

        assertEquals("Le doublon de faible confiance doit être supprimé", 2, filtered.size)
        assertTrue("La tasse la plus confiante doit être conservée", filtered.any { it.id == "det_1" })
        assertTrue("Le livre distinct doit être conservé", filtered.any { it.id == "det_3" })
        assertFalse("Le doublon de tasse doit être éliminé", filtered.any { it.id == "det_2" })
    }

    @Test
    fun `object tracker keeps rawBoundingBox separate from smoothed boundingBox`() {
        tracker.reset()

        val rawObj1 = DetectedObject(
            id = "raw_1",
            label = "Bouteille",
            confidence = 0.90f,
            boundingBox = RectF(0.10f, 0.10f, 0.30f, 0.30f),
            rawBoundingBox = RectF(0.10f, 0.10f, 0.30f, 0.30f)
        )
        val frame1 = tracker.update(listOf(rawObj1))
        assertEquals(1, frame1.size)

        // Frame 2 : variation brusque des coordonnées brutes
        val rawObj2 = DetectedObject(
            id = "raw_2",
            label = "Bouteille",
            confidence = 0.90f,
            boundingBox = RectF(0.20f, 0.20f, 0.40f, 0.40f),
            rawBoundingBox = RectF(0.20f, 0.20f, 0.40f, 0.40f)
        )
        val frame2 = tracker.update(listOf(rawObj2))
        assertEquals(1, frame2.size)

        val tracked = frame2[0]
        assertEquals("rawBoundingBox doit contenir exactement les coordonnées brutes", 0.20f, tracked.rawBoundingBox.left, 0.001f)
        // boundingBox lissée : (0.60 * 0.20) + (0.40 * 0.10) = 0.16
        assertEquals("boundingBox doit être filtrée temporellement", 0.16f, tracked.boundingBox.left, 0.001f)
        assertNotEquals(tracked.rawBoundingBox.left, tracked.boundingBox.left)
    }

    @Test
    fun `object tracker maintains persistence during temporary dropouts`() {
        tracker.reset()

        val rawObj = DetectedObject(
            id = "raw_1",
            label = "Bouteille",
            confidence = 0.90f,
            boundingBox = RectF(0.2f, 0.2f, 0.5f, 0.5f)
        )

        // Image 1 : détection initiale
        val frame1Tracks = tracker.update(listOf(rawObj))
        assertEquals(1, frame1Tracks.size)
        val assignedTrackId = frame1Tracks[0].trackingId

        // Image 2 : absence de détection (missed frame 1) -> conservé par grâce anti-clignotement
        val frame2Tracks = tracker.update(emptyList())
        assertEquals(1, frame2Tracks.size)
        assertEquals(assignedTrackId, frame2Tracks[0].trackingId)
        assertEquals(1, frame2Tracks[0].missedFrames)

        // Image 3 & 4 : conservation
        tracker.update(emptyList())
        tracker.update(emptyList())

        // Image 5 : tolérance dépassée (4 > maxMissedFrames 3) -> purgé
        val frame5Tracks = tracker.update(emptyList())
        assertEquals(0, frame5Tracks.size)
    }

    @Test
    fun `automatic subject selection heuristic scores central and prominent object higher`() {
        val centralObject = DetectedObject(
            id = "obj_center",
            label = "Tasse",
            confidence = 0.90f,
            boundingBox = RectF(0.3f, 0.3f, 0.7f, 0.7f),
            consecutiveFrames = 4
        )

        val cornerTinyObject = DetectedObject(
            id = "obj_corner",
            label = "Objet",
            confidence = 0.50f,
            boundingBox = RectF(0.01f, 0.01f, 0.11f, 0.11f),
            consecutiveFrames = 1
        )

        val scoreCenter = selectionEngine.computeSubjectScore(centralObject)
        val scoreCorner = selectionEngine.computeSubjectScore(cornerTinyObject)

        assertTrue(
            "Le sujet central et bien proportionné ($scoreCenter) doit surpasser l'objet d'angle ($scoreCorner)",
            scoreCenter > scoreCorner
        )

        val result = selectionEngine.processSelection(listOf(cornerTinyObject, centralObject))
        assertEquals("obj_center", result.selectedSubject?.id)
        assertFalse(result.isManualSelection)
    }

    @Test
    fun `hysteresis prevents rapid flapping between subjects with close scores`() {
        selectionEngine.reset()

        // Objet A établi comme sujet sélectionné
        val objA = DetectedObject(
            id = "obj_A",
            label = "Tasse",
            confidence = 0.82f,
            boundingBox = RectF(0.30f, 0.30f, 0.60f, 0.60f),
            consecutiveFrames = 5
        )
        val frame1 = selectionEngine.processSelection(listOf(objA))
        assertEquals("obj_A", frame1.selectedSubject?.id)

        // Frame 2 : Objet B apparaît avec un score à peine supérieur (+0.04, < delta hystérésis 0.12)
        val objB = DetectedObject(
            id = "obj_B",
            label = "Bouteille",
            confidence = 0.86f,
            boundingBox = RectF(0.31f, 0.30f, 0.61f, 0.60f),
            consecutiveFrames = 5
        )
        val frame2 = selectionEngine.processSelection(listOf(objA, objB))
        // Grâce à l'hystérésis, objA conserve le focus photographique stable
        assertEquals("L'hystérésis doit stabiliser obj_A et éviter le basculement intempestif", "obj_A", frame2.selectedSubject?.id)

        // Frame 3 : Objet C apparaît très nettement supérieur (+0.30 en score)
        val objC = DetectedObject(
            id = "obj_C",
            label = "Sujet Majeur",
            confidence = 0.99f,
            boundingBox = RectF(0.20f, 0.20f, 0.80f, 0.80f),
            consecutiveFrames = 6
        )
        val frame3 = selectionEngine.processSelection(listOf(objA, objB, objC))
        assertEquals("Un avantage de score supérieur à l'hystérésis doit permettre la sélection", "obj_C", frame3.selectedSubject?.id)
    }

    @Test
    fun `manual subject locking has absolute priority and cannot be replaced automatically`() {
        selectionEngine.reset()

        val obj1 = DetectedObject(
            id = "obj_1",
            trackingId = 42,
            label = "Tasse",
            confidence = 0.65f,
            boundingBox = RectF(0.10f, 0.10f, 0.30f, 0.30f)
        )
        val obj2Super = DetectedObject(
            id = "obj_2",
            trackingId = 99,
            label = "Personne Centrale",
            confidence = 0.98f,
            boundingBox = RectF(0.25f, 0.25f, 0.75f, 0.75f)
        )

        // Verrouillage manuel explicite de obj1
        selectionEngine.lockSubjectManually("obj_1", trackingId = 42)

        val result = selectionEngine.processSelection(listOf(obj1, obj2Super))
        assertEquals("Le sujet verrouillé manuellement doit être sélectionné", "obj_1", result.selectedSubject?.id)
        assertTrue(result.isManualLocked)
        assertTrue(result.selectedSubject?.isLocked == true)
        assertFalse(result.isSubjectLost)

        // Déverrouillage manuel
        selectionEngine.unlockSubject()
        val unlockedResult = selectionEngine.processSelection(listOf(obj1, obj2Super))
        assertEquals("Après déverrouillage, l'heuristique automatique reprend ses droits", "obj_2", unlockedResult.selectedSubject?.id)
        assertFalse(unlockedResult.isManualLocked)
    }

    @Test
    fun `subject loss is detected when locked subject leaves frame`() {
        selectionEngine.reset()

        val obj1 = DetectedObject(
            id = "obj_1",
            trackingId = 12,
            label = "Tasse",
            confidence = 0.80f,
            boundingBox = RectF(0.3f, 0.3f, 0.6f, 0.6f)
        )

        selectionEngine.lockSubjectManually("obj_1", trackingId = 12)

        // Trame avec le sujet présent
        val resPresent = selectionEngine.processSelection(listOf(obj1))
        assertFalse(resPresent.isSubjectLost)

        // Trame où le sujet verrouillé a quitté le cadre
        val otherObj = DetectedObject(
            id = "other",
            trackingId = 88,
            label = "Chaise",
            confidence = 0.70f,
            boundingBox = RectF(0.1f, 0.1f, 0.2f, 0.2f)
        )
        val resLost = selectionEngine.processSelection(listOf(otherObj))
        assertTrue("isSubjectLost doit être vrai lorsque l'objet verrouillé disparaît", resLost.isSubjectLost)
    }

    @Test
    fun `detection label layout manager prevents label collisions and respects screen bounds`() {
        val layoutMgr = DetectionLabelLayoutManager(
            screenMarginHorizontal = 10f,
            topExclusionBoundary = 80f,
            bottomExclusionBoundary = 150f
        )

        // Deux objets très proches verticalement risquant une collision d'étiquettes
        val obj1 = DetectedObject(
            id = "o1",
            label = "Tasse",
            confidence = 0.90f,
            boundingBox = RectF(0.2f, 0.3f, 0.4f, 0.5f),
            isSelectedSubject = true
        )
        val obj2 = DetectedObject(
            id = "o2",
            label = "Bouteille",
            confidence = 0.80f,
            boundingBox = RectF(0.2f, 0.35f, 0.4f, 0.55f),
            isSelectedSubject = false
        )

        val projectedBoxes = mapOf(
            "o1" to RectF(100f, 200f, 300f, 400f),
            "o2" to RectF(100f, 210f, 300f, 410f)
        )

        val labels = layoutMgr.layoutLabels(
            detectedObjects = listOf(obj1, obj2),
            projectedBoxes = projectedBoxes,
            screenWidth = 1080f,
            screenHeight = 1920f,
            estimatedLabelWidth = { 120f },
            estimatedLabelHeight = 30f,
            displayMode = DetectionDisplayMode.ESSENTIAL
        )

        assertEquals(2, labels.size)
        val l1 = labels[0]
        val l2 = labels[1]

        // Vérifier qu'il n'y a pas de collision verticale directe entre l1 et l2
        val l1Bottom = l1.labelY + l1.labelHeight
        val l2Top = l2.labelY
        val l2Bottom = l2.labelY + l2.labelHeight
        val l1Top = l1.labelY

        val collides = (l1.labelX < l2.labelX + l2.labelWidth &&
                l1.labelX + l1.labelWidth > l2.labelX &&
                l1.labelY < l2Bottom &&
                l1Bottom > l2Top)

        assertFalse("Les étiquettes positionnées ne doivent pas entrer en collision", collides)

        // Vérifier le respect strict des bornes écran
        for (l in labels) {
            assertTrue("labelX doit respecter la marge gauche", l.labelX >= 10f)
            assertTrue("labelX + labelWidth doit respecter la marge droite", l.labelX + l.labelWidth <= 1080f)
            assertTrue("labelY doit être en dessous du bandeau supérieur", l.labelY >= 80f)
            assertTrue("labelY + labelHeight doit être au-dessus du déclencheur", l.labelY + l.labelHeight <= 1920f - 150f)
        }
    }

    @Test
    fun `perception state enum labels match requirements`() {
        assertEquals("Initialisation du moteur visuel...", PerceptionStateEnum.INITIALIZING.defaultMessageFr)
        assertEquals("CAMÉRA ACTIVE · ANALYSE VISUELLE LOCALE", PerceptionStateEnum.ACTIVE.defaultMessageFr)
        assertEquals("Objet détecté", PerceptionStateEnum.OBJECT_FOUND.defaultMessageFr)
        assertEquals("Aucun objet reconnu dans cette image", PerceptionStateEnum.NO_OBJECT.defaultMessageFr)
        assertEquals("Identification incertaine", PerceptionStateEnum.LOW_CONFIDENCE.defaultMessageFr)
        assertEquals("Sujet verrouillé perdu · Touchez pour reverrouiller", PerceptionStateEnum.SUBJECT_LOST.defaultMessageFr)
        assertEquals("Détection momentanément indisponible", PerceptionStateEnum.ERROR.defaultMessageFr)
    }

    @Test
    fun `essential mode limits label count and hides low confidence labels by default`() {
        val layoutMgr = DetectionLabelLayoutManager(
            screenMarginHorizontal = 10f,
            topExclusionBoundary = 80f,
            bottomExclusionBoundary = 150f
        )

        val selectedSubj = DetectedObject(id = "s", label = "Sujet Principal", confidence = 0.90f, boundingBox = RectF(0.3f, 0.3f, 0.6f, 0.6f), isSelectedSubject = true)
        val highObj = DetectedObject(id = "h", label = "Secondaire Haut", confidence = 0.80f, boundingBox = RectF(0.1f, 0.1f, 0.2f, 0.2f))
        val medObj = DetectedObject(id = "m", label = "Secondaire Moyen", confidence = 0.55f, boundingBox = RectF(0.7f, 0.1f, 0.8f, 0.2f))
        val lowObj = DetectedObject(id = "l", label = "Faible Bruit", confidence = 0.35f, boundingBox = RectF(0.1f, 0.7f, 0.2f, 0.8f))

        val boxes = mapOf(
            "s" to RectF(300f, 300f, 600f, 600f),
            "h" to RectF(100f, 100f, 200f, 200f),
            "m" to RectF(700f, 100f, 800f, 200f),
            "l" to RectF(100f, 700f, 200f, 800f)
        )

        // Mode Essentiel avec max 3 étiquettes
        val essentialLabels = layoutMgr.layoutLabels(
            detectedObjects = listOf(selectedSubj, highObj, medObj, lowObj),
            projectedBoxes = boxes,
            screenWidth = 1080f,
            screenHeight = 1920f,
            estimatedLabelWidth = { 100f },
            estimatedLabelHeight = 28f,
            displayMode = DetectionDisplayMode.ESSENTIAL,
            maxLabelsInEssentialMode = 3
        )

        assertEquals("Le mode essentiel doit limiter à 3 étiquettes", 3, essentialLabels.size)
        assertTrue("Le sujet principal doit toujours être présent", essentialLabels.any { it.objectId == "s" })
        assertFalse("L'objet de faible confiance (< 45%) doit être masqué", essentialLabels.any { it.objectId == "l" })
        // Étiquettes épurées sans pourcentage
        val subjectLabel = essentialLabels.first { it.objectId == "s" }
        assertFalse("L'étiquette essentielle ne doit pas afficher le pourcentage", subjectLabel.displayLabel.contains("%"))
    }

    @Test
    fun `diagnostic mode displays all detections with full telemetry and confidence percentages`() {
        val layoutMgr = DetectionLabelLayoutManager(
            screenMarginHorizontal = 10f,
            topExclusionBoundary = 80f,
            bottomExclusionBoundary = 150f
        )

        val obj1 = DetectedObject(id = "1", trackingId = 10, label = "Tasse", confidence = 0.88f, boundingBox = RectF(0.2f, 0.2f, 0.4f, 0.4f))
        val obj2 = DetectedObject(id = "2", trackingId = 11, label = "Livre", confidence = 0.38f, boundingBox = RectF(0.6f, 0.6f, 0.8f, 0.8f))

        val boxes = mapOf(
            "1" to RectF(200f, 200f, 400f, 400f),
            "2" to RectF(600f, 600f, 800f, 800f)
        )

        val diagLabels = layoutMgr.layoutLabels(
            detectedObjects = listOf(obj1, obj2),
            projectedBoxes = boxes,
            screenWidth = 1080f,
            screenHeight = 1920f,
            estimatedLabelWidth = { 140f },
            estimatedLabelHeight = 28f,
            displayMode = DetectionDisplayMode.DIAGNOSTIC
        )

        assertEquals("Le mode diagnostic doit inclure tous les objets même de faible confiance", 2, diagLabels.size)
        assertTrue("L'étiquette diagnostic doit inclure le pourcentage et le track ID", diagLabels[0].displayLabel.contains("88%"))
        assertTrue("L'étiquette de faible confiance doit afficher son pourcentage", diagLabels[1].displayLabel.contains("38%"))
    }

    @Test
    fun `detection label layout manager supports connector lines when offset is required`() {
        val layoutMgr = DetectionLabelLayoutManager(
            screenMarginHorizontal = 10f,
            topExclusionBoundary = 100f,
            bottomExclusionBoundary = 150f,
            labelSpacing = 4f
        )

        // Objet placé tout en haut contre la zone d'exclusion supérieure
        val obj = DetectedObject(id = "top_obj", label = "Objet Haut", confidence = 0.85f, boundingBox = RectF(0.1f, 0.05f, 0.3f, 0.08f))
        val boxes = mapOf("top_obj" to RectF(100f, 90f, 300f, 130f))

        val labels = layoutMgr.layoutLabels(
            detectedObjects = listOf(obj),
            projectedBoxes = boxes,
            screenWidth = 1080f,
            screenHeight = 1920f,
            estimatedLabelWidth = { 100f },
            estimatedLabelHeight = 25f,
            displayMode = DetectionDisplayMode.ESSENTIAL
        )

        assertEquals(1, labels.size)
        val l = labels[0]
        assertTrue("L'étiquette doit rester en dessous de la zone d'exclusion supérieure", l.labelY >= 100f)
        assertTrue("L'étiquette doit rester au-dessus de la zone inférieure", l.labelY + l.labelHeight <= 1920f - 150f)
    }

    @Test
    fun `duplicate suppression preserves distinct physical objects of identical label`() {
        // Deux tasses distinctes côte à côte (IoU = 0, distance physique nette)
        val cup1 = DetectedObject(id = "cup_1", label = "Tasse", confidence = 0.85f, boundingBox = RectF(0.10f, 0.30f, 0.30f, 0.50f))
        val cup2 = DetectedObject(id = "cup_2", label = "Tasse", confidence = 0.82f, boundingBox = RectF(0.60f, 0.30f, 0.80f, 0.50f))

        val clean = tracker.suppressDuplicates(listOf(cup1, cup2), iouThreshold = 0.50f)

        assertEquals("Deux objets physiques distincts avec le même label ne doivent JAMAIS être fusionnés", 2, clean.size)
        assertTrue(clean.any { it.id == "cup_1" })
        assertTrue(clean.any { it.id == "cup_2" })
    }

    @Test
    fun `duplicate suppression detects and removes containment duplicates`() {
        // Une détection englobant presque entièrement une autre détection du même objet
        val bigBox = DetectedObject(id = "person_outer", label = "Personne", confidence = 0.90f, boundingBox = RectF(0.20f, 0.20f, 0.50f, 0.80f))
        val smallBox = DetectedObject(id = "person_inner", label = "Personne", confidence = 0.70f, boundingBox = RectF(0.22f, 0.22f, 0.48f, 0.78f))

        val clean = tracker.suppressDuplicates(listOf(bigBox, smallBox), iouThreshold = 0.50f)

        assertEquals("Le doublon contenu doit être éliminé", 1, clean.size)
        assertEquals("person_outer", clean[0].id)
    }

    @Test
    fun `photography relevance profiles assign appropriate roles for FOOD and PRODUCT`() {
        val foodProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.FOOD, GuidanceMode.GUIDED)
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, foodProfile.getRole("cup"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, foodProfile.getRole("wine glass"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, foodProfile.getRole("bottle"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, foodProfile.getRole("sandwich"))
        assertEquals(ObjectRelevanceRole.CONTEXT_OBJECT, foodProfile.getRole("dining table"))
        assertEquals(ObjectRelevanceRole.CONTEXT_OBJECT, foodProfile.getRole("chair"))

        val productProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.PRODUCT, GuidanceMode.GUIDED)
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, productProfile.getRole("bottle"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, productProfile.getRole("handbag"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, productProfile.getRole("laptop"))
        assertEquals(ObjectRelevanceRole.PRIMARY_CANDIDATE, productProfile.getRole("cell phone"))
        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, productProfile.getRole("couch"))
    }

    @Test
    fun `background rejection penalizes large dining table in favor of foreground cup`() {
        val engine = SubjectSelectionEngine()
        engine.updateContext(PhotographyCategory.FOOD, GuidanceMode.GUIDED)

        // Table à manger occupant 60% de l'image (arrière-plan/contexte)
        val table = DetectedObject(
            id = "table_1",
            label = "Table",
            rawModelLabel = "dining table",
            confidence = 0.88f,
            boundingBox = RectF(0.05f, 0.20f, 0.95f, 0.85f), // area = 0.90 * 0.65 = 0.585 (58.5%)
            relevanceRole = ObjectRelevanceRole.CONTEXT_OBJECT
        )

        // Tasse au premier plan au centre (15% de l'image)
        val cup = DetectedObject(
            id = "cup_1",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.82f,
            boundingBox = RectF(0.35f, 0.40f, 0.65f, 0.70f), // area = 0.30 * 0.30 = 0.09 (9%)
            relevanceRole = ObjectRelevanceRole.PRIMARY_CANDIDATE
        )

        val result = engine.evaluate(listOf(table, cup))

        assertNotNull(result.selectedSubject)
        assertEquals("La tasse doit être sélectionnée comme sujet principal malgré la grande table", "cup_1", result.selectedSubject?.id)
        assertFalse("Il ne doit pas y avoir d'ambiguïté car la table est contextuelle", result.isAmbiguous)
    }

    @Test
    fun `ambiguous candidates trigger choice guidance message`() {
        val engine = SubjectSelectionEngine()
        engine.updateContext(PhotographyCategory.FOOD, GuidanceMode.GUIDED)

        // Deux objets culinaires primaires de score quasi-identique au centre
        val cup1 = DetectedObject(
            id = "cup_left",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.85f,
            boundingBox = RectF(0.20f, 0.35f, 0.48f, 0.65f),
            relevanceRole = ObjectRelevanceRole.PRIMARY_CANDIDATE
        )

        val cup2 = DetectedObject(
            id = "cup_right",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.84f,
            boundingBox = RectF(0.52f, 0.35f, 0.80f, 0.65f),
            relevanceRole = ObjectRelevanceRole.PRIMARY_CANDIDATE
        )

        val result = engine.evaluate(listOf(cup1, cup2))

        assertTrue("Deux sujets équivalents doivent déclencher l'état d'ambiguïté", result.isAmbiguous)
        assertEquals("Plusieurs sujets possibles — Touchez pour choisir.", result.ambiguityMessage)
    }

    @Test
    fun `COCO label translations match French photography terminology`() {
        assertEquals("Tasse", LocalObjectDetector.getFrenchLabel("cup"))
        assertEquals("Verre à pied", LocalObjectDetector.getFrenchLabel("wine glass"))
        assertEquals("Bouteille", LocalObjectDetector.getFrenchLabel("bottle"))
        assertEquals("Table", LocalObjectDetector.getFrenchLabel("dining table"))
        assertEquals("Sandwich", LocalObjectDetector.getFrenchLabel("sandwich"))
        assertEquals("Bol", LocalObjectDetector.getFrenchLabel("bowl"))
        assertEquals("Fourchette", LocalObjectDetector.getFrenchLabel("fork"))
        assertEquals("Cuillère", LocalObjectDetector.getFrenchLabel("spoon"))
        assertEquals("Téléphone portable", LocalObjectDetector.getFrenchLabel("cell phone"))
    }

    @Test
    fun `object tracker area consistency rejects sudden massive area jumps`() {
        tracker.reset()

        val initialObj = DetectedObject(
            id = "cup_init",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.85f,
            boundingBox = RectF(0.40f, 0.40f, 0.60f, 0.60f) // area = 0.04
        )
        val frame1 = tracker.update(listOf(initialObj))
        assertEquals(1, frame1.size)
        val trackId = frame1[0].trackingId
        assertNotNull(trackId)

        // Frame 2 : un objet avec le même label mais une aire 10x plus grande (saut d'échelle irréaliste)
        val jumpedObj = DetectedObject(
            id = "cup_jumped",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.85f,
            boundingBox = RectF(0.05f, 0.05f, 0.95f, 0.95f) // area = 0.81
        )
        val frame2 = tracker.update(listOf(jumpedObj))
        assertEquals(1, frame2.size)
        assertNotEquals("Un saut de surface massif doit rejeter l'association de piste", trackId, frame2[0].trackingId)
    }

    @Test
    fun `AUTOMATIC MODE - contextual objects do not systematically dominate foreground candidates`() {
        val engine = SubjectSelectionEngine()
        engine.updatePhotographyContext(category = null, automaticMode = true)

        // Grande table (contexte / arrière-plan)
        val diningTable = DetectedObject(
            id = "table_auto",
            label = "Table",
            rawModelLabel = "dining table",
            confidence = 0.89f,
            boundingBox = RectF(0.05f, 0.25f, 0.95f, 0.85f), // Surface 54%
            relevanceRole = ObjectRelevanceRole.BACKGROUND_OBJECT
        )

        // Téléphone portable au premier plan
        val cellPhone = DetectedObject(
            id = "phone_auto",
            label = "Téléphone portable",
            rawModelLabel = "cell phone",
            confidence = 0.84f,
            boundingBox = RectF(0.38f, 0.45f, 0.62f, 0.72f), // Surface ~6.5%
            relevanceRole = ObjectRelevanceRole.PRIMARY_CANDIDATE
        )

        val result = engine.processSelection(listOf(diningTable, cellPhone))

        assertNotNull("Un sujet doit être sélectionné", result.selectedSubject)
        assertEquals(
            "Le téléphone en avant-plan doit surpasser la table de fond en mode automatique",
            "phone_auto",
            result.selectedSubject?.id
        )
        assertFalse(result.isAmbiguous)
    }

    @Test
    fun `GUIDED FOOD - category relevance prioritizes culinary elements over furniture`() {
        val foodProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.FOOD, GuidanceMode.GUIDED)

        // Objets culinaires principaux
        val culinaryLabels = listOf(
            "sandwich", "cake", "pizza", "hot dog", "donut",
            "apple", "banana", "orange", "bowl", "cup", "bottle",
            "wine glass", "fork", "spoon", "knife"
        )
        culinaryLabels.forEach { label ->
            assertEquals(
                "L'objet culinaire '$label' doit être candidat prioritaire",
                ObjectRelevanceRole.PRIMARY_CANDIDATE,
                foodProfile.getRole(label)
            )
        }

        // Meubles et contexte
        assertEquals(ObjectRelevanceRole.CONTEXT_OBJECT, foodProfile.getRole("chair"))
        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, foodProfile.getRole("dining table"))
        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, foodProfile.getRole("couch"))
    }

    @Test
    fun `GUIDED PRODUCT - category relevance prioritizes product and tech items`() {
        val productProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.PRODUCT, GuidanceMode.GUIDED)

        val productLabels = listOf(
            "bottle", "cup", "cell phone", "laptop", "mouse",
            "keyboard", "handbag", "backpack", "book", "clock", "vase"
        )
        productLabels.forEach { label ->
            assertEquals(
                "Le produit '$label' doit être candidat prioritaire",
                ObjectRelevanceRole.PRIMARY_CANDIDATE,
                productProfile.getRole(label)
            )
        }

        // Mobilier pénalisé
        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, productProfile.getRole("couch"))
        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, productProfile.getRole("bed"))
        assertEquals(ObjectRelevanceRole.CONTEXT_OBJECT, productProfile.getRole("chair"))
    }

    @Test
    fun `STILL LIFE - prioritizes foreground artistic objects`() {
        val stillLifeProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.STILL_LIFE, GuidanceMode.GUIDED)

        val stillLifeLabels = listOf("vase", "bottle", "cup", "wine glass", "bowl", "book", "potted plant", "clock")
        stillLifeLabels.forEach { label ->
            assertEquals(
                "L'objet '$label' doit être candidat prioritaire en nature morte",
                ObjectRelevanceRole.PRIMARY_CANDIDATE,
                stillLifeProfile.getRole(label)
            )
        }

        assertEquals(ObjectRelevanceRole.BACKGROUND_OBJECT, stillLifeProfile.getRole("dining table"))
    }

    @Test
    fun `FLAT LAY - preserves relevant objects distributed across the surface`() {
        val flatLayProfile = PhotographyRelevanceProfile.forContext(PhotographyCategory.FLAT_LAY, GuidanceMode.GUIDED)

        val flatLayLabels = listOf(
            "sandwich", "cake", "cup", "bottle", "bowl", "fork", "spoon",
            "cell phone", "book", "handbag", "scissors", "apple", "clock"
        )
        flatLayLabels.forEach { label ->
            assertEquals(
                "L'objet en flat lay '$label' doit être reconnu comme candidat prioritaire",
                ObjectRelevanceRole.PRIMARY_CANDIDATE,
                flatLayProfile.getRole(label)
            )
        }
    }

    @Test
    fun `SUBJECT SELECTION STATES - transitions between Proposed, Ambiguous and Manually Locked`() {
        val engine = SubjectSelectionEngine()
        engine.updatePhotographyContext(category = PhotographyCategory.PRODUCT, automaticMode = false)

        val objA = DetectedObject(
            id = "product_1",
            label = "Bouteille",
            rawModelLabel = "bottle",
            confidence = 0.85f,
            boundingBox = RectF(0.40f, 0.40f, 0.60f, 0.70f)
        )

        // 1. État PROPOSED (sélection automatique nette)
        val res1 = engine.processSelection(listOf(objA))
        assertEquals(SubjectVisualState.PROPOSED, res1.visualState)
        assertFalse(res1.isManualLocked)
        assertFalse(res1.isAmbiguous)

        // 2. État AMBIGUOUS (deux candidats de score quasi-identique)
        val objB = DetectedObject(
            id = "product_2",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.84f,
            boundingBox = RectF(0.15f, 0.40f, 0.35f, 0.70f)
        )
        val res2 = engine.processSelection(listOf(objA, objB))
        assertEquals(SubjectVisualState.AMBIGUOUS, res2.visualState)
        assertTrue(res2.isAmbiguous)
        assertEquals("Plusieurs sujets possibles — Touchez pour choisir.", res2.ambiguityMessage)

        // 3. État MANUALLY LOCKED (l'utilisateur verrouille un sujet)
        engine.lockSubjectManually("product_2")
        val res3 = engine.processSelection(listOf(objA, objB))
        assertEquals(SubjectVisualState.MANUALLY_LOCKED, res3.visualState)
        assertTrue(res3.isManualLocked)
        assertFalse("Le verrouillage manuel a priorité absolue et doit annuler l'ambiguïté", res3.isAmbiguous)
        assertEquals("product_2", res3.selectedSubject?.id)

        // 4. Déverrouillage (retour à la sélection automatique ou ambiguïté)
        engine.unlockSubject()
        val res4 = engine.processSelection(listOf(objA, objB))
        assertFalse(res4.isManualLocked)
        assertEquals(SubjectVisualState.AMBIGUOUS, res4.visualState)
    }

    @Test
    fun `ESSENTIAL MODE - excludes labels for context and background objects`() {
        val layoutMgr = DetectionLabelLayoutManager(
            screenMarginHorizontal = 16f,
            topExclusionBoundary = 120f,
            bottomExclusionBoundary = 180f
        )

        val subject = DetectedObject(
            id = "subj",
            label = "Tasse",
            rawModelLabel = "cup",
            confidence = 0.90f,
            boundingBox = RectF(0.3f, 0.4f, 0.5f, 0.6f),
            isSelectedSubject = true,
            relevanceRole = ObjectRelevanceRole.PRIMARY_CANDIDATE
        )
        val contextChair = DetectedObject(
            id = "chair_1",
            label = "Chaise",
            rawModelLabel = "chair",
            confidence = 0.82f,
            boundingBox = RectF(0.6f, 0.4f, 0.8f, 0.7f),
            isSelectedSubject = false,
            relevanceRole = ObjectRelevanceRole.CONTEXT_OBJECT
        )
        val backgroundTable = DetectedObject(
            id = "table_1",
            label = "Table",
            rawModelLabel = "dining table",
            confidence = 0.88f,
            boundingBox = RectF(0.1f, 0.7f, 0.9f, 0.95f),
            isSelectedSubject = false,
            relevanceRole = ObjectRelevanceRole.BACKGROUND_OBJECT
        )

        val boxes = mapOf(
            "subj" to RectF(300f, 600f, 500f, 900f),
            "chair_1" to RectF(600f, 600f, 800f, 1000f),
            "table_1" to RectF(100f, 1100f, 900f, 1500f)
        )

        val labels = layoutMgr.layoutLabels(
            detectedObjects = listOf(subject, contextChair, backgroundTable),
            projectedBoxes = boxes,
            screenWidth = 1080f,
            screenHeight = 1920f,
            estimatedLabelWidth = { 100f },
            estimatedLabelHeight = 25f,
            displayMode = DetectionDisplayMode.ESSENTIAL
        )

        assertEquals("Seul le sujet sélectionné doit recevoir une étiquette en mode essentiel (contexte et arrière-plan exclus)", 1, labels.size)
        assertEquals("subj", labels[0].objectId)
    }
}
