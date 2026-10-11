package io.github.lonemoonspace.dayloom.feature.news.data

import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceId
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.SnapshotStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Two-way sync with Miniflux. Network calls run outside the lock, so a tap is never held up by a slow server; every read
 * and write of the stored articles runs inside it, and the merge only clears a pending change whose value was the one sent.
 * 与 Miniflux 的双向同步。网络调用在锁外进行，服务器慢时点按也不会卡住；对本地文章的每次读写都在锁内，合并时只清除值与已发送值
 * 相同的待发送修改。
 */
class NewsSync(private val store: ArticleStore, private val api: MinifluxApi, private val onPushError: (Exception) -> Unit = {}) {
    private val lock = Mutex()

    /** When each excerpt-only article's full text was last tried and failed. / 每篇只有摘要的文章上次抓全文失败的时刻。 */
    private val fullTextTried = ConcurrentHashMap<Long, Long>()

    /** Read states already sent since the last sync, so opening articles does not resend them. / 上次同步以来已发出的已读状态，打开文章时不再重复发送。 */
    private val sentSinceSync = ConcurrentHashMap<Long, Boolean>()

    suspend fun sync(baseUrl: String, token: String, nowMillis: Long): SyncSummary {
        val unread = api.unread(baseUrl, token)
        val starred = api.starred(baseUrl, token)
        val plan = lock.withLock { NewsSyncPolicy.pushPlan(store.all(), unread, starred) }
        // A failed push keeps the changes pending for the next sync; the download still lands. / 推送失败时修改保留待发送，下载的内容照样写入。
        val pushed = try {
            api.setStatus(baseUrl, token, plan.markRead, read = true)
            api.setStatus(baseUrl, token, plan.markUnread, read = false)
            plan.toggleStar.forEach { api.toggleStar(baseUrl, token, it) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onPushError(e)
            false
        }
        lock.withLock {
            val result = NewsSyncPolicy.merge(
                local = store.all(),
                unread = unread,
                starred = starred,
                sentRead = if (pushed) plan.sentRead else emptyMap(),
                sentStarred = if (pushed) plan.sentStarred else emptyMap(),
                nowMillis = nowMillis,
            )
            store.apply(result)
            sentSinceSync.clear()
        }
        fetchFullTexts(baseUrl, token, nowMillis)
        return lock.withLock { NewsSyncPolicy.summary(store.all()) }
    }

    /**
     * Fetches the full text of a few excerpt-only articles; one that fails waits before it is tried again, and never fails
     * the sync.
     * 为几篇只有摘要的文章抓取全文；失败的等一段时间再试，也不会让同步失败。
     */
    private suspend fun fetchFullTexts(baseUrl: String, token: String, nowMillis: Long) {
        fullTextTried.entries.removeIf { nowMillis - it.value >= NewsSyncPolicy.FULL_TEXT_RETRY.toMillis() }
        val due = lock.withLock { NewsSyncPolicy.fullTextDue(store.all(), fullTextTried, nowMillis) }
        for (article in due) {
            if (!fetchFullText(baseUrl, token, article.id)) fullTextTried[article.id] = nowMillis
        }
    }

    /**
     * Fetches one article's full text and keeps it when it is more than an excerpt; false when it could not be had.
     * 抓取一篇文章的全文，比摘要长时保存；拿不到时返回 false。
     */
    suspend fun fetchFullText(baseUrl: String, token: String, id: Long): Boolean {
        val html = try {
            api.fetchContent(baseUrl, token, id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The excerpt stays; a later sync or opening the article tries again. / 摘要保留；之后的同步或打开文章时再试。
            return false
        }
        if (NewsSyncPolicy.isStub(html)) return false
        lock.withLock { store.saveContent(id, html) }
        return true
    }

    suspend fun markRead(id: Long, read: Boolean, nowMillis: Long) = lock.withLock { store.markRead(id, read, if (read) nowMillis else 0) }

    suspend fun markStarred(id: Long, starred: Boolean) = lock.withLock { store.markStarred(id, starred) }

    /**
     * Sends pending read states right away, so another device sees them before the next sync. They stay pending: the next
     * sync sends them once more and clears them, which is harmless because setting a state is idempotent. Stars wait for
     * the sync: Miniflux only toggles them, which is safe only against the server's current state.
     * 立即发送待发送的已读状态，其他设备不用等下一次同步就能看到。它们仍保持待发送：下一次同步会再发一次并清除——设置状态是幂等的，
     * 重复发送无害。收藏等同步时再发：Miniflux 只能切换收藏，只有对照服务器当前状态才安全。
     */
    suspend fun pushReadStates(baseUrl: String, token: String) {
        val pending = lock.withLock { store.all() }
            .mapNotNull { a -> a.pendingRead?.let { a.id to it } }
            .filter { (id, read) -> sentSinceSync[id] != read }
        if (pending.isEmpty()) return
        api.setStatus(baseUrl, token, pending.filter { it.second }.map { it.first }, read = true)
        api.setStatus(baseUrl, token, pending.filterNot { it.second }.map { it.first }, read = false)
        sentSinceSync.putAll(pending)
    }
}

data class NewsParams(val baseUrl: String)

/**
 * `news.sync`: runs the sync in the shared refresh round (every 30 minutes, also in the background) and keeps the home
 * card's numbers as its snapshot; the articles themselves live in Room.
 * `news.sync`：在共用的刷新轮次里运行同步（每 30 分钟一次，后台也跑），快照里只存首页卡片要的数字；文章本身在 Room 里。
 */
class NewsSyncSource(
    id: SourceId,
    store: SnapshotStore<SyncSummary>,
    clock: AppClock,
    baseUrl: Flow<String>,
    secret: Flow<SecretState>,
    private val token: suspend () -> String,
    private val sync: NewsSync,
) : CachedSource<NewsParams, SyncSummary>(id, store, clock) {

    override val schemaVersion = 1

    override val maxAge: Duration = Duration.ofHours(3)

    override val cadence = RefreshCadence(interval = Duration.ofMinutes(30))

    override val inputs: Flow<SourceInput<NewsParams>> = combine(baseUrl, secret) { url, s ->
        val normalized = NewsSyncPolicy.normalizeBaseUrl(url)
        when {
            normalized == null -> SourceInput.Missing(uiText(R.string.news_setup_server))
            s.display.isBlank() -> SourceInput.Missing(uiText(R.string.news_setup_token))
            else -> SourceInput.Ready(NewsParams(normalized), key = normalized, refreshKey = normalized to Objects.hash(s.display, s.unreadable))
        }
    }

    override suspend fun fetch(params: NewsParams, now: ZonedDateTime, previous: Snapshot<SyncSummary>?): SyncSummary {
        val key = token().ifBlank { throw AppError.NotConfigured(uiText(R.string.news_setup_token)) }
        return sync.sync(params.baseUrl, key, now.toInstant().toEpochMilli())
    }
}
