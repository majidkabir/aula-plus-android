package com.majidkabir.aulaplus

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Demo
import com.majidkabir.aulaplus.data.Phase
import com.majidkabir.aulaplus.data.Tab
import com.majidkabir.aulaplus.ui.LocalNav
import com.majidkabir.aulaplus.ui.Nav
import com.majidkabir.aulaplus.ui.screens.CalendarScreen
import com.majidkabir.aulaplus.ui.screens.ChildPresenceScreen
import com.majidkabir.aulaplus.ui.screens.AlbumScreen
import com.majidkabir.aulaplus.ui.screens.GalleryScreen
import com.majidkabir.aulaplus.ui.screens.LoginScreen
import com.majidkabir.aulaplus.ui.screens.MessagesScreen
import com.majidkabir.aulaplus.ui.screens.MoreScreen
import com.majidkabir.aulaplus.ui.screens.NotificationsScreen
import com.majidkabir.aulaplus.ui.screens.PostDetailScreen
import com.majidkabir.aulaplus.ui.screens.SettingsScreen
import com.majidkabir.aulaplus.ui.screens.ThreadScreen
import com.majidkabir.aulaplus.ui.screens.TodayScreen
import com.majidkabir.aulaplus.ui.screens.VacationRequestsScreen
import com.majidkabir.aulaplus.ui.screens.WeekPlanScreen
import com.majidkabir.aulaplus.ui.theme.AppTheme
import com.majidkabir.aulaplus.ui.theme.AulaTheme
import com.majidkabir.aulaplus.ui.theme.ThemeStore
import com.majidkabir.aulaplus.data.AppLock
import com.majidkabir.aulaplus.ui.screens.LockScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Renders every main screen with demo data (light, dark, themes, large text) for UI review. */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar", maxPercentDifference = 100.0)

    @Before
    fun setup() {
        Demo.isOn = true
        L.override = "en"
        AppState.phase = Phase.LOGGED_IN
        ThemeStore.preview(AppTheme.APRICOT)
    }

    @Composable
    private fun Host(dark: Boolean, content: @Composable () -> Unit) {
        val lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle
        val back = object : OnBackPressedDispatcherOwner {
            override val lifecycle: Lifecycle = lifecycle
            override val onBackPressedDispatcher = OnBackPressedDispatcher()
        }
        val registry = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
            }
        }
        CompositionLocalProvider(
            LocalNav provides Nav(rememberNavController()),
            LocalOnBackPressedDispatcherOwner provides back,
            LocalActivityResultRegistryOwner provides registry,
        ) { AulaTheme(dark) { content() } }
    }

    private fun shot(name: String, dark: Boolean = false, theme: AppTheme = AppTheme.APRICOT, fontScale: Float = 1f, tab: Tab = Tab.TODAY,
                     content: @Composable () -> Unit) {
        ThemeStore.preview(theme)
        AppState.tab = tab
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT, fontScale = fontScale))
        paparazzi.snapshot(name) { Host(dark) { content() } }
    }

    private val screens: List<Triple<String, Tab, @Composable () -> Unit>> = listOf(
        Triple("today", Tab.TODAY, { TodayScreen() }),
        Triple("messages", Tab.MESSAGES, { MessagesScreen() }),
        Triple("thread", Tab.MESSAGES, { ThreadScreen("1") }),
        Triple("calendar", Tab.CALENDAR, { CalendarScreen() }),
        Triple("weekplan", Tab.WEEK_PLAN, { WeekPlanScreen() }),
        Triple("more", Tab.MORE, { MoreScreen() }),
        Triple("child", Tab.TODAY, { ChildPresenceScreen(102) }),
        Triple("post", Tab.MORE, { PostDetailScreen(1) }),
        Triple("settings", Tab.MORE, { SettingsScreen() }),
        Triple("gallery", Tab.MORE, { GalleryScreen() }),
        Triple("album", Tab.MORE, { AlbumScreen(3) }),
        Triple("notifications", Tab.MORE, { NotificationsScreen() }),
        Triple("vacations", Tab.MORE, { VacationRequestsScreen() }),
    )

    @Test fun light() = screens.forEach { (n, t, c) -> shot("light-$n", tab = t, content = c) }
    @Test fun dark() = screens.forEach { (n, t, c) -> shot("dark-$n", dark = true, tab = t, content = c) }
    @Test fun login() { shot("light-login") { LoginScreen() }; shot("dark-login", dark = true) { LoginScreen() } }
    @Test fun themes() {
        shot("theme-sunset-today", theme = AppTheme.SUNSET) { TodayScreen() }
        shot("theme-forest-today", dark = true, theme = AppTheme.FOREST) { TodayScreen() }
        shot("theme-lavender-settings", theme = AppTheme.LAVENDER, tab = Tab.MORE) { SettingsScreen() }
        shot("theme-graphite-calendar", dark = true, theme = AppTheme.GRAPHITE, tab = Tab.CALENDAR) { CalendarScreen() }
    }
    @Test fun lock() {
        shot("light-lock") { LockScreen() }
        shot("dark-lock", dark = true) { LockScreen() }
        shot("theme-sunset-lock", theme = AppTheme.SUNSET) { LockScreen() }
        AppLock.previewEnabled(true)
        shot("light-settings-lock", tab = Tab.MORE) { SettingsScreen() }
        AppLock.previewEnabled(false)
    }
    @Test fun largeText() {
        shot("xl-today", fontScale = 1.8f) { TodayScreen() }
        shot("xl-calendar", fontScale = 1.8f, tab = Tab.CALENDAR) { CalendarScreen() }
    }
}
