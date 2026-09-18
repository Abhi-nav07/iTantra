package com.itantra.core.inference

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Provides a lightweight abstraction for determining device capabilities,
 * preventing OOM crashes by refusing to load multi-GB models on low-RAM devices.
 */
class DeviceCapabilityDetector(private val context: Context) {

    enum class CapabilityProfile {
        /** Capable of holding STT, MT, and TTS in RAM simultaneously. */
        FULL_AI,
        /** Can run AI models but should aggressively unload inactive sessions. */
        STANDARD_AI,
        /** Severely constrained. Should rely on text/emergency only or smallest local models. */
        CORE_ONLY,
        UNKNOWN
    }

    val totalRamMb: Long
        get() {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            return memInfo.totalMem / (1024 * 1024)
        }

    val availableRamMb: Long
        get() {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            return memInfo.availMem / (1024 * 1024)
        }

    val isLowRamDevice: Boolean
        get() {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return actManager.isLowRamDevice
        }

    val apiLevel: Int
        get() = Build.VERSION.SDK_INT

    val abi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    val supportsBluetoothClassic: Boolean
        get() = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH)

    fun determineProfile(): CapabilityProfile {
        val ram = totalRamMb
        return when {
            ram >= 4000 -> CapabilityProfile.FULL_AI
            ram >= 2000 -> CapabilityProfile.STANDARD_AI
            ram > 0 -> CapabilityProfile.CORE_ONLY
            else -> CapabilityProfile.UNKNOWN
        }
    }
}
