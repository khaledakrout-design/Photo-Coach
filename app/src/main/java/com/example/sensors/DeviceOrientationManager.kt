package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Gère la détection de l'orientation et de l'inclinaison pour l'horizon artificiel
 * et le niveau de composition à l'aide des capteurs matériels Android.
 */
class DeviceOrientationManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _orientation = MutableStateFlow(OrientationState())
    val orientation: StateFlow<OrientationState> = _orientation.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Lissage par filtre passe-bas pour éviter les vibrations visuelles
    private var smoothedRoll = 0f
    private var smoothedPitch = 0f
    private val smoothingFactor = 0.2f

    private var isListening = false

    fun startListening() {
        if (isListening || sensorManager == null) return

        val registered = when {
            rotationSensor != null -> {
                sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            }
            accelSensor != null -> {
                sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            }
            else -> false
        }

        isListening = registered
        _orientation.update { it.copy(isSensorAvailable = registered) }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // azimuth = orientationAngles[0]
                // pitch = orientationAngles[1]
                // roll = orientationAngles[2]
                val rawPitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                val rawRoll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

                updateSmoothedValues(rawRoll, rawPitch)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                // Calcul roll & pitch à partir de l'accéléromètre
                val rawRoll = Math.toDegrees(atan2(-ax.toDouble(), ay.toDouble())).toFloat()
                val rawPitch = Math.toDegrees(atan2(az.toDouble(), sqrt((ax * ax + ay * ay).toDouble()))).toFloat()

                updateSmoothedValues(rawRoll, rawPitch)
            }
        }
    }

    private fun updateSmoothedValues(rawRoll: Float, rawPitch: Float) {
        // Filtre passe-bas
        smoothedRoll = smoothedRoll + smoothingFactor * (rawRoll - smoothedRoll)
        smoothedPitch = smoothedPitch + smoothingFactor * (rawPitch - smoothedPitch)

        val isLevel = abs(smoothedRoll) <= OrientationState.LEVEL_THRESHOLD_DEGREES
        val isFlatLay = abs(smoothedPitch) > 75f // Appareil pointé vers le bas ou le haut (cadrage à plat)

        _orientation.update {
            it.copy(
                rollDegrees = smoothedRoll,
                pitchDegrees = smoothedPitch,
                isLevel = isLevel,
                isFlatLay = isFlatLay,
                isSensorAvailable = true
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Non requis pour le niveau d'horizon
    }
}
