package io.github.lonemoonspace.dayloom.core.module

import io.github.lonemoonspace.dayloom.core.location.Places
import io.github.lonemoonspace.dayloom.core.network.NetworkStatus
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCoordinator
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.routine.Routine
import io.github.lonemoonspace.dayloom.core.secret.SecretState
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
 * Everything core offers a module; modules get their dependencies only from here. Every name a module stores under
 * (settings key, snapshot file, secret id, notification channel) is prefixed with the module id here, so modules cannot collide
 * and never build those names by hand.
 * core 提供给模块的全部能力；模块只能从这里拿依赖。模块存储用的每个名字（设置键、快照文件、凭据 id、通知渠道）
 * 都在这里加上模块 id 前缀，模块之间不会撞名，也不需要自己拼这些名字。
 */
interface ModuleContext {
    val moduleId: String
    val http: OkHttpClient
    val clock: AppClock
    val connectivity: NetworkStatus
    val coordinator: RefreshCoordinator
    val appScope: CoroutineScope

    /** Saved places shared by all modules (read-only; edited in settings). / 所有模块共用的已保存地点（只读；在设置页编辑）。 */
    val places: Places

    /** The shared daily windows (to work / back home). / 共用的日常时间窗（上班 / 回家）。 */
    val routine: Flow<Routine>

    /** This module's settings; call once per module. / 本模块的设置；每个模块只调用一次。 */
    fun <T> settings(serializer: KSerializer<T>, default: T): ValueStore<T>

    fun sourceId(name: String): SourceId = SourceId("$moduleId.$name")

    /** Snapshot store for [sourceId]; one per source. / [sourceId] 的快照存储；每个来源一个。 */
    fun <T> snapshots(sourceId: SourceId, serializer: KSerializer<T>): SnapshotStore<T>

    fun secret(name: String): ModuleSecret

    /**
     * The user's Google Maps Platform key, shared with place search so it is entered once (`core.google_maps`).
     * 用户的 Google Maps Platform Key，与地点搜索共用，只需输入一次（`core.google_maps`）。
     */
    val googleMapsKey: ModuleSecret

    /**
     * The app's Room database `dayloom.db` (design §6.1), built for [type]. Only one module may own it; a second caller
     * fails at creation, so a new module needing Room gets its own file instead of sharing tables.
     * App 的 Room 数据库 `dayloom.db`（设计文档 §6.1），按 [type] 构建。只能由一个模块持有；第二个调用者在创建时就会失败，
     * 所以新模块需要 Room 时要用自己的文件，而不是共用表。
     */
    fun <T : RoomDatabase> database(type: KClass<T>): T

    fun channelId(name: String): String = "$moduleId.$name"

    /**
     * The [offset]-th id of this module's notification range; throws if outside it.
     * 本模块通知号段中的第 [offset] 个 id；超出号段时抛异常。
     */
    fun notificationId(offset: Int): Int

    /** `dayloom://<moduleId>`, opens the module's tab, or home when it has none. / `dayloom://<模块id>`，打开模块的标签页，没有时打开首页。 */
    val deepLink: String get() = "dayloom://$moduleId"
}

/**
 * One credential of a module. Requests use [usable]; input fields show [observe]'s `display`.
 * 模块的一个凭据。发请求用 [usable]；输入框显示 [observe] 的 `display`。
 */
interface ModuleSecret {
    fun observe(): Flow<SecretState>
    suspend fun usable(): String
    suspend fun put(plain: String)
}
