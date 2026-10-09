package io.github.lonemoonspace.dayloom.app.nav

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.AppGraph
import io.github.lonemoonspace.dayloom.app.home.HomeScreen
import io.github.lonemoonspace.dayloom.app.home.HomeViewModel
import io.github.lonemoonspace.dayloom.app.settings.SettingsScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsViewModel
import io.github.lonemoonspace.dayloom.core.ui.CompactNavBar
import io.github.lonemoonspace.dayloom.core.ui.CompactTopBar
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.LocalBarInsets
import io.github.lonemoonspace.dayloom.core.ui.LocalHazeState
import io.github.lonemoonspace.dayloom.core.ui.NavTab

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val MODULE = "module/{moduleId}"
    fun module(id: String) = "module/$id"
}

/**
 * The navigation root: glass top bar, floating bottom bar (Home + module tabs + Settings) and the NavHost.
 * Tabs come from the enabled modules, so a new module with a tab appears here without changes.
 * 导航根：玻璃顶栏、悬浮底栏（首页 + 模块标签页 + 设置）与 NavHost。标签页来自已开启的模块，新模块的标签页无需改这里就会出现。
 */
@Composable
fun AppRoot(graph: AppGraph, pendingIntent: Intent?) {
    val navController = rememberNavController()
    val active by graph.host.active.collectAsStateWithLifecycle()
    val tabModules = active.orEmpty().filter { it.instance.tab != null }

    // Wait until the enabled modules are known, otherwise a link to a module tab would fall back to home.
    // 等已开启模块确定后再处理，否则指向模块标签页的链接会被误判为回到首页。
    LaunchedEffect(pendingIntent, active != null) {
        val intent = pendingIntent ?: return@LaunchedEffect
        if (active == null) return@LaunchedEffect
        val uri = intent.data ?: return@LaunchedEffect
        when (val target = DeepLinkPolicy.resolve(uri.scheme, uri.host, tabModules.mapTo(mutableSetOf()) { it.module.id })) {
            DeepLinkPolicy.Destination.Home -> navController.navigateToTab(Routes.HOME)
            DeepLinkPolicy.Destination.Settings -> navController.navigateToTab(Routes.SETTINGS)
            is DeepLinkPolicy.Destination.ModuleTab -> navController.navigateToTab(Routes.module(target.moduleId))
            null -> Unit
        }
    }

    val homeVm: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(graph.host.active, graph.appSettings, graph.coordinator) }
        },
    )
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.HOME
    val currentModuleId = backStackEntry?.arguments?.getString("moduleId")
    val hazeState = rememberHazeState()

    CompositionLocalProvider(
        LocalAppClock provides graph.clock,
        LocalHazeState provides hazeState,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                when (route) {
                    Routes.HOME -> CompactTopBar(title = stringResource(R.string.app_name)) {
                        if (homeState.cards.isNotEmpty()) {
                            IconButton(onClick = { editing = !editing }) {
                                Icon(
                                    imageVector = if (editing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = stringResource(if (editing) R.string.home_done else R.string.home_edit_order),
                                )
                            }
                        }
                        if (homeState.refreshing) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { homeVm.refresh() }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.home_refresh))
                            }
                        }
                    }
                    Routes.SETTINGS -> CompactTopBar(title = stringResource(R.string.nav_settings))
                    else -> {
                        val module = tabModules.firstOrNull { it.module.id == currentModuleId }
                        CompactTopBar(title = module?.let { stringResource(it.module.title) }.orEmpty())
                    }
                }
            },
            bottomBar = {
                val home = stringResource(R.string.nav_home)
                val settings = stringResource(R.string.nav_settings)
                val tabs = buildList {
                    add(NavTab(route == Routes.HOME, home, { navController.navigateToTab(Routes.HOME) }) { tint ->
                        Icon(Icons.Default.Home, contentDescription = null, tint = tint)
                    })
                    tabModules.forEach { m ->
                        val tab = m.instance.tab ?: return@forEach
                        val label = stringResource(tab.label)
                        add(NavTab(currentModuleId == m.module.id, label, { navController.navigateToTab(Routes.module(m.module.id)) }) { tint ->
                            Icon(painterResource(tab.icon), contentDescription = null, tint = tint)
                        })
                    }
                    add(NavTab(route == Routes.SETTINGS, settings, { navController.navigateToTab(Routes.SETTINGS) }) { tint ->
                        Icon(Icons.Default.Settings, contentDescription = null, tint = tint)
                    })
                }
                CompactNavBar(tabs)
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                CompositionLocalProvider(LocalBarInsets provides padding) {
                    NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                        composable(Routes.HOME) {
                            LifecycleEventEffect(Lifecycle.Event.ON_START) { homeVm.onForeground() }
                            HomeScreen(
                                state = homeState,
                                editing = editing && homeState.cards.isNotEmpty(),
                                onSaveOrder = homeVm::saveOrder,
                                onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                            )
                        }
                        composable(Routes.SETTINGS) {
                            val vm: SettingsViewModel = viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        SettingsViewModel(
                                            modules = graph.host.modules,
                                            settings = graph.appSettings,
                                            active = graph.host.active,
                                            zone = graph.zone,
                                            language = graph.language,
                                        )
                                    }
                                },
                            )
                            val state by vm.state.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                onLanguage = vm::setLanguage,
                                onTimeZone = vm::setTimeZone,
                                onModuleEnabled = vm::setModuleEnabled,
                            )
                        }
                        composable(
                            Routes.MODULE,
                            arguments = listOf(navArgument("moduleId") { type = NavType.StringType }),
                        ) { entry ->
                            val id = entry.arguments?.getString("moduleId")
                            // The module may have been disabled meanwhile; then there is nothing to show. / 模块可能已被关闭，此时没有内容可显示。
                            tabModules.firstOrNull { it.module.id == id }?.instance?.tab?.content?.invoke()
                        }
                    }
                }
            }
        }
    }
}

/** Switches tabs keeping each tab's own back stack and state. / 切换标签页，并保留每个标签页自己的返回栈与状态。 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
