package io.github.lonemoonspace.dayloom.core.refresh

import io.github.lonemoonspace.dayloom.core.i18n.UiText
import io.github.lonemoonspace.dayloom.core.storage.InMemorySnapshotStore
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.time.AppClock
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A controllable source whose parameter is a plain string ("" = not configured); a fetch returns "<param>#<n>".
 * [gate] suspends every fetch until completed (to create an in-flight refresh); [failWith] makes the fetch throw.
 * 可控的假来源，参数是一个字符串（"" = 未配置）；抓取结果为 "<参数>#<第几次>"。
 * [gate] 让每次抓取挂起到它完成（制造在途刷新）；[failWith] 让抓取抛出它。
 */
class FakeSource(
    name: String,
    clock: AppClock,
    val store: InMemorySnapshotStore<String> = InMemorySnapshotStore(),
    initialParam: String = "A",
    /** Extra refresh-key part, standing in for a credential fingerprint. / 额外的刷新键部分，代替凭据指纹。 */
    initialSignal: Any = 0,
) : CachedSource<String, String>(SourceId("test.$name"), store, clock) {

    val param = MutableStateFlow(initialParam)
    val signal = MutableStateFlow(initialSignal)

    /** Replace to simulate a broken settings file. / 替换它可模拟设置文件损坏。 */
    var inputsOverride: Flow<SourceInput<String>>? = null

    override val schemaVersion: Int = 1
    override val maxAge: Duration = Duration.ofHours(1)

    override val inputs: Flow<SourceInput<String>>
        get() = inputsOverride ?: kotlinx.coroutines.flow.combine(param, signal) { p, s -> input(p, s) }

    var gate: CompletableDeferred<Unit>? = null
    var failWith: Exception? = null

    var fetchCount = 0
        private set
    val fetchedWith = mutableListOf<String>()
    val previousSeen = mutableListOf<Snapshot<String>?>()

    override suspend fun fetch(params: String, now: ZonedDateTime, previous: Snapshot<String>?): String {
        fetchCount++
        fetchedWith += params
        previousSeen += previous
        gate?.await()
        failWith?.let { throw it }
        return "$params#$fetchCount"
    }

    companion object {
        val MISSING = UiText.Raw("param")

        fun input(param: String, signal: Any = 0): SourceInput<String> {
            val p = param.trim()
            return if (p.isEmpty()) SourceInput.Missing(MISSING) else SourceInput.Ready(p, key = p, refreshKey = p to signal)
        }
    }
}
