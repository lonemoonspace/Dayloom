package io.github.lonemoonspace.dayloom.app

import android.content.Context
import android.util.Log
import androidx.datastore.dataStoreFile
import io.github.lonemoonspace.dayloom.BuildConfig
import io.github.lonemoonspace.dayloom.core.i18n.AppLanguage
import io.github.lonemoonspace.dayloom.core.i18n.SystemAppLanguage
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.network.ConnectivityMonitor
import io.github.lonemoonspace.dayloom.core.network.SharedHttpClient
import io.github.lonemoonspace.dayloom.core.notify.NotificationEngine
import io.github.lonemoonspace.dayloom.core.notify.NotificationSender
import io.github.lonemoonspace.dayloom.core.notify.PrefsNotificationStateStore
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.secret.DataStoreSecretStore
import io.github.lonemoonspace.dayloom.core.secret.SecretBox
import io.github.lonemoonspace.dayloom.core.secret.SecretStore
import io.github.lonemoonspace.dayloom.core.storage.AppSettings
import io.github.lonemoonspace.dayloom.core.storage.AppStores
import io.github.lonemoonspace.dayloom.core.storage.DataStoreSnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.ModuleSettingsStore
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.storage.moduleSettingsDataStore
import io.github.lonemoonspace.dayloom.core.storage.notifyStateDataStore
import io.github.lonemoonspace.dayloom.core.storage.secretsDataStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.core.time.SystemAppClock
import io.github.lonemoonspace.dayloom.core.time.ZonePolicy
import io.github.lonemoonspace.dayloom.core.time.currentDeviceZone
import io.github.lonemoonspace.dayloom.core.time.deviceZoneFlow
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.KSerializer

/**
 * The hand-written dependency graph, created once in [io.github.lonemoonspace.dayloom.DayloomApp].
 * 手写的依赖图，在 [io.github.lonemoonspace.dayloom.DayloomApp] 里创建一次。
 */
class AppGraph(context: Context, modules: List<FeatureModule> = ModuleRegistry.modules) {
    private val appContext = context.applicationContext

    /** Process-wide scope: shared fetches and watchers live here, not in a screen or worker. / 进程级作用域：共享的抓取与监听都在这里，不随某个页面或 Worker 结束。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val appSettings: ValueStore<AppSettings> = AppStores.jsonValueStore(
        serializer = AppSettings.serializer(),
        default = AppSettings(),
        scope = ioScope,
        onDecodeError = { Log.w(TAG, "app_settings unreadable, using defaults", it) },
    ) { appContext.dataStoreFile(AppStores.APP_SETTINGS_FILE) }

    val zone: StateFlow<ZoneId> = combine(
        appSettings.flow.map { it.timeZoneOverride }.distinctUntilChanged(),
        deviceZoneFlow(appContext),
    ) { override, device -> ZonePolicy.resolve(override, device) }
        .stateIn(appScope, SharingStarted.Eagerly, currentDeviceZone())

    val clock: AppClock = SystemAppClock(zoneProvider = { zone.value })

    val language: AppLanguage = SystemAppLanguage(appContext)

    val http = SharedHttpClient.build(BuildConfig.VERSION_NAME, debugLogging = BuildConfig.DEBUG)

    val connectivity = ConnectivityMonitor(appContext)

    val secrets: SecretStore = DataStoreSecretStore(appContext.secretsDataStore, SecretBox)

    private val moduleSettings = ModuleSettingsStore(appContext.moduleSettingsDataStore) { moduleId, e ->
        Log.w(TAG, "settings of module $moduleId unreadable, using defaults", e)
    }

    val coordinator = RefreshCoordinator(
        connectivity = connectivity,
        clock = clock,
        appScope = appScope,
        watchInputs = true,
        onWatchError = { id, e -> Log.w(TAG, "watching inputs of ${id ?: "sources"} failed, retrying", e) },
    )

    val notificationEngine = NotificationEngine(
        stateStore = PrefsNotificationStateStore(appContext.notifyStateDataStore),
        notifier = NotificationSender(appContext),
        onError = { rule, e -> Log.w(TAG, "notification rule ${rule.stateKey} failed", e) },
    )

    // A file may have only one DataStore instance per process. / 同一文件在进程内只能有一个 DataStore 实例。
    private val snapshotFiles = mutableSetOf<String>()

    private val snapshotFactory = object : DefaultModuleContext.SnapshotFactory {
        override fun <T> create(id: SourceId, serializer: KSerializer<T>): SnapshotStore<T> {
            synchronized(snapshotFiles) {
                check(snapshotFiles.add(id.snapshotFileName)) { "snapshot store for $id created twice" }
            }
            return DataStoreSnapshotStore(serializer, ioScope) { appContext.dataStoreFile(id.snapshotFileName) }
        }
    }

    val host = ModuleHost(
        modules = modules,
        settings = appSettings,
        contextFor = { module ->
            DefaultModuleContext(
                module = module,
                http = http,
                clock = clock,
                connectivity = connectivity,
                coordinator = coordinator,
                appScope = appScope,
                moduleSettings = moduleSettings,
                secrets = secrets,
                snapshotFactory = snapshotFactory,
            )
        },
        coordinator = coordinator,
        scope = appScope,
    )

    private companion object {
        const val TAG = "AppGraph"
    }
}
