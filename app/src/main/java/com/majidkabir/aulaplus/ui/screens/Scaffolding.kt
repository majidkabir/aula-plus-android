package com.majidkabir.aulaplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.majidkabir.aulaplus.core.L
import com.majidkabir.aulaplus.ui.components.Dims
import kotlinx.coroutines.launch

/** Soft brand tint at the top of every screen, fading into the background. */
@Composable
fun ThemedBackground(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(c.background).background(
            Brush.verticalGradient(
                0f to c.primary.copy(alpha = if (dark) 0.26f else 0.15f),
                0.22f to c.secondary.copy(alpha = if (dark) 0.08f else 0.05f),
                0.45f to Color.Transparent,
            ),
        ),
    ) { content() }
}

/**
 * Standard screen: Material 3 collapsing top bar over the themed background,
 * pull-to-refresh, and a LazyColumn body with consistent spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AulaScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    large: Boolean = onBack == null,
    actions: @Composable RowScope.() -> Unit = {},
    refreshing: Boolean = false,
    onRefresh: (suspend () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    spacing: androidx.compose.ui.unit.Dp = Dims.gap,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val behavior = if (large) TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else TopAppBarDefaults.pinnedScrollBehavior()
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    )
    val nav: @Composable () -> Unit = {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = L.t("Tilbage") }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
        }
    }
    ThemedBackground {
        Scaffold(
            modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                if (large) LargeTopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = nav, actions = actions, colors = barColors, scrollBehavior = behavior)
                else TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = nav, actions = actions, colors = barColors, scrollBehavior = behavior)
            },
            bottomBar = bottomBar,
        ) { inner ->
            val list: @Composable () -> Unit = {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = Dims.pad, end = Dims.pad, top = inner.calculateTopPadding() + 4.dp,
                        bottom = inner.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    content = content,
                )
            }
            if (onRefresh != null) {
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { onRefresh() } }) { list() }
            } else list()
        }
    }
}
