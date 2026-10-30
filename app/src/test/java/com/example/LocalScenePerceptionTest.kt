package com.example

import android.content.Context
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.detection.ConfidenceFilterSettings
import com.example.detection.ConfidenceTier
import com.example.detection.DetectionDisplayMode
import com.example.detection.DetectionLabelLayoutManager
import com.example.detection.DetectedObject
import com.example.detection.ObjectTracker
import com.example.detection.PerceptionStateEnum
import com.example.detection.SubjectSelectionEngine
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
}
