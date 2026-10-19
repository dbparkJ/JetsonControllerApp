package com.example.jetsoncontroller.ui.theme

import android.app.UiModeManager
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class AppearanceStoreTest {
    @Test
    fun `first install uses system standard graphite and persists before either platform call`() {
        val fake = Fixture()
        assertEquals(Appearance(), fake.store.read())
        assertTrue(fake.events.isEmpty())
        assertTrue(fake.store.reconcile())
        assertEquals(listOf("commit", "splash:101", "night:0"), fake.events)
        fake.assertSaved(Appearance(), 101)
    }

    @Test
    fun `every legacy theme migrates once with default light and dark palettes`() {
        ThemeMode.entries.forEach { mode ->
            val fake = Fixture(mutableMapOf("theme" to mode.name))
            assertEquals(Appearance(mode = mode), fake.store.read())
            assertTrue(fake.store.reconcile())
            fake.assertSaved(Appearance(mode = mode), 101)
            assertFalse(fake.values.containsKey("theme"))
            assertEquals(Appearance(mode = mode), fake.newStore().read())
        }
    }

    @Test
    fun `new preferences win over legacy and keep both palette choices`() {
        val fake = Fixture(mutableMapOf("theme" to "DARK", "theme_mode" to "LIGHT",
            "theme_light" to "HIGH_CONTRAST", "theme_dark" to "NAVY_DARK"))
        val expected = Appearance(ThemeMode.LIGHT, GeoPalette.HIGH_CONTRAST, GeoPalette.NAVY_DARK)
        assertEquals(expected, fake.store.read())
        assertTrue(fake.store.reconcile())
        fake.assertSaved(expected, 102)
        assertFalse(fake.values.containsKey("theme"))
    }

    @Test
    fun `unknown mode and invalid or wrong-mode palettes recover to first-install defaults`() {
        listOf(
            mutableMapOf<String, Any>("theme" to "OLD_UNKNOWN"),
            mutableMapOf<String, Any>("theme_mode" to "UNKNOWN", "theme_light" to "UNKNOWN", "theme_dark" to "UNKNOWN"),
            mutableMapOf<String, Any>("theme_light" to "NAVY_DARK", "theme_dark" to "NAVY_LIGHT")
        ).forEach { values -> assertEquals(Appearance(), Fixture(values).store.read()) }
    }

    @Test
    fun `every mode and palette combination commits all keys then splash then application mode`() {
        ThemeMode.entries.forEach { mode ->
            GeoPalette.entries.filterNot { it.isDark }.forEach { light ->
                GeoPalette.entries.filter { it.isDark }.forEach { dark ->
                    val fake = Fixture()
                    val appearance = Appearance(mode, light, dark)
                    val id = fake.id(AppearanceSplashTheme.forPalette(dark))
                    assertTrue(fake.store.update(appearance))
                    val night = when (mode) {
                        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                    }
                    assertEquals(listOf("commit", "splash:$id", "night:$night"), fake.events)
                    fake.assertSaved(appearance, id)
                    assertEquals(appearance, fake.newStore().read())
                }
            }
        }
    }

    @Test
    fun `choosing a dark palette while light commits its adaptive splash without changing visible palette`() {
        val fake = Fixture()
        fake.store.update(Appearance(mode = ThemeMode.LIGHT, lightPalette = GeoPalette.HIGH_CONTRAST))
        fake.events.clear()
        assertTrue(fake.store.setPalette(GeoPalette.MIST))
        assertEquals(GeoPalette.HIGH_CONTRAST, fake.store.read().palette(systemDark = true))
        assertEquals(GeoPalette.MIST, fake.store.read().darkPalette)
        assertEquals(listOf("commit", "splash:103", "night:1"), fake.events)
    }

    @Test
    fun `mode updates retain independently selected light and dark palettes`() {
        val fake = Fixture()
        fake.store.update(Appearance(ThemeMode.SYSTEM, GeoPalette.NAVY_LIGHT, GeoPalette.MIST))
        ThemeMode.entries.forEach { mode ->
            assertTrue(fake.store.setMode(mode))
            assertEquals(Appearance(mode, GeoPalette.NAVY_LIGHT, GeoPalette.MIST), fake.newStore().read())
        }
    }

    @Test
    fun `reconcile repairs stale splash ids after restore or resource reassignment`() {
        val fake = Fixture(mutableMapOf("theme_mode" to "SYSTEM", "theme_light" to "NAVY_LIGHT",
            "theme_dark" to "NAVY_DARK", "theme_splash" to 999))
        fake.idOffset = 200
        assertTrue(fake.store.reconcile())
        fake.assertSaved(Appearance(ThemeMode.SYSTEM, GeoPalette.NAVY_LIGHT, GeoPalette.NAVY_DARK), 202)
        assertEquals(listOf("commit", "splash:202", "night:0"), fake.events)
    }

    @Test
    fun `process recreation replays platform state even if persisted splash id already matches`() {
        val fake = Fixture()
        fake.store.setPalette(GeoPalette.NAVY_DARK)
        fake.events.clear()
        val restoredStore = fake.newStore()
        assertTrue(restoredStore.reconcile())
        assertEquals(listOf("commit", "splash:102", "night:0"), fake.events)
        val restored = restoredStore.read()
        assertEquals(GeoPalette.STANDARD, restored.palette(systemDark = false))
        assertEquals(GeoPalette.NAVY_DARK, restored.palette(systemDark = true))
        assertEquals("Theme.GeoX.Splash.Navy", AppearanceSplashTheme.forPalette(restored.darkPalette).resourceName)
    }

    @Test
    fun `failed synchronous commit never calls either platform wrapper`() {
        val fake = Fixture(mutableMapOf("theme" to "SYSTEM"))
        fake.commitSucceeds = false
        assertFalse(fake.store.setMode(ThemeMode.DARK))
        assertEquals(listOf("commit"), fake.events)
    }

    @Test
    fun `interruption after commit cannot change night mode before splash and is repaired on startup`() {
        val fake = Fixture()
        fake.failSplash = true
        assertThrows(IllegalStateException::class.java) {
            fake.store.update(Appearance(ThemeMode.DARK, darkPalette = GeoPalette.MIST))
        }
        assertEquals(listOf("commit", "splash:103"), fake.events)
        fake.assertSaved(Appearance(ThemeMode.DARK, darkPalette = GeoPalette.MIST), 103)
        fake.failSplash = false
        fake.events.clear()
        assertTrue(fake.newStore().reconcile())
        assertEquals(listOf("commit", "splash:103", "night:2"), fake.events)
    }

    @Test
    fun `observing shared preferences sees complete snapshots and can be unsubscribed`() {
        val fake = Fixture()
        val snapshots = mutableListOf<Appearance>()
        val unsubscribe = fake.store.observe { snapshots += it }
        val next = Appearance(ThemeMode.LIGHT, GeoPalette.NAVY_LIGHT, GeoPalette.MIST)
        fake.store.update(next)
        assertEquals(Appearance(), snapshots.first())
        assertTrue(snapshots.drop(1).isNotEmpty())
        assertTrue(snapshots.drop(1).all { it == next })
        unsubscribe()
        val count = snapshots.size
        fake.store.setMode(ThemeMode.DARK)
        assertEquals(count, snapshots.size)
    }

    private class Fixture(val values: MutableMap<String, Any> = mutableMapOf()) {
        val events = mutableListOf<String>()
        var commitSucceeds = true
        var failSplash = false
        var idOffset = 100
        private val pending = linkedMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        private val editor: SharedPreferences.Editor = mock(SharedPreferences.Editor::class.java) { call ->
            when (call.method.name) {
                "putString", "putInt" -> {
                    pending[call.getArgument(0)] = call.getArgument<Any?>(1)
                    call.mock
                }
                "remove" -> { pending[call.getArgument(0)] = null; call.mock }
                "commit" -> {
                    events += "commit"
                    if (commitSucceeds) {
                        val keys = pending.keys.toList()
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        keys.forEach { key -> listeners.toList().forEach { it.onSharedPreferenceChanged(preferences, key) } }
                    }
                    pending.clear()
                    commitSucceeds
                }
                else -> error("Unexpected editor method: ${call.method.name}")
            }
        }
        private val preferences: SharedPreferences = mock(SharedPreferences::class.java) { call ->
            when (call.method.name) {
                "getString", "getInt" -> values[call.getArgument<String>(0)] ?: call.arguments[1]
                "edit" -> editor
                "registerOnSharedPreferenceChangeListener" -> {
                    listeners += call.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0); null
                }
                "unregisterOnSharedPreferenceChangeListener" -> {
                    listeners -= call.getArgument<SharedPreferences.OnSharedPreferenceChangeListener>(0); null
                }
                else -> error("Unexpected preferences method: ${call.method.name}")
            }
        }

        fun id(theme: AppearanceSplashTheme): Int = idOffset + theme.ordinal + 1

        fun newStore() = AppearanceStore(preferences,
            ApplicationNightMode { mode ->
                assertSnapshotComplete()
                events += "night:$mode"
            },
            SplashScreenTheme { id ->
                assertSnapshotComplete()
                assertEquals(values["theme_splash"], id)
                events += "splash:$id"
                check(!failSplash)
            }, ::id)

        val store = newStore()

        private fun assertSnapshotComplete() {
            assertTrue(values.keys.containsAll(listOf("theme_mode", "theme_light", "theme_dark", "theme_splash")))
            assertFalse(values.containsKey("theme"))
        }

        fun assertSaved(appearance: Appearance, splash: Int) {
            assertEquals(mapOf("theme_mode" to appearance.mode.name, "theme_light" to appearance.lightPalette.name,
                "theme_dark" to appearance.darkPalette.name, "theme_splash" to splash), values)
        }
    }
}
