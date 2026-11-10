package io.github.lonemoonspace.dayloom.feature.news.data

import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleOwner
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.Feed
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedError
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedFetcher
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedParser
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedState
import io.github.lonemoonspace.dayloom.feature.news.domain.FetchResult
import io.github.lonemoonspace.dayloom.feature.news.domain.ParsedFeed
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads the followed feeds straight from the phone and merges them into the stored articles, [FeedPolicy.PARALLEL_FETCHES]
 * at a time: each batch is merged and let go before the next is fetched, so fifty feeds never sit in memory at once. One
 * broken feed only marks itself; the sync fails only when every feed failed for lack of network. Network calls run outside
 * [lock], like [NewsSync], so a tap is never held up.
 * 在手机上直接读取订阅的源并合并进本地文章，每批 [FeedPolicy.PARALLEL_FETCHES] 个：一批合并完、释放掉之后再抓下一批，五十个源不会
 * 同时留在内存里。某个源坏了只标记它自己；只有所有源都因为没网失败时，这次同步才算失败。与 [NewsSync] 一样，网络调用在 [lock]
 * 之外进行，点按不会被卡住。
 */
class FeedSync(
    private val store: ArticleStore,
    private val fetcher: FeedFetcher,
    private val owner: ArticleOwner,
    private val lock: Mutex,
) {
    private class Outcome(val state: FeedState, val parsed: ParsedFeed? = null, val failure: AppError? = null)

    suspend fun sync(feeds: List<Feed>, previous: List<FeedState>, nowMillis: Long): SyncSummary {
        // With no feed every article would count as unfollowed and be deleted. / 没有订阅源时，所有文章都会被当成退订而删除。
        if (feeds.isEmpty()) return lock.withLock { store.summary() }
        // Validators are only worth sending when the articles they vouch for are still here; after a switch of source or a
        // feed added again, a 304 would leave the list empty.
        // 只有校验值对应的文章还在本机时才值得发送；切换来源或重新添加某个源之后，一个 304 会让列表空着。
        val held = lock.withLock {
            if (owner.get() == FeedPolicy.OWNER) store.ids().mapTo(HashSet()) { FeedPolicy.feedTagOf(it) } else emptySet()
        }
        val before = previous.associateBy { it.url }
        val states = mutableListOf<FeedState>()
        var failure: AppError? = null
        var offline = true
        for (batch in feeds.chunked(FeedPolicy.PARALLEL_FETCHES)) {
            val outcomes = coroutineScope {
                batch.map { feed ->
                    async { read(feed, before[feed.url]?.takeIf { FeedPolicy.feedTag(feed.url) in held }, nowMillis) }
                }.awaitAll()
            }
            states += outcomes.map { it.state }
            if (outcomes.any { it.state.error != FeedError.NETWORK }) offline = false
            failure = failure ?: outcomes.firstNotNullOfOrNull { it.failure }
            val fetched = outcomes.mapNotNull { o -> o.parsed?.let { o.state.url to it } }.toMap()
            if (fetched.isEmpty()) continue
            lock.withLock {
                // Taken over only once a feed has answered, so a sync without network keeps the other source's articles.
                // 只有某个源成功应答后才接管，没网时的同步不会丢掉另一个来源的文章。
                if (owner.get() != FeedPolicy.OWNER) {
                    store.clear()
                    owner.set(FeedPolicy.OWNER)
                }
                // Only this batch's feeds can have their articles rewritten, so only their text is read.
                // 只有这一批订阅源的文章可能被改写，所以只读它们的正文。
                val tags = fetched.keys.mapTo(HashSet()) { FeedPolicy.feedTag(it) }
                val local = store.all(withText = { FeedPolicy.feedTagOf(it) in tags })
                store.apply(FeedPolicy.merge(local, fetched, feeds, nowMillis))
            }
        }
        if (offline) throw failure ?: AppError.Offline()
        return lock.withLock { store.summary() }.copy(feeds = states)
    }

    private suspend fun read(feed: Feed, previous: FeedState?, nowMillis: Long): Outcome {
        val kept = FeedState(url = feed.url, etag = previous?.etag.orEmpty(), lastModified = previous?.lastModified.orEmpty(), okAt = previous?.okAt ?: 0)
        return try {
            when (val result = fetcher.fetch(feed.url, kept.etag, kept.lastModified)) {
                FetchResult.NotModified -> Outcome(kept.copy(okAt = nowMillis))
                FetchResult.TooLarge -> Outcome(kept.copy(error = FeedError.TOO_LARGE))
                is FetchResult.Fetched -> {
                    val parsed = FeedParser.parse(result.bytes) ?: return Outcome(kept.copy(error = FeedError.NOT_A_FEED))
                    // Only the items the merge can keep are held on to. / 只留下合并时可能保留的条目。
                    Outcome(FeedState(feed.url, result.etag, result.lastModified, okAt = nowMillis), FeedPolicy.newest(parsed, nowMillis))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppError.Http) {
            Outcome(kept.copy(error = FeedError.HTTP, code = e.code), failure = e)
        } catch (e: AppError.BadData) {
            Outcome(kept.copy(error = FeedError.NOT_A_FEED), failure = e)
        } catch (e: Exception) {
            Outcome(kept.copy(error = FeedError.NETWORK), failure = e as? AppError ?: AppError.Network(e))
        }
    }
}

/**
 * Finds the feed behind an address the user typed: the address itself when it is a feed, else the first of the feeds the
 * page links to that can be read. A dead candidate is skipped, not reported, since the next one may well work.
 * 找出用户输入的地址背后的订阅源：地址本身是订阅源就用它，否则用网页指向的、第一个能读的订阅源。坏掉的候选直接跳过、不报错，
 * 因为下一个很可能是好的。
 */
class FeedFinder(private val fetcher: FeedFetcher) {
    /** The feed's address and content, or null when none was found; the page's own failure is thrown. / 订阅源的地址与内容，找不到时为 null；网页本身的失败会抛出。 */
    suspend fun find(url: String): Pair<String, ParsedFeed>? {
        val page = fetcher.fetch(url, "", "") as? FetchResult.Fetched ?: return null
        FeedParser.parse(page.bytes)?.let { return url to it }
        val candidates = FeedParser.discover(FeedParser.pageText(page.bytes), page.finalUrl.ifBlank { url })
        for (candidate in candidates.take(MAX_CANDIDATES)) {
            val feedUrl = FeedPolicy.normalizeFeedUrl(candidate) ?: continue
            val fetched = try {
                fetcher.fetch(feedUrl, "", "") as? FetchResult.Fetched ?: continue
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            FeedParser.parse(fetched.bytes)?.let { return feedUrl to it }
        }
        return null
    }

    private companion object {
        /** Feed links on one page worth trying. / 一个网页上值得尝试的订阅源链接数。 */
        const val MAX_CANDIDATES = 3
    }
}
