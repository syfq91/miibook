# ProGuard/R8 rules for MiiReader.
#
# The release build currently runs with `isMinifyEnabled = false`, so these rules are
# NOT applied. That is deliberate: Readium 3.3.0 ships no consumer keep rules (verified
# against the published AARs), and with no device on the build machine a class stripped
# in error could never be caught before it crashed a user's reader. See LIMITATIONS.md
# section 2.6.
#
# When minification is switched on, the rules below are the starting point — and the
# Phase 6 bring-up checklist in LIMITATIONS.md must then be run against the release APK.

# --- Readium Kotlin Toolkit ----------------------------------------------------
# The navigator talks to its WebView through a scripted interface; methods reached from
# JavaScript must keep their names. The default Android config covers this, but state it
# explicitly so a config change cannot silently drop it.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Readium models cross the JS bridge and are referenced by name in some paths.
-keep class org.readium.r2.** { *; }

# --- Kotlin / coroutines / serialization ---------------------------------------
# kotlinx-serialization ships its own consumer rules (META-INF/proguard); the rest of
# the Kotlin metadata rules come from the default Android config.
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# --- Room -----------------------------------------------------------------------
# Room ships consumer rules in room-runtime; nothing extra required.

# --- General --------------------------------------------------------------------
# Tests and logging reference classes the shrinker would otherwise drop from debug-only
# paths; keep line numbers so any future crash report stays readable.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
