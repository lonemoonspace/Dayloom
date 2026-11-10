package io.github.lonemoonspace.dayloom.feature.news

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.module.ConfigState
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.ModuleTab
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.refresh.SourceResult
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.feature.news.data.FeedApi
import io.github.lonemoonspace.dayloom.feature.news.data.FeedFinder
import io.github.lonemoonspace.dayloom.feature.news.data.FeedSync
import io.github.lonemoonspace.dayloom.feature.news.data.LlmApi
import io.github.lonemoonspace.dayloom.feature.news.data.MinifluxApi
import io.github.lonemoonspace.dayloom.feature.news.data.NewsDatabase
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSync
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSourceSettings
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSyncSource
import io.github.lonemoonspace.dayloom.feature.news.data.RoomArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleOwner
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleItem
import io.github.lonemoonspace.dayloom.feature.news.domain.Feed
import io.github.lonemoonspace.dayloom.feature.news.domain.FeedPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSourceKind
import io.github.lonemoonspace.dayloom.feature.news.domain.ListedArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import io.github.lonemoonspace.dayloom.feature.news.ui.AddFeedResult
import io.github.lonemoonspace.dayloom.feature.news.ui.ConnectResult
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsActions
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsCard
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsSettingsSection
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsTab
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * News in its own tab, from feeds the phone reads directly (the default, no server or account needed) or from the user's
 * own Miniflux server with read and starred state synced both ways; AI summaries from any OpenAI-compatible endpoint in the
 * app language. Off by default: there is nothing to show until feeds are picked.
 * 独立标签页里的新闻，来自手机直接读取的订阅源（默认，不需要服务器或账号），或用户自己的 Miniflux 服务器（已读与收藏双向同步）；
 * 可用任意兼容 OpenAI 的接口按 App 语言生成 AI 摘要。默认关闭：选好订阅源之前没有内容可显示。
 */
object NewsModule : FeatureModule {
    override val id = "news"
    override val title = R.string.news_title
    override val summary = R.string.news_summary
    override val icon = R.drawable.ic_news
    override val defaultEnabled = false

    override fun create(ctx: ModuleContext): ModuleInstance = NewsInstance(ctx)
}

@Serializable
data class NewsSettings(
    /** Reserved for migrations after v1.0.0. / 预留给 v1.0.0 之后的迁移。 */
    val version: Int = 1,
    /** Normalized https base, e.g. `https://reader.example.org`. / 规范化后的 https 地址，如 `https://reader.example.org`。 */
    val serverUrl: String = "",
    /** OpenAI-compatible base, e.g. `https://api.example.org/v1`. / 兼容 OpenAI 的基础地址，如 `https://api.example.org/v1`。 */
    val llmUrl: String = "",
    val llmModel: String = "",
    /**
     * The server the stored articles came from (design §11.7), or [FeedPolicy.OWNER] for feeds; written by the sync.
     * 本地文章来自的服务器（设计文档 §11.7），来自订阅源时为 [FeedPolicy.OWNER]；由同步写入。
     */
    val articlesFrom: String = "",
    /** Null until chosen; see [kind]. / 选择之前为 null；见 [kind]。 */
    val source: NewsSourceKind? = null,
    val feeds: List<Feed> = emptyList(),
    /** The suggested feeds were shown once; they do not pop up again. / 推荐源已经弹出过一次，不再自动弹出。 */
    val starterOffered: Boolean = false,
) {
    /**
     * The source in use: the one chosen, else Miniflux for someone who already set up a server, else feeds.
     * 正在使用的来源：选过的那个；没选过时，已经设置了服务器的用 Miniflux，否则用订阅源。
     */
    fun kind(): NewsSourceKind = source ?: if (serverUrl.isNotBlank()) NewsSourceKind.MINIFLUX else NewsSourceKind.RSS
}

/** Saved values reach the source's inputs through DataStore within milliseconds; this is only a safety net. / 保存的值经 DataStore 几毫秒内就到达来源输入；这只是兜底。 */
private const val INPUT_WAIT_MS = 5_000L

/** Cached list items beyond the current list before the cache is pruned. / 缓存的列表项比当前列表多出这么多时才清理。 */
private const val ITEM_CACHE_SLACK = 50

/** Articles whose text is read in one go when the list is built. / 生成列表时一次读多少篇文章的正文。 */
private const val ITEM_TEXT_BATCH = 50

private class NewsInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(NewsSettings.serializer(), NewsSettings())
    private val token = ctx.secret("miniflux_token")
    private val llmKey = ctx.secret("llm_api_key")
    private val articles = RoomArticleStore(ctx.database(NewsDatabase::class, *NewsDatabase.MIGRATIONS).articles())
    private val miniflux = MinifluxApi(ctx.http)
    private val feedApi = FeedApi(ctx.http)
    private val finder = FeedFinder(feedApi)
    private val llm = LlmApi(ctx.http)
    private val owner = object : ArticleOwner {
        override suspend fun get() = store.get().articlesFrom
        override suspend fun set(baseUrl: String) = store.update { it.copy(articlesFrom = baseUrl) }
    }

    /** One lock for both syncs: they share the stored articles. / 两种同步共用一把锁：它们读写同一批本地文章。 */
    private val lock = Mutex()
    private val sync = NewsSync(articles, miniflux, owner, lock)
    private val feedSync = FeedSync(articles, feedApi, owner, lock)

    private val kind: Flow<NewsSourceKind> = store.flow.map { it.kind() }.distinctUntilChanged()

    private val source = NewsSyncSource(
        id = ctx.sourceId("sync"),
        store = ctx.snapshots(ctx.sourceId("sync"), SyncSummary.serializer()),
        clock = ctx.clock,
        settings = store.flow.map { NewsSourceSettings(it.kind(), it.serverUrl, it.feeds) }.distinctUntilChanged(),
        secret = token.observe(),
        token = token::usable,
        sync = sync,
        feedSync = feedSync,
    )

    override val sources = listOf(source)

    private val ready: Flow<Boolean> = combine(store.flow, token.observe()) { s, t ->
        when (s.kind()) {
            NewsSourceKind.RSS -> s.feeds.isNotEmpty()
            NewsSourceKind.MINIFLUX -> NewsSyncPolicy.normalizeBaseUrl(s.serverUrl) != null && t.display.isNotBlank()
        }
    }.distinctUntilChanged()

    override val configured: Flow<ConfigState> = combine(ready, kind) { isReady, k ->
        when {
            isReady -> ConfigState.Ready
            k == NewsSourceKind.RSS -> ConfigState.NeedsSetup(uiText(R.string.news_setup_feeds))
            else -> ConfigState.NeedsSetup(uiText(R.string.news_setup_server))
        }
    }

    /** The summarize button only shows once endpoint, model and key are all set. / 接口、模型与 Key 都填好后才显示生成摘要的按钮。 */
    private val summarizerReady: Flow<Boolean> = combine(store.flow, llmKey.observe()) { s, k ->
        NewsSyncPolicy.normalizeBaseUrl(s.llmUrl) != null && s.llmModel.isNotBlank() && k.display.isNotBlank()
    }.distinctUntilChanged()

    override val homeCards = listOf(
        HomeCard(key = "unread", title = R.string.news_title, defaultOrder = 300) {
            val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
            // Counts come from the stored articles, so reading one updates the card at once. / 数量取自本地文章，读完一篇卡片立即更新。
            val summary by remember { articles.observeSummary() }.collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val isReady by ready.collectAsStateWithLifecycle(initialValue = true)
            val k by kind.collectAsStateWithLifecycle(initialValue = NewsSourceKind.RSS)
            // The minute tick makes the card turn "cached" while it is on screen, not only at the next redraw.
            // 用每分钟跳动的时间，卡片停在屏幕上时也会变成「缓存」，不必等下一次重绘。
            val now = rememberMinuteTick()
            val stale = snapshot?.let { source.isStale(it, now.toInstant()) } == true
            NewsCard(
                summary = summary.takeIf { snapshot != null },
                configured = isReady,
                feeds = k == NewsSourceKind.RSS,
                error = status[source.id]?.lastError,
                stale = stale,
                pushRejected = snapshot?.value?.pushRejected == true,
            )
        },
    )

    override val tab = ModuleTab(label = R.string.news_title, icon = R.drawable.ic_news) { Tab() }

    override val settings = SettingsSection {
        // Null until DataStore answers, so the first-use suggestions never pop up for someone who already has feeds.
        // DataStore 应答之前为 null，已经有订阅源的人不会看到首次使用的推荐源弹出来。
        val loaded by store.flow.collectAsStateWithLifecycle(initialValue = null)
        val saved = loaded ?: NewsSettings()
        val tokenState by token.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        val keyState by llmKey.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        NewsSettingsSection(
            saved = saved,
            loaded = loaded != null,
            token = tokenState,
            llmKey = keyState,
            feedStates = snapshot?.value?.feeds.orEmpty(),
            connect = ::connect,
            addFeed = ::addFeed,
            saveLlmKey = { plain -> ctx.appScope.launch { llmKey.put(plain) } },
        ) { transform -> ctx.appScope.launch { store.update(transform) } }
    }

    /**
     * Checks an address the user typed and adds the feed: the address itself when it is a feed, else the first feed the page
     * links to. Runs in the app scope, so leaving Settings halfway still adds it; the new feed list then refreshes the source.
     * 检查用户输入的地址并添加订阅源：地址本身是订阅源就用它，否则用网页指向的第一个订阅源。放在应用级作用域里运行，中途离开设置页
     * 也会添加；订阅源列表一变，来源就会刷新。
     */
    private suspend fun addFeed(text: String): AddFeedResult = ctx.appScope.async {
        val url = FeedPolicy.normalizeFeedUrl(text) ?: return@async AddFeedResult.Invalid
        val before = store.get().feeds
        if (before.any { it.url == url }) return@async AddFeedResult.Exists
        if (before.size >= FeedPolicy.MAX_FEEDS) return@async AddFeedResult.Full
        try {
            val (feedUrl, parsed) = finder.find(url) ?: return@async AddFeedResult.NotFound
            if (store.get().feeds.any { it.url == feedUrl }) return@async AddFeedResult.Exists
            val title = parsed.title.ifBlank { FeedPolicy.hostOf(feedUrl) }
            store.update { it.copy(feeds = FeedPolicy.add(it.feeds, listOf(Feed(feedUrl, title)))) }
            AddFeedResult.Added(title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AddFeedResult.Failed(e as? AppError ?: AppError.Unexpected(e))
        }
    }.await()

    /**
     * Saves the server (and a new token), waits until the sync source sees them, then syncs once through the coordinator.
     * Runs in the app scope, so leaving Settings halfway still saves everything.
     * 保存服务器（及新令牌），等同步来源看到新值后，经协调器同步一次。放在应用级作用域里运行，中途离开设置页也会全部保存。
     */
    private suspend fun connect(url: String, plainToken: String?): ConnectResult = ctx.appScope.async {
        try {
            store.update { it.copy(serverUrl = url) }
            if (plainToken != null) token.put(plainToken)
            withTimeoutOrNull(INPUT_WAIT_MS) { source.inputs.first { it is SourceInput.Ready<*> && it.key == url } }
                ?: return@async ConnectResult.Failed(AppError.NotConfigured(uiText(R.string.news_setup_token)))
            when (val result = ctx.coordinator.refresh(setOf(source.id), Trigger.USER).results[source.id]) {
                is SourceResult.Success -> ConnectResult.Connected(articles.observeSummary().first().unread)
                is SourceResult.Failed -> ConnectResult.Failed(result.error)
                is SourceResult.Skipped, null -> ConnectResult.Failed(AppError.Offline())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ConnectResult.Failed(e as? AppError ?: AppError.Unexpected(e))
        }
    }.await()

    // Writes go to the app scope, so leaving the tab right after a tap does not cancel them. / 写入放在应用级作用域，点完立刻离开也不会被取消。
    private val actions = object : NewsActions {
        override fun open(article: StoredArticle) {
            if (!article.read) setRead(article.id, true)
        }

        override fun setRead(id: Long, read: Boolean) {
            ctx.appScope.launch {
                sync.markRead(id, read, ctx.clock.now().toInstant().toEpochMilli())
                pushReadStates()
            }
        }

        override fun setStarred(id: Long, starred: Boolean) {
            ctx.appScope.launch { sync.markStarred(id, starred) }
        }

        override fun fetchFullText(article: StoredArticle) {
            ctx.appScope.launch {
                if (store.get().kind() != NewsSourceKind.MINIFLUX) return@launch
                val base = NewsSyncPolicy.normalizeBaseUrl(store.get().serverUrl) ?: return@launch
                val key = token.usable().ifBlank { return@launch }
                sync.fetchFullText(base, key, article.id, ctx.clock.now().toInstant().toEpochMilli())
            }
        }

        override suspend fun summarize(article: StoredArticle, language: String) {
            val settings = store.get()
            val base = NewsSyncPolicy.normalizeBaseUrl(settings.llmUrl)
            val key = llmKey.usable()
            if (base == null || settings.llmModel.isBlank() || key.isBlank()) throw AppError.NotConfigured(uiText(R.string.news_setup_llm))
            val summary = llm.summarize(base, key, settings.llmModel, article.title, NewsSyncPolicy.plainText(article.contentHtml), language)
            articles.saveSummary(article.id, summary, language)
        }
    }

    /** Best effort: whatever fails here goes out with the next sync. / 尽力而为：这里失败的会随下一次同步发出。 */
    private suspend fun pushReadStates() {
        if (store.get().kind() != NewsSourceKind.MINIFLUX) return
        val base = NewsSyncPolicy.normalizeBaseUrl(store.get().serverUrl) ?: return
        val key = token.usable().ifBlank { return }
        try {
            sync.pushReadStates(base, key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Still pending; the next sync retries. / 仍是待发送，下一次同步会重试。
        }
    }

    private class CachedItem(val textLength: Int, val item: ArticleItem)

    /**
     * List items by article id. An article's text is read from the database and stripped of its HTML only when it is new
     * or its text changed, not on every read or star: the list itself arrives without text.
     * 按文章 id 缓存的列表项。只有新文章或正文变了的文章才从数据库读正文并去掉 HTML，标已读或收藏时不重读：列表本身不带正文。
     */
    private val items = ConcurrentHashMap<Long, CachedItem>()

    private suspend fun itemsOf(list: List<ListedArticle>): List<ArticleItem> {
        if (items.size > list.size + ITEM_CACHE_SLACK) items.keys.retainAll(list.mapTo(HashSet()) { it.article.id })
        val changed = list.filter { items[it.article.id]?.textLength != it.textLength }
        for (batch in changed.chunked(ITEM_TEXT_BATCH)) {
            val texts = articles.texts(batch.map { it.article.id })
            batch.forEach { listed ->
                items[listed.article.id] = CachedItem(listed.textLength, NewsSyncPolicy.listItem(listed.article, texts[listed.article.id].orEmpty()))
            }
        }
        return list.mapNotNull { listed -> items[listed.article.id]?.item?.copy(article = listed.article) }
    }

    @Composable
    private fun Tab() {
        var filter by rememberSaveable { mutableStateOf(ArticleFilter.UNREAD) }
        var query by rememberSaveable { mutableStateOf("") }
        var openId by rememberSaveable { mutableStateOf<Long?>(null) }
        val isReady by ready.collectAsStateWithLifecycle(initialValue = true)
        val listItems by remember(filter) { articles.observe(filter).map { itemsOf(it) }.flowOn(Dispatchers.Default) }
            .collectAsStateWithLifecycle(initialValue = null)
        val list = remember(listItems, query) { listItems?.filter { NewsSyncPolicy.matches(it, query) } }
        val open by remember(openId) { openId?.let { articles.observe(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
        val counts by remember { articles.observeSummary() }.collectAsStateWithLifecycle(initialValue = null)
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val k by kind.collectAsStateWithLifecycle(initialValue = NewsSourceKind.RSS)
        val canSummarize by summarizerReady.collectAsStateWithLifecycle(initialValue = false)
        val feedList by remember { store.flow.map { it.feeds }.distinctUntilChanged() }.collectAsStateWithLifecycle(initialValue = emptyList())
        LaunchedEffect(Unit) {
            try {
                ctx.coordinator.refreshDue(setOf(source.id), Trigger.AUTO, daytime = false, throttleFailures = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Shown through the coordinator's status. / 通过协调器状态显示。
            }
        }
        val feeds = k == NewsSourceKind.RSS
        NewsTab(
            configured = isReady,
            feeds = feeds,
            failingFeeds = if (feeds) FeedPolicy.failing(feedList, snapshot?.value?.feeds.orEmpty()) else emptyList(),
            canSummarize = canSummarize,
            filter = filter,
            onFilter = { filter = it },
            query = query,
            onQuery = { query = it },
            articles = list,
            open = open,
            onClose = { openId = null },
            counts = counts,
            error = status[source.id]?.lastError,
            pushRejected = snapshot?.value?.pushRejected == true,
            actions = object : NewsActions by actions {
                override fun open(article: StoredArticle) {
                    openId = article.id
                    actions.open(article)
                }
            },
        )
    }
}
