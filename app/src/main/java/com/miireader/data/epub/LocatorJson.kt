package com.miireader.data.epub

import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/**
 * Serializes a [Locator] to the string form stored in Room, and back again.
 *
 * A Readium `Locator` is the only representation of a reading position we persist: it
 * carries the resource href plus the location within it, which is what the navigator
 * needs to restore a position. Page numbers are deliberately not stored, because they
 * change whenever the font size or the screen size changes.
 */

/** JSON string stored in `reading_progress.locator_json` and `bookmarks.locator_json`. */
fun Locator.encodeToJsonString(): String = toJSON().toString()

/**
 * Decodes a locator previously produced by [encodeToJsonString].
 *
 * Returns null for missing, malformed, or structurally invalid input so callers can fall
 * back to the start of the publication instead of failing the whole screen.
 */
fun decodeLocator(json: String?): Locator? {
    if (json.isNullOrBlank()) return null
    return try {
        Locator.fromJSON(JSONObject(json))
    } catch (e: Exception) {
        null
    }
}

/**
 * Whether two locators point at the same reading position.
 *
 * String equality on the serialized form is too brittle (key order and float formatting
 * can differ), so positions are compared on their stable, meaningful parts instead.
 */
fun Locator.isSamePositionAs(other: Locator): Boolean {
    if (href != other.href) return false

    val a = locations.progression
    val b = other.locations.progression
    if (a != null && b != null) return a == b

    val pa = locations.position
    val pb = other.locations.position
    if (pa != null && pb != null) return pa == pb

    // Nothing finer than the resource itself is available on both sides.
    return true
}
