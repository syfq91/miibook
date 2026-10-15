package com.miireader

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Phase 6: configuration change and activity recreation.
 *
 * `MainActivity` carries real restoration logic: it installs Readium's dummy fragment
 * factory before `super.onCreate()` so system restoration cannot crash, then removes the
 * placeholder because the reader rebuilds from the position persisted in Room. That code
 * only runs when Android recreates the activity — which is what `recreate()` drives here.
 *
 * Limits: a Robolectric recreation exercises the Activity lifecycle and the Compose
 * content, but not a real rotation of a WebView on a device. Rotation and page-position
 * survival on hardware remain unverified (see LIMITATIONS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityRecreationTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the activity can be recreated and still shows its content`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val first = controller.get()
        assertFalse(first.isFinishing)
        assertNotNull(first.window)

        // Saves instance state and rebuilds the activity through onCreate(savedState),
        // which is the path that installs and then clears the placeholder fragment.
        controller.recreate()

        val second = controller.get()
        assertFalse("recreation must not fail", second.isFinishing)
        assertFalse("recreation must not destroy the activity", second.isDestroyed)

        val content = second.window.decorView.findViewById<View>(android.R.id.content)
        assertTrue(content is ViewGroup)
        assertTrue(
            "the compose tree must be attached after recreation",
            (content as ViewGroup).childCount > 0
        )
    }

    @Test
    fun `the manifest absorbs configuration changes so the webview is not recreated`() {
        // The reader hosts a WebView; without these flags a rotation would tear the
        // navigator down mid-chapter (see the comment in MainActivity's manifest entry).
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )

        val required = ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_DENSITY

        val declared = info.configChanges
        // Every flag the reader relies on must be absorbed. Robolectric reports extra
        // bits (mcc|mnc) that are not in the merged manifest — which declares exactly
        // these six — so assert containment rather than exact equality.
        assertEquals(
            "MainActivity must declare these config changes so the WebView survives " +
                "(missing: ${required and declared.inv()})",
            required,
            declared and required
        )
    }
}
