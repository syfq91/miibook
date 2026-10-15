package com.miireader.platform.epaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 evaluation contract: device identity is detected from [android.os.Build]
 * strings, and refresh control stays a confirmed no-op until a manufacturer SDK has been
 * verified on hardware (see the interface docs and `plan.md`).
 */
class DefaultEpaperControllerTest {

    private fun controller(brand: String, manufacturer: String, model: String = "Device") =
        DefaultEpaperController(
            deviceManufacturer = manufacturer,
            deviceModel = model,
            deviceBrand = brand
        )

    @Test
    fun `known e-paper vendors are detected by brand or manufacturer`() {
        assertTrue(controller("onyx", "Onyx", "BOOX Palma").isEpaperDevice)
        assertTrue(controller("boox", "Unknown", "BOOX Go 6").isEpaperDevice)
        assertTrue(controller("bigme", "Bigme", "Bigme B751C").isEpaperDevice)
        assertTrue(controller("boyue", "Boyue", "Likebook").isEpaperDevice)
        assertTrue(controller("meebook", "Unknown", "Meebook P78").isEpaperDevice)
        // Manufacturer-only match, e.g. brand reporting something generic.
        assertTrue(controller("generic", "Onyx", "Note Air").isEpaperDevice)
    }

    @Test
    fun `ordinary phones and tablets are not detected`() {
        assertFalse(controller("samsung", "samsung", "SM-S918B").isEpaperDevice)
        assertFalse(controller("google", "Google", "Pixel 8").isEpaperDevice)
        assertFalse(controller("unknown", "Generic Device", "Emulator").isEpaperDevice)
    }

    @Test
    fun `device identity is passed through for the settings diagnostics`() {
        val controller = controller("bigme", "Bigme", "B751C")

        assertEquals("Bigme", controller.deviceManufacturer)
        assertEquals("B751C", controller.deviceModel)
    }

    @Test
    fun `refresh control is an explicit no-op`() {
        // Nothing here may throw or touch vendor APIs: until a manufacturer SDK is
        // confirmed on hardware, refresh behaviour belongs to the device itself.
        val controller = controller("onyx", "Onyx")

        controller.requestFullRefresh()
        controller.setRefreshMode(EpaperRefreshMode.NORMAL)
        controller.setRefreshMode(EpaperRefreshMode.HIGH_QUALITY)
        controller.setRefreshMode(EpaperRefreshMode.FAST)
    }
}
