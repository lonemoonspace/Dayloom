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
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.feature.news.data.LlmApi
import io.github.lonemoonspace.dayloom.feature.news.data.MinifluxApi
import io.github.lonemoonspace.dayloom.feature.news.data.NewsDatabase
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSync
import io.github.lonemoonspace.dayloom.feature.news.data.NewsSyncSource
import io.github.lonemoonspace.dayloom.feature.news.data.RoomArticleStore
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsActions
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsCard
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsSettingsSection
import io.github.lonemoonspace.dayloom.feature.news.ui.NewsTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * News from the user's own Miniflux server: its own tab with the articles, read and starred state synced both ways, and AI
 * summaries from any OpenAI-compatible endpoint in the app language. Off by default because it needs a server.
 * 来自用户自己的 Miniflux 服务器的新闻：独立标签页显示文章，已读与收藏状态双向同步，并可用任意兼容 OpenAI 的接口按 App 语言生成
 * AI 摘要。需要服务器，所以默认关闭。
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
)

private class NewsInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val store = ctx.settings(NewsSettings.serializer(), NewsSettings())
    private val token = ctx.secret("miniflux_token")
    private val llmKey = ctx.secret("llm_api_key")
    private val articles = RoomArticleStore(ctx.database(NewsDatabase::class).articles())
    private val miniflux = MinifluxApi(ctx.http)
    private val llm = LlmApi(ctx.http)
    private val sync = NewsSync(articles, miniflux)

    private val source = NewsSyncSource(
        id = ctx.sourceId("sync"),
        store = ctx.snapshots(ctx.sourceId("sync"), SyncSummary.serializer()),
        clock = ctx.clock,
        baseUrl = store.flow.map { it.serverUrl }.distinctUntilChanged(),
        secret = token.observe(),
        token = token::usable,
        sync = sync,
    )

    override val sources = listOf(source)

    private val ready: Flow<Boolean> = combine(store.flow, token.observe()) { s, t ->
        NewsSyncPolicy.normalizeBaseUrl(s.serverUrl) != null && t.display.isNotBlank()
    }.distinctUntilChanged()

    override val configured: Flow<ConfigState> = ready.map {
        if (it) ConfigState.Ready else ConfigState.NeedsSetup(uiText(R.string.news_setup_server))
    }

    override val homeCards = listOf(
        HomeCard(key = "unread", title = R.string.news_title, defaultOrder = 300) {
            val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
            val status by ctx.coordinator.status.collectAsStateWithLifecycle()
            val isReady by ready.collectAsStateWithLifecycle(initialValue = true)
            val stale = snapshot?.let { source.isStale(it, ctx.clock.now().toInstant()) } == true
            NewsCard(snapshot?.value, isReady, status[source.id]?.lastError, stale)
        },
    )

    override val tab = ModuleTab(label = R.string.news_title, icon = R.drawable.ic_news) { Tab() }

    override val settings = SettingsSection {
        val saved by store.flow.collectAsStateWithLifecycle(initialValue = NewsSettings())
        val tokenState by token.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        val keyState by llmKey.observe().collectAsStateWithLifecycle(initialValue = SecretState.EMPTY)
        NewsSettingsSection(
            saved = saved,
            token = tokenState,
            llmKey = keyState,
            saveToken = { plain -> ctx.appScope.launch { token.put(plain) } },
            saveLlmKey = { plain -> ctx.appScope.launch { llmKey.put(plain) } },
        ) { transform -> ctx.appScope.launch { store.update(transform) } }
    }

    // Writes go to the app scope, so leaving the tab right after a tap does not cancel them. / 写入放在应用级作用域，点完立刻离开也不会被取消。
    private val actions = object : NewsActions {
        override fun open(id: Long) = setRead(id, true)

        override fun setRead(id: Long, read: Boolean) {
            ctx.appScope.launch {
                sync.markRead(id, read)
                pushReadStates()
            }
        }

        override fun setStarred(id: Long, starred: Boolean) {
            ctx.appScope.launch { sync.markStarred(id, starred) }
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

    @Composable
    private fun Tab() {
        var filter by rememberSaveable { mutableStateOf(ArticleFilter.UNREAD) }
        var openId by rememberSaveable { mutableStateOf<Long?>(null) }
        val isReady by ready.collectAsStateWithLifecycle(initialValue = true)
        val list by remember(filter) { articles.observe(filter) }.collectAsStateWithLifecycle(initialValue = null)
        val open by remember(openId) { openId?.let { articles.observe(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            try {
                ctx.coordinator.refreshDue(setOf(source.id), Trigger.AUTO, inWindow = false, throttleFailures = true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Shown through the coordinator's status. / 通过协调器状态显示。
            }
        }
        NewsTab(
            configured = isReady,
            filter = filter,
            onFilter = { filter = it },
            articles = list,
            open = open,
            onClose = { openId = null },
            counts = snapshot?.value,
            error = status[source.id]?.lastError,
            actions = object : NewsActions by actions {
                override fun open(id: Long) {
                    openId = id
                    actions.open(id)
                }
            },
        )
    }
}
