package io.github.lonemoonspace.dayloom.app

import io.github.lonemoonspace.dayloom.core.location.Places
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleSecret
import io.github.lonemoonspace.dayloom.core.network.NetworkStatus
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.secret.SecretStore
import io.github.lonemoonspace.dayloom.core.secret.SharedSecrets
import io.github.lonemoonspace.dayloom.core.storage.ModuleSettingsStore
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import androidx.room.RoomDatabase
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import okhttp3.OkHttpClient

/**
 * The [ModuleContext] given to one module; all names are prefixed with the module id here.
 * 交给某个模块的 [ModuleContext]；所有名字都在这里加上模块 id 前缀。
 */
class DefaultModuleContext(
    private val module: FeatureModule,
    override val http: OkHttpClient,
    override val clock: AppClock,
    override val connectivity: NetworkStatus,
    override val coordinator: RefreshCoordinator,
    override val appScope: CoroutineScope,
    override val places: Places,
    override val routine: Flow<Routine>,
    private val moduleSettings: ModuleSettingsStore,
    private val secrets: SecretStore,
    private val snapshotFactory: SnapshotFactory,
    private val databaseFactory: DatabaseFactory,
) : ModuleContext {

    /** Builds the one Room database for the module that owns it. / 为持有它的模块构建唯一的 Room 数据库。 */
    interface DatabaseFactory {
        fun <T : RoomDatabase> create(moduleId: String, type: KClass<T>): T
    }

    /** Creates one snapshot store per source file; the app enforces "one instance per file". / 每个来源文件创建一个快照存储；由 App 保证「一个文件一个实例」。 */
    interface SnapshotFactory {
        fun <T> create(id: SourceId, serializer: KSerializer<T>): SnapshotStore<T>
    }

    override val moduleId: String get() = module.id

    private var settingsCreated = false

    override fun <T> settings(serializer: KSerializer<T>, default: T): ValueStore<T> {
        check(!settingsCreated) { "settings() of module $moduleId requested twice" }
        settingsCreated = true
        return moduleSettings.forModule(moduleId, serializer, default)
    }

    override fun <T> snapshots(sourceId: SourceId, serializer: KSerializer<T>): SnapshotStore<T> {
        require(sourceId.moduleId == moduleId) { "module $moduleId cannot use source $sourceId" }
        return snapshotFactory.create(sourceId, serializer)
    }

    override fun secret(name: String): ModuleSecret = secretOf("$moduleId.$name")

    override val googleMapsKey: ModuleSecret get() = secretOf(SharedSecrets.GOOGLE_MAPS)

    private fun secretOf(id: String): ModuleSecret {
        return object : ModuleSecret {
            override fun observe(): Flow<SecretState> = secrets.observe(id)
            override suspend fun usable(): String = secrets.usable(id)
            override suspend fun put(plain: String) = secrets.put(id, plain)
        }
    }

    override fun <T : RoomDatabase> database(type: KClass<T>): T = databaseFactory.create(moduleId, type)

    override fun notificationId(offset: Int): Int = ModulePolicy.notificationId(moduleId, module.notificationIds, offset)
}
