package com.miireader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTapZoneTest {

    private val width = 900

    @Test
    fun `left third turns back`() {
        assertEquals(ReaderTapZone.PREVIOUS_PAGE, readerTapZoneAt(0f, width))
        assertEquals(ReaderTapZone.PREVIOUS_PAGE, readerTapZoneAt(299f, width))
    }

    @Test
    fun `middle third toggles the toolbar`() {
        // The boundary pixels belong to the middle so the page-turn zones stay narrow.
        assertEquals(ReaderTapZone.TOGGLE_TOOLBAR, readerTapZoneAt(300f, width))
        assertEquals(ReaderTapZone.TOGGLE_TOOLBAR, readerTapZoneAt(450f, width))
        assertEquals(ReaderTapZone.TOGGLE_TOOLBAR, readerTapZoneAt(600f, width))
    }

    @Test
    fun `right third turns forward`() {
        assertEquals(ReaderTapZone.NEXT_PAGE, readerTapZoneAt(601f, width))
        assertEquals(ReaderTapZone.NEXT_PAGE, readerTapZoneAt(900f, width))
    }

    @Test
    fun `a view that has not been measured yet only toggles the toolbar`() {
        assertEquals(ReaderTapZone.TOGGLE_TOOLBAR, readerTapZoneAt(0f, width = 0))
    }
}
