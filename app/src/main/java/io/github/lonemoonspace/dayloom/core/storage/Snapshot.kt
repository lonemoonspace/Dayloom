package io.github.lonemoonspace.dayloom.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * The last successful fetch of one source: structured data only, never rendered text.
 * New fields need defaults, otherwise snapshots already on disk fail to decode and are treated as missing.
 * 某个来源最近一次成功抓取的结果：只存结构化数据，不存界面文案。
 * 新字段必须有默认值，否则磁盘上的旧快照解码失败、被当作无缓存。
 */
@Serializable
data class Snapshot<T>(
    val value: T,
    /** Fetch time, epoch millis. / 抓取时刻，epoch 毫秒。 */
    val fetchedAt: Long,
    /** Fingerprint of the parameters that produced it (place, stops…). / 产生这份数据的参数指纹（地点、站点……）。 */
    val paramsKey: String,
    /** Must equal the source's schemaVersion, otherwise treated as missing. / 必须等于来源的 schemaVersion，否则视为无缓存。 */
    val schema: Int,
)

/**
 * Snapshot storage of a single source: one instance and one file per source, so writes never wake other sources' observers.
 * 单个来源的快照存储：每个来源一个实例、一个文件，写入不会唤醒其他来源的观察者。
 */
interface SnapshotStore<T> {
    /** Emits only when this source writes; null when missing or undecodable. / 只在本来源写入时发射；无文件或解码失败为 null。 */
    val flow: Flow<Snapshot<T>?>

    suspend fun read(): Snapshot<T>?

    suspend fun write(snapshot: Snapshot<T>)

    suspend fun clear()
}
