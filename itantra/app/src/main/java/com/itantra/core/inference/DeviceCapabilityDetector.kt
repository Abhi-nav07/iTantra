package com.itantra.core.inference

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Provides a lightweight abstraction for determining device hardware capabilities,
 * preventing Out-Of-Memory (OOM) crashes by refusing to load multi-hundred-MB models
 * on severely memory-constrained devices.
 *
 * NOTE: Capability profiles are heuristic architectural policies to guide runtime
 * model loading safety. They are NOT empirical benchmark proof that a specific 2 GB
 * or 3 GB handset is supported without physical device testing.
 */
open class DeviceCapabilityDetector(private val context: Context? = null) {

    enum class CapabilityProfile {
        /** Capable of holding STT, MT, and TTS in RAM simultaneously (heuristic >= 4 GB RAM). */
        FULL_AI,
        /** Can run models sequentially; must aggressively unload inactive models (heuristic 2 GB - 4 GB RAM). */
        STANDARD_AI,
        /** Severely constrained (< 2 GB RAM or isLowRamDevice). Relies on emergency text/transport only. */
        CORE_ONLY,
        UNKNOWN
    }

    data class DeviceProfileInfo(
        val manufacturer: String,
        val model: String,
        val totalRamMb: Long,
        val availableRamMb: Long,
        val isLowRamDevice: Boolean,
        val apiLevel: Int,
        val primaryAbi: String,
        val supportedAbis: List<String>,
        val cpuCoreCount: Int,
        val profile: CapabilityProfile,
        val classificationRationale: String
    )

    val totalRamMb: Long
        get() {
            val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            return memInfo.totalMem / (1024 * 1024)
        }

    val availableRamMb: Long
        get() {
            val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            return memInfo.availMem / (1024 * 1024)
        }

    val isLowRamDevice: Boolean
        get() {
            val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            return actManager.isLowRamDevice
        }

    val apiLevel: Int
        get() = Build.VERSION.SDK_INT

    val abi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    val supportedAbis: List<String>
        get() = Build.SUPPORTED_ABIS?.toList() ?: emptyList()

    val cpuCoreCount: Int
        get() = Runtime.getRuntime().availableProcessors()

    val manufacturer: String
        get() = Build.MANUFACTURER ?: "unknown"

    val model: String
        get() = Build.MODEL ?: "unknown"

    val supportsBluetoothClassic: Boolean
        get() = context?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH) ?: false

    open fun determineProfile(): CapabilityProfile {
        val ram = totalRamMb
        val lowRam = isLowRamDevice
        return when {
            lowRam -> CapabilityProfile.CORE_ONLY
            ram >= 4000 -> CapabilityProfile.FULL_AI
            ram >= 2000 -> CapabilityProfile.STANDARD_AI
            ram > 0 -> CapabilityProfile.CORE_ONLY
            else -> CapabilityProfile.UNKNOWN
        }
    }

    fun getProfileInfo(): DeviceProfileInfo {
        val profile = determineProfile()
        val ram = totalRamMb
        val lowRam = isLowRamDevice
        val rationale = when (profile) {
            CapabilityProfile.FULL_AI -> "Total RAM ($ram MB) >= 4000 MB and lowRam=$lowRam. Supports concurrent STT, TTS, and MT."
            CapabilityProfile.STANDARD_AI -> "Total RAM ($ram MB) between 2000 MB and 4000 MB. Requires aggressive unloading between model switches."
            CapabilityProfile.CORE_ONLY -> "Constrained device (RAM: $ram MB, lowRam=$lowRam). Heavy models disabled to prevent OOM; emergency text only."
            CapabilityProfile.UNKNOWN -> "Unable to inspect system memory parameters."
        }

        return DeviceProfileInfo(
            manufacturer = manufacturer,
            model = model,
            totalRamMb = ram,
            availableRamMb = availableRamMb,
            isLowRamDevice = lowRam,
            apiLevel = apiLevel,
            primaryAbi = abi,
            supportedAbis = supportedAbis,
            cpuCoreCount = cpuCoreCount,
            profile = profile,
            classificationRationale = rationale
        )
    }
}
