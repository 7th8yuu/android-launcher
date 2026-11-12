package com.example.mylauncher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/** Độ nghiêng của máy (-1..1) — điều khiển vệt sáng "liquid glass" và hiệu ứng chiều sâu. */
object Tilt : SensorEventListener {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    private var manager: SensorManager? = null
    private var started = false

    fun start(ctx: Context) {
        if (started) return
        val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        manager = m
        started = true
    }

    fun stop() {
        manager?.unregisterListener(this)
        started = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        val nx = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
        val ny = (e.values[1] / 9.81f).coerceIn(-1f, 1f)
        val tx = x + (nx - x) * 0.15f
        val ty = y + (ny - y) * 0.15f
        if (abs(tx - x) > 0.004f) x = tx
        if (abs(ty - y) > 0.004f) y = ty
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
