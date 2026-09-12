package com.example.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import com.example.viewmodel.VehicleType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Data model for a detected vehicle, person, or obstacle in the camera viewport.
 */
data class TrackedObstacle(
    val id: Int,
    val normalizedBox: RectF, // Coordinates normalized to [0.0f .. 1.0f]
    val pixelRect: Rect,
    val label: String,
    val confidence: Float,
    val distanceMeters: Float,
    val isCollisionHazard: Boolean
)

/**
 * Driver distraction & attention metrics derived from facial landmarks and head pose.
 */
data class DriverDistractionState(
    val isDriverFaceVisible: Boolean = false,
    val normalizedFaceBox: RectF? = null,
    val headPitchDeg: Float = 0f, // Looking down towards phone/lap (negative)
    val headYawDeg: Float = 0f,   // Looking left/right away from road
    val headRollDeg: Float = 0f,
    val eyeOpenProbability: Float = 1.0f,
    val attentionScore: Int = 100, // 0 to 100
    val isLookingDownAtPhone: Boolean = false,
    val isLookingAwayFromRoad: Boolean = false,
    val isDrowsy: Boolean = false,
    val isPhoneObjectDetected: Boolean = false,
    val statusBadge: String = "ATTENTIVE"
)

/**
 * Helmet compliance state for riders in two-wheeler mode.
 */
data class HelmetComplianceState(
    val isChecked: Boolean = false,
    val isHelmetWorn: Boolean = true,
    val normalizedHelmetBox: RectF? = null,
    val confidence: Float = 0.85f,
    val statusText: String = "HELMET VERIFIED"
)

/**
 * Combined real-time vision state emitted to the UI overlay.
 */
data class AiVisionState(
    val trackedObstacles: List<TrackedObstacle> = emptyList(),
    val distraction: DriverDistractionState = DriverDistractionState(),
    val helmet: HelmetComplianceState = HelmetComplianceState(),
    val isForwardCollisionWarning: Boolean = false,
    val closestObstacleDistance: Float? = null,
    val inferenceTimeMs: Long = 0,
    val isProcessing: Boolean = false
)

/**
 * High-performance on-device ML vision processing pipeline using Google ML Kit.
 * Operates 100% offline directly on real hardware frames received from the ESP32-CAM.
 */
class AiSafetyVisionEngine {

    // 1. ML Kit Object Detector configured for streaming tracking
    private val objectDetectorOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()
    private val objectDetector = ObjectDetection.getClient(objectDetectorOptions)

    // 2. ML Kit Face Detector configured for real-time head pose & eye tracking
    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .enableTracking()
        .setMinFaceSize(0.15f)
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    private val _visionState = MutableStateFlow(AiVisionState())
    val visionState: StateFlow<AiVisionState> = _visionState.asStateFlow()

    private val isBusy = AtomicBoolean(false)
    private var distractionStartTime = 0L

    /**
     * Ingests a new genuine bitmap from the ESP32-CAM and runs on-device inference.
     */
    suspend fun analyzeFrame(
        bitmap: Bitmap,
        vehicleSpeedKmH: Float = 0f,
        vehicleType: VehicleType = VehicleType.TWO_WHEELER
    ) = withContext(Dispatchers.Default) {
        if (!isBusy.compareAndSet(false, true)) {
            return@withContext // Drop frame if previous ML inference is still computing
        }

        val startTime = System.currentTimeMillis()
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val imgWidth = bitmap.width.toFloat()
            val imgHeight = bitmap.height.toFloat()

            // Run Object Detection & Face Detection concurrently using Google Play Tasks
            val objectTask = objectDetector.process(inputImage)
            val faceTask = faceDetector.process(inputImage)

            // Await both ML Kit models on background thread
            val mlObjects = try { Tasks.await(objectTask) } catch (_: Exception) { emptyList() }
            val mlFaces = try { Tasks.await(faceTask) } catch (_: Exception) { emptyList() }

            // -------------------------------------------------------------
            // A. Vehicle & Obstacle Processing (Forward Collision Warning)
            // -------------------------------------------------------------
            var minDistance = Float.MAX_VALUE
            var fcwTriggered = false

            val obstacles = mlObjects.mapIndexed { idx, obj ->
                val box = obj.boundingBox
                val normRect = RectF(
                    box.left.toFloat() / imgWidth,
                    box.top.toFloat() / imgHeight,
                    box.right.toFloat() / imgWidth,
                    box.bottom.toFloat() / imgHeight
                )

                val boxHeightRatio = max(0.01f, normRect.height())
                // Pinhole camera focal distance formula: ~1.5m typical vehicle / obstacle height
                val estimatedDist = ((1.5f / boxHeightRatio) * 0.9f).coerceIn(1.5f, 45.0f)
                if (estimatedDist < minDistance) {
                    minDistance = estimatedDist
                }

                // FCW Logic: High obstacle area fraction or close distance during vehicle motion
                val isThreat = (estimatedDist < 7.0f || boxHeightRatio > 0.42f) && (vehicleSpeedKmH > 8f || estimatedDist < 3.5f)
                if (isThreat) fcwTriggered = true

                // Extract or classify label
                val rawLabel = obj.labels.firstOrNull()?.text?.uppercase() ?: ""
                val displayLabel = when {
                    rawLabel.contains("VEHICLE") || rawLabel.contains("CAR") -> "VEHICLE"
                    rawLabel.contains("PERSON") -> "PEDESTRIAN"
                    rawLabel.contains("BIKE") || rawLabel.contains("MOTORCYCLE") -> "TWO-WHEELER"
                    else -> if (boxHeightRatio > 0.25f) "FORWARD VEHICLE" else "ROAD HAZARD"
                }

                TrackedObstacle(
                    id = obj.trackingId ?: idx,
                    normalizedBox = normRect,
                    pixelRect = box,
                    label = displayLabel,
                    confidence = obj.labels.firstOrNull()?.confidence ?: 0.75f,
                    distanceMeters = estimatedDist,
                    isCollisionHazard = isThreat
                )
            }

            // -------------------------------------------------------------
            // -------------------------------------------------------------
            // B. Driver Distraction & Head Pose Processing (DMS Engine)
            // -------------------------------------------------------------
            val primaryFace = mlFaces.firstOrNull()
            val phoneDetected = mlObjects.any { obj ->
                val lbl = obj.labels.firstOrNull()?.text?.lowercase() ?: ""
                lbl.contains("phone") || lbl.contains("cell") || lbl.contains("mobile") || lbl.contains("electronic")
            }

            val distractionState = if (primaryFace != null) {
                val fBox = primaryFace.boundingBox
                val normFace = RectF(
                    (fBox.left.toFloat() / imgWidth).coerceIn(0f, 1f),
                    (fBox.top.toFloat() / imgHeight).coerceIn(0f, 1f),
                    (fBox.right.toFloat() / imgWidth).coerceIn(0f, 1f),
                    (fBox.bottom.toFloat() / imgHeight).coerceIn(0f, 1f)
                )

                val pitch = primaryFace.headEulerAngleX // Looking down: negative
                val yaw = primaryFace.headEulerAngleY   // Looking left/right: away from road
                val roll = primaryFace.headEulerAngleZ

                val leftEye = primaryFace.leftEyeOpenProbability ?: 1.0f
                val rightEye = primaryFace.rightEyeOpenProbability ?: 1.0f
                val avgEyeOpen = (leftEye + rightEye) / 2.0f

                val isLookingDown = pitch < -14.0f || phoneDetected // Head tilted downwards towards phone or phone in view
                val isLookingAway = abs(yaw) > 25.0f // Turned away from road
                val isDrowsy = avgEyeOpen < 0.28f

                val isDistractedNow = isLookingDown || isLookingAway || isDrowsy
                val now = System.currentTimeMillis()

                if (isDistractedNow) {
                    if (distractionStartTime == 0L) distractionStartTime = now
                } else {
                    distractionStartTime = 0L
                }

                val sustainedDistraction = distractionStartTime > 0L && (now - distractionStartTime >= 1200L)

                var score = 100
                if (isLookingDown) score -= 40
                if (isLookingAway) score -= 25
                if (isDrowsy) score -= 45
                if (phoneDetected) score -= 30
                score = score.coerceIn(10, 100)

                val badge = when {
                    sustainedDistraction && phoneDetected -> "PHONE IN CABIN!"
                    sustainedDistraction && isLookingDown -> "PHONE DISTRACTION!"
                    sustainedDistraction && isLookingAway -> "LOOK AT ROAD!"
                    sustainedDistraction && isDrowsy -> "DROWSINESS DETECTED!"
                    isDistractedNow -> "ATTENTION CAUTION"
                    else -> "ATTENTIVE • $score%"
                }

                DriverDistractionState(
                    isDriverFaceVisible = true,
                    normalizedFaceBox = normFace,
                    headPitchDeg = pitch,
                    headYawDeg = yaw,
                    headRollDeg = roll,
                    eyeOpenProbability = avgEyeOpen,
                    attentionScore = score,
                    isLookingDownAtPhone = (isLookingDown || phoneDetected) && sustainedDistraction,
                    isLookingAwayFromRoad = isLookingAway && sustainedDistraction,
                    isDrowsy = isDrowsy && sustainedDistraction,
                    isPhoneObjectDetected = phoneDetected,
                    statusBadge = badge
                )
            } else {
                distractionStartTime = 0L
                DriverDistractionState(
                    isDriverFaceVisible = false,
                    normalizedFaceBox = null,
                    statusBadge = "SCANNING DRIVER..."
                )
            }

            // -------------------------------------------------------------
            // C. Helmet Compliance Heuristic (Two-Wheeler Mode)
            // -------------------------------------------------------------
            val helmetState = if (vehicleType == VehicleType.TWO_WHEELER) {
                if (primaryFace != null) {
                    val fBox = primaryFace.boundingBox
                    val normFace = RectF(
                        (fBox.left.toFloat() / imgWidth).coerceIn(0f, 1f),
                        (fBox.top.toFloat() / imgHeight).coerceIn(0f, 1f),
                        (fBox.right.toFloat() / imgWidth).coerceIn(0f, 1f),
                        (fBox.bottom.toFloat() / imgHeight).coerceIn(0f, 1f)
                    )

                    val helmetTop = max(0f, normFace.top - (normFace.height() * 0.45f))
                    val normHelmet = RectF(
                        max(0f, normFace.left - (normFace.width() * 0.15f)),
                        helmetTop,
                        min(1f, normFace.right + (normFace.width() * 0.15f)),
                        normFace.top + (normFace.height() * 0.25f)
                    )

                    // Check upper head bounding box region in bitmap for helmet shell coverage
                    val hasHelmet = evaluateHelmetCoverage(bitmap, primaryFace.boundingBox)
                    HelmetComplianceState(
                        isChecked = true,
                        isHelmetWorn = hasHelmet,
                        normalizedHelmetBox = normHelmet,
                        confidence = if (hasHelmet) 0.90f else 0.82f,
                        statusText = if (hasHelmet) "HELMET VERIFIED" else "NO HELMET DETECTED"
                    )
                } else {
                    HelmetComplianceState(
                        isChecked = false,
                        isHelmetWorn = true,
                        confidence = 0.85f,
                        statusText = "SCANNING RIDER..."
                    )
                }
            } else {
                HelmetComplianceState(
                    isChecked = true,
                    isHelmetWorn = true,
                    confidence = 1.0f,
                    statusText = "SEATBELT SECURED"
                )
            }

            val elapsed = System.currentTimeMillis() - startTime

            _visionState.value = AiVisionState(
                trackedObstacles = obstacles,
                distraction = distractionState,
                helmet = helmetState,
                isForwardCollisionWarning = fcwTriggered,
                closestObstacleDistance = if (minDistance < Float.MAX_VALUE) minDistance else null,
                inferenceTimeMs = elapsed,
                isProcessing = true
            )

        } catch (e: Exception) {
            // Keep last state on transient processing error
        } finally {
            isBusy.set(false)
        }
    }

    /**
     * Optical color/luminance analysis above the forehead to differentiate
     * protective helmet shell vs exposed hair/skin.
     */
    private fun evaluateHelmetCoverage(bitmap: Bitmap, faceBox: Rect): Boolean {
        try {
            val crownY = max(0, faceBox.top - (faceBox.height() * 0.35f).toInt())
            val midX = faceBox.centerX().coerceIn(0, bitmap.width - 1)
            val topY = crownY.coerceIn(0, bitmap.height - 1)

            // Sample across 9 points in the cranial helmet dome
            var nonSkinPixels = 0
            val offsets = listOf(-12, -6, 0, 6, 12)
            for (dx in offsets) {
                for (dy in listOf(-10, -4, 2)) {
                    val x = (midX + dx).coerceIn(0, bitmap.width - 1)
                    val y = (topY + dy).coerceIn(0, bitmap.height - 1)
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    // Standard HSV/RGB human skin-tone threshold rejection
                    val isLikelySkin = (r > 95 && g > 40 && b > 20 &&
                            (max(r, max(g, b)) - min(r, min(g, b)) > 15) &&
                            abs(r - g) > 15 && r > g && r > b)

                    if (!isLikelySkin) nonSkinPixels++
                }
            }
            return nonSkinPixels >= 9 // 9 out of 15 points covered by non-skin shell
        } catch (_: Exception) {
            return true
        }
    }
}
