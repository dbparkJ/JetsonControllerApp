package com.example.jetsoncontroller.ui.components.geo.shell

import org.junit.Assert.*
import org.junit.Test

class TabletLayoutPolicyTest {
    @Test fun portraitTabletKeeps720DpColumnAndLandscapeUsesMeasuredPanes() {
        val portrait = geoWindow(800f, 1333f, 1f)
        assertTrue(portrait.rail)
        assertFalse(portrait.twoPane)
        assertEquals(720f, portrait.paneWidthDp, 0f)
        assertNull(geoPaneWidths(portrait))
        val landscape = geoWindow(1333f, 800f, 1f)
        assertEquals(GeoPaneWidths(400f, 781f), geoPaneWidths(landscape))
        assertEquals(GeoPaneWidths(520f, 661f), geoPaneWidths(landscape, GeoPanePolicy.WORK_HOME))
        assertEquals(GeoPaneWidths(360f, 821f), geoPaneWidths(landscape, GeoPanePolicy.ACTIVE_RUN))
        assertEquals(GeoPaneWidths(400f, 728f), geoPaneWidths(geoWindow(1280f, 800f, 1f)))
    }

    @Test fun exactMinimumsRespectBothPaneWidthsAtNormalAndEnlargedText() {
        assertFalse(geoWindow(871f, 800f, 1f).twoPane)
        assertEquals(GeoPaneWidths(320f, 400f), geoPaneWidths(geoWindow(872f, 800f, 1f)))
        assertFalse(geoWindow(1087f, 800f, 1.3f).twoPane)
        assertEquals(GeoPaneWidths(416f, 520f), geoPaneWidths(geoWindow(1088f, 800f, 1.3f)))
        listOf(1280f, 1333f).forEach { width ->
            assertTrue(geoWindow(width, 800f, 1.3f).twoPane)
            assertFalse(geoWindow(width, 800f, 1.5f).twoPane)
            assertFalse(geoWindow(width, 800f, 2f).twoPane)
            assertFalse(geoWindow(width, 479f, 1f).twoPane)
        }
    }

    @Test fun smallFontsDoNotCreateExtraSpaceOrLowerBreakpoints() {
        listOf(800f, 871f, 872f, 1280f, 1333f).forEach { width ->
            val normal = geoWindow(width, 800f, 1f)
            val small = geoWindow(width, 800f, .8f)
            assertEquals(normal.twoPane, small.twoPane)
            assertEquals(geoPaneWidths(normal), geoPaneWidths(small))
        }
    }

    @Test fun listsStayBoundedAndEveryVisibleDetailMeetsItsMinimum() {
        listOf(.8f, 1f, 1.3f, 1.49f).forEach { scale ->
            listOf(872f, 1000f, 1088f, 1280f, 1333f, 2400f).forEach { width ->
                val window = geoWindow(width, 800f, scale)
                GeoPanePolicy.entries.forEach { policy ->
                    geoPaneWidths(window, policy)?.let { panes ->
                        val s = maxOf(1f, scale)
                        assertTrue(panes.listDp >= 320f * s - .01f)
                        assertTrue(panes.detailDp >= 400f * s - .01f)
                        if (policy != GeoPanePolicy.WORK_HOME) assertTrue(panes.listDp <= 440f * s + .01f)
                        assertEquals(minOf(1560f, window.paneWidthDp) - 72f, panes.listDp + panes.detailDp, .01f)
                    }
                }
            }
        }
        assertEquals(400f, geoPaneWidths(geoWindow(2400f, 800f, 1f))!!.listDp, 0f)
    }

    @Test fun narrowingRetainsTheDestinationSelectionAndNeverAddsASecondPhonePane() {
        assertEquals(setOf(GeoPaneSelection.LIST, GeoPaneSelection.DETAIL), geoVisiblePanes(true, GeoPaneSelection.DETAIL))
        assertEquals(setOf(GeoPaneSelection.DETAIL), geoVisiblePanes(false, GeoPaneSelection.DETAIL))
        assertEquals(setOf(GeoPaneSelection.LIST), geoVisiblePanes(false, GeoPaneSelection.LIST))
        listOf(320f, 360f, 412f, 599f).forEach { width ->
            listOf(.8f, 1f, 1.3f, 1.5f, 2f).forEach { scale ->
                val phone = geoWindow(width, 882f, scale)
                assertEquals(GeoWidthClass.COMPACT, phone.widthClass)
                assertFalse(phone.rail)
                assertFalse(phone.twoPane)
                assertEquals(width, phone.paneWidthDp, 0f)
            }
        }
        assertTrue(geoWindow(600f, 882f, 1f).rail)
    }

    @Test fun tabletCardGridsUseActualWidthAndFontScale() {
        assertTrue(GeoTabletRules.homeGrid(672f, 1f))
        assertTrue(GeoTabletRules.homeGrid(672f, .8f))
        assertFalse(GeoTabletRules.homeGrid(672f, 1.3f))
        assertFalse(GeoTabletRules.homeGrid(472f, 1f))
        assertEquals(5, GeoTabletRules.quickColumns(672f, 1f))
        assertEquals(2, GeoTabletRules.quickColumns(672f, 1.3f))
        assertEquals(2, GeoTabletRules.quickColumns(399f, 1f))
        assertEquals(1, GeoTabletRules.quickColumns(100f, 1f))
        assertEquals(3, GeoTabletRules.themeColumns(672f, 1f))
        assertEquals(2, GeoTabletRules.themeColumns(672f, 1.3f))
        assertEquals(2, GeoTabletRules.themeColumns(400f, 1f))
        assertEquals(1, GeoTabletRules.themeColumns(319f, 1f))
    }
}
