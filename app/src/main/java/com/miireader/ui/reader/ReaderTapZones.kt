package com.miireader.ui.reader

/** What a tap on the publication view should do, based on where it landed. */
internal enum class ReaderTapZone {
    PREVIOUS_PAGE,
    TOGGLE_TOOLBAR,
    NEXT_PAGE
}

/**
 * Width of each page-turn zone as a fraction of the publication view.
 *
 * The middle third toggles the toolbar so a tap on the text itself does not turn the
 * page; the outer thirds turn pages. Page-turn zones are strictly smaller than a third,
 * so the boundary pixel itself belongs to the middle.
 */
private const val PAGE_TURN_ZONE_FRACTION = 1f / 3f

/**
 * Classifies a tap at [x] pixels from the left edge of a view that is [width] pixels wide.
 *
 * Readium reports tap coordinates in physical pixels relative to the publication view,
 * so [x] and [width] share a coordinate space. A zero-width view (before the first
 * layout pass) falls back to toggling the toolbar, which is always safe.
 */
internal fun readerTapZoneAt(x: Float, width: Int): ReaderTapZone {
    if (width <= 0) return ReaderTapZone.TOGGLE_TOOLBAR

    val sideZoneWidth = width * PAGE_TURN_ZONE_FRACTION
    return when {
        x < sideZoneWidth -> ReaderTapZone.PREVIOUS_PAGE
        x > width - sideZoneWidth -> ReaderTapZone.NEXT_PAGE
        else -> ReaderTapZone.TOGGLE_TOOLBAR
    }
}
