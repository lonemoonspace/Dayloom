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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.ActiveModule
import io.github.lonemoonspace.dayloom.app.AppGraph
import io.github.lonemoonspace.dayloom.app.home.HomeScreen
import io.github.lonemoonspace.dayloom.app.home.HomeViewModel
import io.github.lonemoonspace.dayloom.app.onboarding.OnboardingScreen
import io.github.lonemoonspace.dayloom.app.settings.PlacesCard
import io.github.lonemoonspace.dayloom.app.settings.ModuleSettingsScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsPage
import io.github.lonemoonspace.dayloom.app.settings.SettingsPageScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsScreen
import io.github.lonemoonspace.dayloom.app.settings.SettingsViewModel
import io.github.lonemoonspace.dayloom.app.settings.SharedDataViewModel
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.ui.CompactNavBar
import io.github.lonemoonspace.dayloom.core.ui.CompactTopBar
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.LocalBarInsets
import io.github.lonemoonspace.dayloom.core.ui.LocalHazeState
import io.github.lonemoonspace.dayloom.core.ui.NavTab
import io.github.lonemoonspace.dayloom.core.ui.PullRefresh
import io.github.lonemoonspace.dayloom.core.ui.theme.LocalAppSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.overWeave
import io.github.lonemoonspace.dayloom.core.ui.theme.weaveBackground
import io.github.lonemoonspace.dayloom.core.ui.theme.weaveColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Each module tab has a route of its own rather than one `module/{moduleId}` pattern: tab switches save and restore state
 * per destination, and with a shared pattern opening News restored whichever module tab had been open before (or nothing,
 * if that module had been turned off since).
 * 每个模块标签页都有自己的路由，而不是共用 `module/{moduleId}` 模式：切换标签页时按目的地保存与恢复状态，共用模式下
 * 打开「新闻」会恢复之前打开过的其他模块页（若那个模块已被关闭，则什么都不显示）。
 */
internal object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    private const val MODULE_PREFIX = "module/"
    private const val SETTINGS_PAGE_PREFIX = "settings/"
    private const val SETTINGS_MODULE_PREFIX = "settings/module/"
    fun module(id: String) = MODULE_PREFIX + id
    fun moduleIdOf(route: String?): String? = route?.takeIf { it.startsWith(MODULE_PREFIX) }?.removePrefix(MODULE_PREFIX)

    /**
     * Settings pages sit on the settings tab's own back stack, so leaving the tab and coming back returns to the same page.
     * 设置的子页面在设置标签页自己的返回栈上，离开标签页再回来仍停在同一页。
     */
    fun settingsPage(page: SettingsPage) = SETTINGS_PAGE_PREFIX + page.segment
    fun settingsModule(id: String) = SETTINGS_MODULE_PREFIX + id
    fun settingsPageOf(route: String?): SettingsPage? =
        route?.takeIf { it.startsWith(SETTINGS_PAGE_PREFIX) && !it.startsWith(SETTINGS_MODULE_PREFIX) }
            ?.removePrefix(SETTINGS_PAGE_PREFIX)
            ?.let(SettingsPage::of)
    fun settingsModuleOf(route: String?): String? = route?.takeIf { it.startsWith(SETTINGS_MODULE_PREFIX) }?.removePrefix(SETTINGS_MODULE_PREFIX)
    fun isSettings(route: String?): Boolean = route == SETTINGS || route?.startsWith(SETTINGS_PAGE_PREFIX) == true
}

/**
 * The home slogan's handwriting fonts: Long Cang (cut down to the Chinese slogan's glyphs, see
 * `scripts/fonts/subset_slogan.py`) and Parisienne. They are two resources chosen by the slogan text, not one font with a
 * `font-zh` variant: Compose loads fonts through the application context and caches them by resource id for the whole
 * process, so after a language switch the English slogan was still given the Chinese subset and fell back to the system font.
 * 首页标语的手写字体：龙藏体（只保留中文标语的字形，见 `scripts/fonts/subset_slogan.py`）和 Parisienne。拆成两个资源、按标语
 * 文字来选，而不是一个字体加 `font-zh` 变体：Compose 用 Application 的 Context 加载字体，并按资源 id 在整个进程里缓存，切换
 * 语言后英文标语仍拿到中文子集，只能退回系统字体。
 */
private val ChineseSloganFont = FontFamily(Font(R.font.slogan_long_cang))
private val LatinSloganFont = FontFamily(Font(R.font.slogan_parisienne))

private fun sloganFont(slogan: String): FontFamily =
    if (slogan.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) ChineseSloganFont else LatinSloganFont

/**
 * Shows the onboarding until it has been finished once, then the app. Nothing is drawn before the settings are read, so a
 * returning user never sees the onboarding flash by ([StartupWait] speaks up only if reading keeps failing).
 * 首次启动引导完成之前显示引导，之后显示 App。设置读出来之前什么都不画，老用户不会看到引导一闪而过（只有一直读不出来时
 * [StartupWait] 才出声）。
 */
@Composable
fun AppRoot(graph: AppGraph, pendingIntent: Intent?) {
    val settings by graph.appSettings.flow.collectAsStateWithLifecycle(initialValue = null)
    when (settings?.onboardingDone) {
        null -> StartupWait()
        false -> {
            val vm = settingsViewModel(graph)
            val sharedVm = sharedDataViewModel(graph)
            val state by vm.state.collectAsStateWithLifecycle()
            val places by sharedVm.placeList.collectAsStateWithLifecycle()
            val editor by sharedVm.editor.collectAsStateWithLifecycle()
            OnboardingScreen(
                state = state,
                onLanguage = vm::setLanguage,
                onModuleEnabled = vm::setModuleEnabled,
                places = places,
                editor = editor,
                sharedVm = sharedVm,
                onFinish = vm::finishOnboarding,
            )
        }
        true -> MainShell(graph, pendingIntent, weave = settings?.weaveBackground != false)
    }
}

@Composable
private fun settingsViewModel(graph: AppGraph): SettingsViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                modules = graph.host.modules,
                settings = graph.appSettings,
                sectionsOf = { graph.host.instanceOf(it).settingsSections },
                statusOf = { graph.host.instanceOf(it).settingsStatus },
                configOf = { graph.host.instanceOf(it).configured },
                zone = graph.zone,
                language = graph.language,
            )
        }
    },
)

@Composable
private fun sharedDataViewModel(graph: AppGraph): SharedDataViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            SharedDataViewModel(
                places = graph.places,
                search = graph.placeSearch,
                secrets = graph.secrets,
                appScope = graph.appScope,
                ioContext = Dispatchers.IO,
            )
        }
    },
)

/**
 * The navigation root: glass top bar, floating bottom bar (Home + module tabs + Settings) and the NavHost.
 * Tabs come from the enabled modules, so a new module with a tab appears here without changes.
 * 导航根：玻璃顶栏、悬浮底栏（首页 + 模块标签页 + 设置）与 NavHost。标签页来自已开启的模块，新模块的标签页无需改这里就会出现。
 */
@Composable
private fun MainShell(graph: AppGraph, pendingIntent: Intent?, weave: Boolean) {
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
            initializer { HomeViewModel(graph.host.active, graph.appSettings, graph.coordinator, graph.clock) }
        },
    )
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: Routes.HOME
    val currentModuleId = Routes.moduleIdOf(route)
    // Routes for every registered module, enabled or not, so the graph never loses a destination that is on the back stack.
    // 为每个已登记的模块建路由（不论是否开启），图里就不会缺少返回栈上的目的地。
    val moduleIds = remember(graph) { graph.host.modules.map { it.id } }
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
                    Routes.HOME -> CompactTopBar(
                        title = stringResource(R.string.app_name),
                        subtitle = stringResource(R.string.app_slogan),
                        subtitleStyle = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = sloganFont(stringResource(R.string.app_slogan)),
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp,
                        ),
                    ) {
                        if (homeState.userOrder.isNotEmpty()) {
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
                        val settingsPage = Routes.settingsPageOf(route)
                        val settingsModule = Routes.settingsModuleOf(route)?.let { id -> graph.host.modules.firstOrNull { it.id == id } }
                        when {
                            settingsPage != null -> CompactTopBar(title = stringResource(settingsPage.title), onBack = { navController.popBackStack() })
                            settingsModule != null -> CompactTopBar(title = stringResource(settingsModule.title), onBack = { navController.popBackStack() })
                            else -> {
                                val module = tabModules.firstOrNull { it.module.id == currentModuleId }
                                CompactTopBar(title = module?.let { stringResource(it.module.title) }.orEmpty()) {
                                    // Straight to this module's settings; Back returns to the tab. / 直接打开这个模块的设置；返回键回到标签页。
                                    if (module != null) {
                                        val label = stringResource(R.string.settings_module_settings, stringResource(module.module.title))
                                        IconButton(onClick = { navController.navigate(Routes.settingsModule(module.module.id)) }) {
                                            Icon(Icons.Default.Settings, contentDescription = label)
                                        }
                                    }
                                }
                            }
                        }
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
                            tab.iconContent?.invoke(tint) ?: Icon(painterResource(tab.icon), contentDescription = null, tint = tint)
                        })
                    }
                    add(NavTab(Routes.isSettings(route), settings, { navController.openSettingsTab(route) }) { tint ->
                        Icon(Icons.Default.Settings, contentDescription = null, tint = tint)
                    })
                }
                CompactNavBar(tabs)
            },
        ) { padding ->
            val scheme = MaterialTheme.colorScheme
            // Drawn inside the haze source, so the bars blur the weave too. / 画在毛玻璃的取样范围内，顶栏底栏下也是模糊的织纹。
            val weaveLayer = if (weave) {
                Modifier.weaveBackground(weaveColor(scheme, dark = scheme.background.luminance() < 0.5f))
            } else {
                Modifier
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .background(scheme.background)
                    .then(weaveLayer),
            ) {
                // Cards on these pages let the weave show through; with the weave off, or outside this shell, cards stay solid.
                // 这些页面上的卡片透出织纹；关掉织纹或不在这个外壳里时，卡片仍是实心的。
                val surfaces = MaterialTheme.appSurfaces
                CompositionLocalProvider(
                    LocalBarInsets provides padding,
                    LocalAppSurfaces provides remember(weave, surfaces) { if (weave) surfaces.overWeave() else surfaces },
                ) {
                    NavHost(navController = navController, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                        composable(Routes.HOME) {
                            LifecycleStartEffect(homeVm) {
                                homeVm.startPolling()
                                onStopOrDispose { homeVm.stopPolling() }
                            }
                            val reordering = editing && homeState.userOrder.isNotEmpty()
                            PullRefresh(refreshing = homeState.refreshing, onRefresh = { homeVm.refresh() }, enabled = !reordering) {
                                HomeScreen(
                                    state = homeState,
                                    editing = reordering,
                                    onSaveOrder = homeVm::saveOrder,
                                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                                )
                            }
                        }
                        composable(Routes.SETTINGS) {
                            val vm = settingsViewModel(graph)
                            val sharedVm = sharedDataViewModel(graph)
                            val state by vm.state.collectAsStateWithLifecycle()
                            val places by sharedVm.placeList.collectAsStateWithLifecycle()
                            SettingsScreen(
                                state = state,
                                places = places,
                                onOpenPage = { navController.navigate(Routes.settingsPage(it)) { launchSingleTop = true } },
                                onOpenModule = { navController.navigate(Routes.settingsModule(it)) { launchSingleTop = true } },
                                onModuleEnabled = vm::setModuleEnabled,
                            )
                        }
                        SettingsPage.entries.forEach { page ->
                            composable(Routes.settingsPage(page)) {
                                val vm = settingsViewModel(graph)
                                val sharedVm = sharedDataViewModel(graph)
                                val state by vm.state.collectAsStateWithLifecycle()
                                SettingsPageScreen(
                                    page = page,
                                    state = state,
                                    onLanguage = vm::setLanguage,
                                    onTimeZone = vm::setTimeZone,
                                    onMorningBrief = vm::setMorningBrief,
                                    onMorningBriefTime = vm::setMorningBriefTime,
                                    onThemePreset = vm::setThemePreset,
                                    onWeaveBackground = vm::setWeaveBackground,
                                ) {
                                    val places by sharedVm.placeList.collectAsStateWithLifecycle()
                                    val editor by sharedVm.editor.collectAsStateWithLifecycle()
                                    PlacesCard(places, editor, sharedVm)
                                }
                            }
                        }
                        moduleIds.forEach { id ->
                            composable(Routes.settingsModule(id)) {
                                val vm = settingsViewModel(graph)
                                val state by vm.state.collectAsStateWithLifecycle()
                                // Before the first read the list is empty; the page fills in a moment later. / 第一次读到之前列表为空，稍后页面就会填上。
                                val toggle = state.modules.firstOrNull { it.module.id == id } ?: return@composable
                                ModuleSettingsScreen(toggle, vm::setModuleEnabled)
                            }
                        }
                        moduleIds.forEach { id ->
                            composable(Routes.module(id)) {
                                // The module may have been disabled meanwhile; then there is nothing to show. / 模块可能已被关闭，此时没有内容可显示。
                                val active = tabModules.firstOrNull { it.module.id == id } ?: return@composable
                                val content = active.instance.tab?.content ?: return@composable
                                ModuleRefresh(graph.coordinator, active) { content() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pull-to-refresh for a module's tab: refreshes that module's sources, showing the indicator while any of them is busy.
 * 模块标签页的下拉刷新：刷新这个模块的来源，其中任何一个在刷新时显示指示器。
 */
@Composable
private fun ModuleRefresh(coordinator: RefreshCoordinator, active: ActiveModule, content: @Composable () -> Unit) {
    val ids = remember(active) { active.instance.sources.mapTo(mutableSetOf()) { it.id } }
    val status by coordinator.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    PullRefresh(
        refreshing = ids.any { status[it]?.refreshing == true },
        onRefresh = {
            scope.launch {
                try {
                    coordinator.refresh(ids, Trigger.USER)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Per-source errors are in coordinator.status. / 各来源的错误在 coordinator.status 里。
                }
            }
        },
        content = content,
    )
}

/**
 * Tapping Settings while on one of its pages goes back to the list, like other apps; from elsewhere it switches tabs.
 * 在设置的某个子页面上再点「设置」回到设置列表，与其他 App 一致；在别处点则切换标签页。
 */
private fun NavHostController.openSettingsTab(route: String) {
    if (route != Routes.SETTINGS && Routes.isSettings(route) && popBackStack(Routes.SETTINGS, inclusive = false)) return
    navigateToTab(Routes.SETTINGS)
}

/** Switches tabs keeping each tab's own back stack and state. / 切换标签页，并保留每个标签页自己的返回栈与状态。 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
