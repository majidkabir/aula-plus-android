package com.majidkabir.aulaplus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.data.AppState
import com.majidkabir.aulaplus.data.Phase
import com.majidkabir.aulaplus.data.Repo
import com.majidkabir.aulaplus.data.Tab
import com.majidkabir.aulaplus.ui.screens.*

/** Navigation helper available to every screen. */
class Nav(val controller: NavHostController) {
    fun open(route: String) = controller.navigate(route)
    fun back() { controller.popBackStack() }
    fun tab(t: Tab) {
        AppState.tab = t
        controller.navigate(t.route) {
            popUpTo(controller.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/** Objects handed to detail screens without serialising them into the route. */
object NavCache {
    val items = mutableMapOf<String, Any>()
    fun put(key: String, value: Any): String { items[key] = value; return key }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav") }

val Tab.route: String
    get() = when (this) {
        Tab.TODAY -> "today"; Tab.MESSAGES -> "messages"; Tab.CALENDAR -> "calendar"
        Tab.WEEK_PLAN -> "weekplan"; Tab.MORE -> "more"
    }

@Composable
fun AulaRoot() {
    LaunchedEffect(Unit) { AppState.start() }
    when (AppState.phase) {
        Phase.LAUNCHING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        Phase.LOGGED_OUT -> LoginScreen()
        Phase.LOGGED_IN -> {
            MainNav()
            if (AppState.isLocked) LockScreen()
        }
    }
}

@Composable
fun MainNav(startRoute: String = AppState.tab.route) {
    val controller = rememberNavController()
    val nav = Nav(controller)
    LaunchedEffect(AppState.pendingRoute) {
        AppState.pendingRoute?.let { route ->
            AppState.pendingRoute = null
            // Only our own notification routes; anything else (or a stale id) must not crash the app.
            if (Regex("""(thread/\d+|post/\d+|album/\d+|vacations|notifications)""").matches(route)) runCatching { nav.open(route) }
        }
    }
    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(controller, startDestination = startRoute) {
            composable("today") { TodayScreen() }
            composable("messages") { MessagesScreen() }
            composable("calendar") { CalendarScreen() }
            composable("weekplan") { WeekPlanScreen() }
            composable("more") { MoreScreen() }
            composable("thread/{id}") { ThreadScreen(it.arguments?.getString("id") ?: "") }
            composable("compose") { ComposeScreen() }
            composable("child/{id}") { ChildPresenceScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0) }
            composable("event/{key}") { EventDetailScreen(it.arguments?.getString("key") ?: "") }
            composable("post/{id}") { PostDetailScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0) }
            composable("posts") { PostsScreen() }
            composable("gallery") { GalleryScreen() }
            composable("album/{id}") { AlbumScreen(it.arguments?.getString("id")?.toIntOrNull() ?: 0) }
            composable("notifications") { NotificationsScreen() }
            composable("dates") { ImportantDatesScreen() }
            composable("contacts") { ContactsScreen() }
            composable("documents") { DocumentsScreen() }
            composable("doc/{key}") { DocumentDetailScreen(it.arguments?.getString("key") ?: "") }
            composable("vacations") { VacationRequestsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}

/** Bottom navigation shared by the five top-level screens. */
@Composable
fun AulaBottomBar() {
    val nav = LocalNav.current
    val current = nav.controller.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TabItem(nav, current, Tab.TODAY, L.t("I dag"), Icons.Filled.WbSunny, Icons.Outlined.WbSunny)
        TabItem(nav, current, Tab.MESSAGES, L.t("Beskeder"), Icons.AutoMirrored.Filled.Chat, Icons.AutoMirrored.Outlined.Chat, Repo.unreadCount)
        TabItem(nav, current, Tab.CALENDAR, L.t("Kalender"), Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth)
        TabItem(nav, current, Tab.WEEK_PLAN, L.t("Ugeplan"), Icons.Filled.ListAlt, Icons.Outlined.ListAlt)
        TabItem(nav, current, Tab.MORE, L.t("Mere"), Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    nav: Nav, current: String?, t: Tab, label: String, on: ImageVector, off: ImageVector, badge: Int = 0,
) {
    val selected = current == t.route || (current == null && AppState.tab == t)
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.4f
    NavigationBarItem(
        alwaysShowLabel = true,
        selected = selected,
        onClick = { nav.tab(t) },
        icon = {
            BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
                // With very large text there's no room for labels; the icon still carries the name for TalkBack.
                Icon(if (selected) on else off, if (largeText) label else null)
            }
        },
        label = if (largeText) null else ({ Text(label, maxLines = 1) }),
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    )
}
