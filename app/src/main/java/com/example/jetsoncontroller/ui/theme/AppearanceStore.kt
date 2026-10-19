package com.example.jetsoncontroller.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.window.SplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.jetsoncontroller.R

enum class ThemeMode(val label: String) { SYSTEM("시스템 따름"), LIGHT("밝게"), DARK("어둡게") }

data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val lightPalette: GeoPalette = GeoPalette.STANDARD,
    val darkPalette: GeoPalette = GeoPalette.GRAPHITE
) {
    init {
        require(!lightPalette.isDark && darkPalette.isDark)
    }

    fun usesDarkPalette(systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    fun palette(systemDark: Boolean): GeoPalette = paletteForDarkTheme(usesDarkPalette(systemDark))

    fun paletteForDarkTheme(dark: Boolean): GeoPalette = if (dark) darkPalette else lightPalette

    fun withPalette(palette: GeoPalette): Appearance =
        if (palette.isDark) copy(darkPalette = palette) else copy(lightPalette = palette)

    val summary: String get() = when (mode) {
        ThemeMode.SYSTEM -> "시스템 · ${lightPalette.pickerLabel} / ${darkPalette.pickerLabel}"
        ThemeMode.LIGHT -> "밝게 · ${lightPalette.pickerLabel}"
        ThemeMode.DARK -> "어둡게 · ${darkPalette.pickerLabel}"
    }
}

internal val GeoPalette.pickerLabel: String get() = when (this) {
    GeoPalette.NAVY_LIGHT, GeoPalette.NAVY_DARK -> "네이비"
    else -> label
}

/** These names must stay stable: Android persists the selected theme across process death. */
enum class AppearanceSplashTheme(val resourceName: String) {
    GRAPHITE("Theme.GeoX.Splash.Graphite"),
    NAVY("Theme.GeoX.Splash.Navy"),
    DIM("Theme.GeoX.Splash.Dim");

    companion object {
        fun forPalette(palette: GeoPalette): AppearanceSplashTheme = when (palette) {
            GeoPalette.GRAPHITE -> GRAPHITE
            GeoPalette.NAVY_DARK -> NAVY
            GeoPalette.MIST -> DIM
            else -> error("A splash theme needs a dark palette")
        }
    }
}

fun interface ApplicationNightMode {
    fun setApplicationNightMode(mode: Int)
}

class AndroidApplicationNightMode(private val manager: UiModeManager) : ApplicationNightMode {
    override fun setApplicationNightMode(mode: Int) = manager.setApplicationNightMode(mode)
}

fun interface SplashScreenTheme {
    fun setSplashScreenTheme(themeId: Int)
}

class AndroidSplashScreenTheme(private val splashScreen: SplashScreen) : SplashScreenTheme {
    override fun setSplashScreenTheme(themeId: Int) = splashScreen.setSplashScreenTheme(themeId)
}

/** Synchronous persistence always precedes either platform call, including during migration. */
class AppearanceStore(
    private val preferences: SharedPreferences,
    private val nightMode: ApplicationNightMode,
    private val splashScreen: SplashScreenTheme,
    private val splashThemeId: (AppearanceSplashTheme) -> Int
) {
    fun read(): Appearance {
        val mode = preferences.getString(MODE_KEY, null)
            ?: preferences.getString(LEGACY_KEY, null)
        return Appearance(
            mode = ThemeMode.entries.firstOrNull { it.name == mode } ?: ThemeMode.SYSTEM,
            lightPalette = readPalette(LIGHT_KEY, dark = false),
            darkPalette = readPalette(DARK_KEY, dark = true)
        )
    }

    private fun readPalette(key: String, dark: Boolean): GeoPalette =
        GeoPalette.entries.firstOrNull { it.name == preferences.getString(key, null) && it.isDark == dark }
            ?: GeoPalette.defaultFor(dark)

    fun setMode(mode: ThemeMode): Boolean = update(read().copy(mode = mode))

    fun setPalette(palette: GeoPalette): Boolean = update(read().withPalette(palette))

    fun update(appearance: Appearance): Boolean {
        val themeId = splashThemeId(AppearanceSplashTheme.forPalette(appearance.darkPalette))
        require(themeId != 0)
        val committed = preferences.edit()
            .putString(MODE_KEY, appearance.mode.name)
            .putString(LIGHT_KEY, appearance.lightPalette.name)
            .putString(DARK_KEY, appearance.darkPalette.name)
            .putInt(SPLASH_KEY, themeId)
            .remove(LEGACY_KEY)
            .commit()
        if (!committed) return false
        splashScreen.setSplashScreenTheme(themeId)
        nightMode.setApplicationNightMode(when (appearance.mode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        })
        return true
    }

    /** Replays the platform calls too, repairing an interrupted commit/platform hand-off. */
    fun reconcile(): Boolean = update(read())

    fun observe(onChange: (Appearance) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key in setOf(MODE_KEY, LIGHT_KEY, DARK_KEY, LEGACY_KEY)) onChange(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChange(read())
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        internal const val LEGACY_KEY = "theme"
        internal const val MODE_KEY = "theme_mode"
        internal const val LIGHT_KEY = "theme_light"
        internal const val DARK_KEY = "theme_dark"
        internal const val SPLASH_KEY = "theme_splash"
    }
}

internal fun Context.appearanceActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.appearanceActivity()
    else -> null
}

/** Callable by startup before setContent; the Compose adapter also reconciles on first use. */
@SuppressLint("DiscouragedApi")
fun createAppearanceStore(activity: Activity): AppearanceStore = AppearanceStore(
    preferences = activity.getSharedPreferences("appearance", Context.MODE_PRIVATE),
    nightMode = AndroidApplicationNightMode(requireNotNull(activity.getSystemService(UiModeManager::class.java))),
    splashScreen = AndroidSplashScreenTheme(activity.splashScreen),
    splashThemeId = { theme ->
        // The resources belong to 4-A. Keep this slice buildable before its XML is merged.
        activity.resources.getIdentifier(theme.resourceName, "style", activity.packageName)
            .takeIf { it != 0 } ?: R.style.Theme_JetsonController
    }
)

@Composable
fun rememberAppearanceStore(): AppearanceStore {
    val context = LocalContext.current
    return remember(context) { createAppearanceStore(requireNotNull(context.appearanceActivity())) }
}

@Composable
fun rememberAppearance(store: AppearanceStore = rememberAppearanceStore()): Appearance {
    var appearance by remember(store) { mutableStateOf(store.read()) }
    DisposableEffect(store) {
        val unsubscribe = store.observe { appearance = it }
        onDispose { unsubscribe() }
    }
    return appearance
}

/** Startup can use this reader for its splash/loader/app hand-off without duplicating key parsing. */
@Composable
fun rememberAppearancePalette(): GeoPalette {
    val systemDark = isSystemInDarkTheme()
    if (LocalInspectionMode.current) return GeoPalette.defaultFor(systemDark)
    val store = rememberAppearanceStore()
    val appearance = rememberAppearance(store)
    LaunchedEffect(store) { store.reconcile() }
    return appearance.palette(systemDark)
}

/** Adapter for MainActivity and the existing settings dialog until its route is replaced. */
@Composable
fun rememberThemePreference(): Pair<ThemeMode, (ThemeMode) -> Unit> {
    if (LocalInspectionMode.current) return ThemeMode.SYSTEM to { _ -> }
    val store = rememberAppearanceStore()
    val appearance = rememberAppearance(store)
    return appearance.mode to { next -> store.setMode(next); Unit }
}
