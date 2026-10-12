package com.miireader.platform.epaper

import android.os.Build

enum class EpaperRefreshMode {
    NORMAL,
    HIGH_QUALITY,
    FAST
}

/**
 * Platform interface for e-paper display refresh control and hardware awareness.
 * The default implementation strictly avoids calling undocumented or vendor-specific APIs.
 */
interface EpaperController {
    val isEpaperDevice: Boolean
    val deviceManufacturer: String
    val deviceModel: String

    fun requestFullRefresh()
    fun setRefreshMode(mode: EpaperRefreshMode)
}

class DefaultEpaperController(
    override val deviceManufacturer: String = Build.MANUFACTURER ?: "Android",
    override val deviceModel: String = Build.MODEL ?: "Generic Device",
    private val deviceBrand: String = Build.BRAND ?: ""
) : EpaperController {

    override val isEpaperDevice: Boolean
        get() {
            val brand = deviceBrand.lowercase()
            val manufacturer = deviceManufacturer.lowercase()
            return brand.contains("onyx") ||
                    manufacturer.contains("onyx") ||
                    brand.contains("boox") ||
                    brand.contains("bigme") ||
                    brand.contains("boyue") ||
                    brand.contains("meebook") ||
                    brand.contains("moan")
        }

    override fun requestFullRefresh() {
        // Safe baseline: no vendor-specific private API calls.
        // Vendor-specific SDKs (e.g. Onyx SDK or Bigme SDK) will be hooked in
        // Phase 5 after confirming hardware documentation.
    }

    override fun setRefreshMode(mode: EpaperRefreshMode) {
        // Safe baseline: relies on standard Android surface redraws.
    }
}
