package com.miireader.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Machine-checked e-paper constraints.
 *
 * Phase 5 of `plan.md` requires an audit of the UI for animations and other behaviours
 * that e-paper panels handle badly. A one-off human audit rots, so the rules live here
 * instead: this test reads the real sources under `app/src/main/java/com/miireader` and
 * fails the build if a forbidden construct comes back.
 *
 * Rules covered:
 *  - no animations or animated transitions (including navigation)
 *  - no ripple/press indication on any clickable
 *  - no gradients, blur, shadow, translucency, or non-zero elevation
 *  - no continuously spinning loaders or polling loops
 *  - stable keys on every lazy list item
 *  - 48dp minimum touch targets
 *  - `sp` text sizes so system font scaling applies
 *  - no Settings control that advertises an unverified refresh capability
 *
 * Anything that genuinely needs a device (panel refresh quality, ghosting, real button
 * hardware) cannot be asserted here and is documented as unverified in `plan.md`.
 */
class EpaperUiConstraintTest {

    // --------------------------------------------------------------- animations

    @Test
    fun `no animation apis appear in the ui layer`() {
        val forbidden = listOf(
            "AnimatedVisibility",
            "AnimatedContent",
            "animateContentSize",
            "animateFloatAsState",
            "animateDpAsState",
            "animateColorAsState",
            "animateIntAsState",
            "animateValueAsState",
            "updateTransition",
            "rememberInfiniteTransition",
            "Crossfade",
            "Animatable(",
            "tween(",
            "spring(",
            "keyframes(",
            "withAnimation"
        )
        val violations = uiSources().flatMap { file ->
            val text = file.readText()
            forbidden.filter { token -> text.contains(token) }
                .map { token -> "${file.relativeTo(sourceRoot)}: $token" }
        }
        assertTrue("Animation APIs are forbidden on e-paper:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun `navigation applies no animated transitions`() {
        val nav = File(sourceRoot, "ui/navigation/AppNavigation.kt").readText()
        listOf(
            "enterTransition = { EnterTransition.None }",
            "exitTransition = { ExitTransition.None }",
            "popEnterTransition = { EnterTransition.None }",
            "popExitTransition = { ExitTransition.None }"
        ).forEach { declaration ->
            assertTrue("NavHost must disable transitions: missing `$declaration`", nav.contains(declaration))
        }
        val animated = Regex("""slideIn|fadeIn|scaleIn|Expand|Shrink|AnimatedNavHost""")
            .findAll(nav).map { it.value }.toList()
        assertTrue("Animated navigation transitions are forbidden: $animated", animated.isEmpty())
    }

    // ------------------------------------------------------------------- ripple

    @Test
    fun `every clickable disables the ripple indication`() {
        val violations = mutableListOf<String>()
        uiSources().forEach { file ->
            val code = file.readText().lines()
                .filterNot { it.trimStart().startsWith("import ") }
                .joinToString("\n")

            if (code.contains("ripple(")) violations += "${file.name}: rememberRipple/ripple() usage"
            if (code.contains("LocalIndication")) violations += "${file.name}: LocalIndication usage"
            if (code.contains("combinedClickable")) violations += "${file.name}: combinedClickable usage"

            val clickables = Regex("""clickable\(""").findAll(code).count()
            val noIndication = Regex("""indication\s*=\s*null""").findAll(code).count()
            if (noIndication < clickables) {
                violations += "${file.name}: $clickables clickable call sites but only " +
                    "$noIndication pass `indication = null`"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    // ------------------------------------------------- gradients, blur, shadow

    @Test
    fun `no gradients blur shadows translucency or elevation`() {
        val rules = mapOf(
            "gradients are forbidden" to Regex("""Brush\.|gradient"""),
            "blur is forbidden" to Regex("""\bblur\("""),
            "shadows are forbidden" to Regex("""\bshadow\("""),
            "explicit alpha is forbidden (e-paper needs solid colors)" to
                Regex("""alpha\s*=|\.copy\(alpha|\.alpha\("""),
            "translucent color literals are forbidden" to Regex("""Color\(\s*0x(?!FF)"""),
            "only zero elevation is allowed" to Regex("""elevation\s*=\s*(?!0\.dp)""")
        )
        val violations = mutableListOf<String>()
        uiSources().forEach { file ->
            val text = file.readText()
            rules.forEach { (message, regex) ->
                regex.findAll(text).forEach { match ->
                    violations += "${file.relativeTo(sourceRoot)}: $message (${match.value})"
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    // ------------------------------------------- loaders, polling, list keys

    @Test
    fun `no spinning loaders or polling loops`() {
        val forbidden = listOf(
            "CircularProgressIndicator" to "spinning loader",
            "LinearProgressIndicator" to "animated progress bar",
            "while (true)" to "unbounded loop",
            "delay(" to "timed polling"
        )
        val violations = mutableListOf<String>()
        uiSources().forEach { file ->
            val text = file.readText()
            forbidden.forEach { (token, why) ->
                if (text.contains(token)) violations += "${file.name}: $why (`$token`)"
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `every lazy list item uses a stable key`() {
        val violations = mutableListOf<String>()
        uiSources().forEach { file ->
            val lines = file.readText().lines()
            lines.forEachIndexed { index, line ->
                if (line.contains("items(") && !line.trimStart().startsWith("import ")) {
                    val window = lines.drop(index).take(6).joinToString("\n")
                    if (!window.contains("key =")) {
                        violations += "${file.name}:${index + 1}: items(...) without a key"
                    }
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    // ------------------------------------------------------ targets and type

    @Test
    fun `touch target baseline is 48dp and shared components honour it`() {
        val dimensions = File(sourceRoot, "ui/theme/EpaperDimensions.kt").readText()
        assertTrue(
            "EpaperDimensions.MinTouchTarget must stay at 48dp",
            Regex("""MinTouchTarget\s*:\s*Dp\s*=\s*48\.dp""").containsMatchIn(dimensions)
        )
        listOf("components/EpaperButton.kt", "components/EpaperTopBar.kt", "components/EpaperSwitch.kt")
            .forEach { path ->
                val text = File(sourceRoot, "ui/$path").readText()
                assertTrue("$path must size itself from MinTouchTarget", text.contains("MinTouchTarget"))
            }
    }

    @Test
    fun `text sizes stay in sp so system font scaling applies`() {
        val violations = mutableListOf<String>()

        val typeLines = File(sourceRoot, "ui/theme/Type.kt").readText().lines()
        typeLines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            val declaresSize = trimmed.startsWith("fontSize =") || trimmed.startsWith("lineHeight =")
            if (declaresSize && !trimmed.endsWith(".sp,") && !trimmed.endsWith(".sp")) {
                violations += "Type.kt:${index + 1}: text size must be sp (`$trimmed`)"
            }
        }

        uiSources().forEach { file ->
            if (file.readText().contains("fontScale")) {
                violations += "${file.name}: overriding fontScale disables system font scaling"
            }
        }

        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    // ------------------------------------------------- honest settings surface

    @Test
    fun `settings never advertises refresh control it cannot deliver`() {
        val settings = File(sourceRoot, "ui/settings/SettingsScreen.kt").readText()

        val forbidden = listOf(
            "Flash Refresh",
            "Clears residual ghosting",
            "Allow UI Animations",
            "Full Screen Refresh",
            "onTriggerFullRefresh",
            "onAnimationsToggled",
            "onRefreshIntervalChanged"
        )
        val violations = forbidden.filter { settings.contains(it) }
        assertTrue(
            "Settings must not imply refresh/animation behaviour MiiReader cannot provide " +
                "(see platform/epaper/EpaperController): $violations",
            violations.isEmpty()
        )

        assertTrue(
            "Settings must point users at their device's own refresh controls",
            settings.contains("MiiReader does not change how your display refreshes")
        )

        val wholeTree = mainSources().filter { it.readText().contains("onTriggerFullRefresh") }
        assertTrue("onTriggerFullRefresh must be gone entirely: $wholeTree", wholeTree.isEmpty())
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Guards the other tests: if source discovery ever resolved to the wrong directory,
     * every scan below would silently pass against an empty file set.
     */
    @Test
    fun `source discovery finds the real ui tree`() {
        assertTrue(
            "Expected the full ui/ tree, found ${uiSources().size} files under $sourceRoot",
            uiSources().size >= 30
        )
        assertTrue(
            "Expected the full main source set, found ${mainSources().size} files",
            mainSources().size >= 50
        )
        assertTrue(
            "Key screens must be part of the scan",
            uiSources().any { it.name == "SettingsScreen.kt" } &&
                uiSources().any { it.name == "ReaderScreen.kt" } &&
                uiSources().any { it.name == "BookshelfScreen.kt" }
        )
    }

    private fun uiSources(): List<File> = File(sourceRoot, "ui")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun mainSources(): List<File> = sourceRoot
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    companion object {
        /**
         * Unit tests run with a working directory that Gradle chooses (module or root),
         * so walk upwards until the well-known source path shows up.
         */
        internal val sourceRoot: File by lazy {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            var found: File? = null
            repeat(8) {
                val current = dir ?: return@repeat
                listOf(
                    File(current, "src/main/java/com/miireader"),
                    File(current, "app/src/main/java/com/miireader")
                ).firstOrNull { File(it, "ui").isDirectory }?.let { found = it }
                dir = current.parentFile
            }
            found ?: error(
                "Could not locate app/src/main/java/com/miireader starting from " +
                    System.getProperty("user.dir")
            )
        }
    }
}
