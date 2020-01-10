package me.jameshunt.tilegame

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class GravitySensor : SensorEventListener {

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            val rotMatrix = FloatArray(9)
            val rotVals = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
            SensorManager.remapCoordinateSystem(
                rotMatrix,
                SensorManager.AXIS_X, SensorManager.AXIS_Y, rotMatrix
            )

            SensorManager.getOrientation(rotMatrix, rotVals)
            val azimuth = Math.toDegrees(rotVals[0].toDouble()).toFloat()
            val pitch = Math.toDegrees(rotVals[1].toDouble()).toFloat()
            val roll = Math.toDegrees(rotVals[2].toDouble()).toFloat()

            Log.d("pitch", pitch.toString())
            Log.d("roll", roll.toString())
            Log.d("azimuth", azimuth.toString())

            TileFromDirection.fromPitch(pitch, roll)?.let {
                println(it)
            }
        }
    }

    enum class TileFromDirection {
        Top,
        Bottom,
        Left,
        Right;

        companion object {
            fun fromPitch(pitch: Float, roll: Float): TileFromDirection? {
                if (pitch == 0f && roll == 0f) return null
                return when {
                    pitch < -45f -> Top
                    pitch < 45f && pitch > -45f && roll < 0f -> Right
                    pitch < 45f && pitch > -45f && roll > 0f -> Left
                    pitch > 45f -> Bottom
//                    else -> TODO("pitch: $pitch, roll: $roll")
                    else -> null
                }
            }
        }
    }
}