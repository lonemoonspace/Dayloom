package io.github.lonemoonspace.dayloom.feature.news.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.error.toAppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.currentLocale
import io.github.lonemoonspace.dayloom.core.ui.plusBars
import io.github.lonemoonspace.dayloom.core.ui.rememberMinuteTick
import io.github.lonemoonspace.dayloom.core.ui.rememberPatternFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.Headline
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the tab needs from the module. / 标签页需要模块提供的东西。 */
internal interface NewsActions {
    fun open(id: Long)
    fun setRead(id: Long, read: Boolean)
    fun setStarred(id: Long, starred: Boolean)

    /** Throws an [AppError] when the summarizer is not set up or fails. / 摘要未设置或失败时抛出 [AppError]。 */
    suspend fun summarize(article: StoredArticle, language: String)
}

/**
 * The home card: unread count and the three newest unread headlines. / 首页卡片：未读数与最新的三条未读标题。
 */
@Composable
internal fun NewsCard(summary: SyncSummary?, configured: Boolean, error: AppError?, stale: Boolean) {
    val now = rememberMinuteTick()
    InfoCard(
        title = stringResource(R.string.news_title),
        icon = R.drawable.ic_news,
        subtitle = summary?.let { pluralStringResource(R.plurals.news_unread_count, it.unread, it.unread) },
        stale = stale,
    ) {
        when {
            !configured -> Hint(stringResource(R.string.news_tab_setup))
            summary == null -> if (error == null) SkeletonLines()
            summary.latest.isEmpty() -> Hint(stringResource(R.string.news_all_read))
            else -> summary.latest.forEach { HeadlineRow(it, now) }
        }
        if (error != null) ErrorLine(error)
    }
}

@Composable
private fun HeadlineRow(headline: Headline, now: ZonedDateTime) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(headline.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Hint(headline.feed + " · " + ago(headline.publishedAt, now))
    }
}

/**
 * The tab: the article list with filters, or one article with its AI summary. Back from an article returns to the list.
 * 标签页：带筛选的文章列表，或者一篇文章及其 AI 摘要。从文章按返回键回到列表。
 */
@Composable
internal fun NewsTab(
    configured: Boolean,
    filter: ArticleFilter,
    onFilter: (ArticleFilter) -> Unit,
    articles: List<StoredArticle>?,
    open: StoredArticle?,
    onClose: () -> Unit,
    counts: SyncSummary?,
    error: AppError?,
    actions: NewsActions,
) {
    val now = rememberMinuteTick()
    if (open != null) {
        BackHandler(onBack = onClose)
        ArticleDetail(open, now, onClose, actions)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp).plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!configured) {
            item { InfoCard(title = stringResource(R.string.news_title), icon = R.drawable.ic_news) { Hint(stringResource(R.string.news_tab_setup)) } }
            return@LazyColumn
        }
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter == ArticleFilter.UNREAD, { onFilter(ArticleFilter.UNREAD) }, {
                    Text(counts?.let { pluralStringResource(R.plurals.news_unread_count, it.unread, it.unread) } ?: stringResource(R.string.news_filter_unread))
                })
                FilterChip(filter == ArticleFilter.STARRED, { onFilter(ArticleFilter.STARRED) }, { Text(stringResource(R.string.news_filter_starred)) })
                FilterChip(filter == ArticleFilter.ALL, { onFilter(ArticleFilter.ALL) }, { Text(stringResource(R.string.news_filter_all)) })
            }
        }
        if (error != null) item(key = "error") { ErrorLine(error) }
        when {
            articles == null -> item { SkeletonLines(lines = 4) }
            articles.isEmpty() -> item {
                Hint(stringResource(if (filter == ArticleFilter.UNREAD) R.string.news_all_read else R.string.news_none))
            }
            else -> items(articles, key = { it.id }) { article -> ArticleRow(article, now) { actions.open(article.id) } }
        }
    }
}

/** One article in the list: feed and age, title, reading time; read ones are dimmed. / 列表里的一篇：来源与时间、标题、阅读时长；已读的变淡。 */
@Composable
private fun ArticleRow(article: StoredArticle, now: ZonedDateTime, onClick: () -> Unit) {
    val dim = article.read
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appSurfaces.card, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                article.feedTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // The age keeps the rest of the row, so the star always ends up at the right edge. / 时间占满剩余部分，星标总在最右边。
            Text(
                " · " + ago(article.publishedAt, now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (article.starred) Icon(Icons.Default.Star, contentDescription = stringResource(R.string.news_starred), tint = MaterialTheme.statusColors.amber, modifier = Modifier.size(16.dp))
        }
        Text(
            article.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (dim) null else FontWeight.SemiBold,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (article.readingMinutes > 0 || article.summary.isNotEmpty()) {
            Hint(
                listOfNotNull(
                    article.readingMinutes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.news_reading_minutes, it, it) },
                    stringResource(R.string.news_has_summary).takeIf { article.summary.isNotEmpty() },
                ).joinToString(" · "),
            )
        }
    }
}

@Composable
private fun ArticleDetail(article: StoredArticle, now: ZonedDateTime, onClose: () -> Unit, actions: NewsActions) {
    val uri = LocalUriHandler.current
    val language = currentLocale().language
    val scope = rememberCoroutineScope()
    var summarizing by remember(article.id) { mutableStateOf(false) }
    var summaryError by remember(article.id) { mutableStateOf<AppError?>(null) }
    val content = remember(article.contentHtml) { AnnotatedString.fromHtml(article.contentHtml) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp).plusBars(top = true),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.news_back)) }
                Text(article.feedTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(article.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Hint(
                listOfNotNull(
                    article.author.takeIf { it.isNotBlank() },
                    Instant.ofEpochMilli(article.publishedAt).atZone(now.zone).format(rememberPatternFormatter("MMMdHm")),
                    article.readingMinutes.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.news_reading_minutes, it, it) },
                ).joinToString(" · "),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (article.url.isNotBlank()) TextButton(onClick = { uri.openUri(article.url) }) { Text(stringResource(R.string.news_open_original)) }
                TextButton(onClick = { actions.setStarred(article.id, !article.starred) }) {
                    Text(stringResource(if (article.starred) R.string.news_unstar else R.string.news_star))
                }
                TextButton(onClick = { actions.setRead(article.id, !article.read) }) {
                    Text(stringResource(if (article.read) R.string.news_mark_unread else R.string.news_mark_read))
                }
            }
        }
        item {
            InfoCard(title = stringResource(R.string.news_summary_title)) {
                when {
                    article.summary.isNotEmpty() && article.summaryLanguage == language -> SelectionContainer {
                        Text(article.summary, style = MaterialTheme.typography.bodyMedium)
                    }
                    summarizing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Hint(stringResource(R.string.news_summarizing))
                    }
                    else -> {
                        // A summary in another language is still shown until a new one is asked for. / 另一种语言的摘要在重新生成前照样显示。
                        if (article.summary.isNotEmpty()) Text(article.summary, style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = {
                            summarizing = true
                            summaryError = null
                            scope.launch {
                                try {
                                    actions.summarize(article, language)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    summaryError = e.toAppError()
                                } finally {
                                    summarizing = false
                                }
                            }
                        }) { Text(stringResource(R.string.news_summarize)) }
                    }
                }
                summaryError?.let { ErrorLine(it) }
            }
        }
        item { SelectionContainer { Text(content, style = MaterialTheme.typography.bodyLarge) } }
    }
}

/** "5 min", "3 h", "2 d" ago. / 「5 分钟前」「3 小时前」「2 天前」。 */
@Composable
private fun ago(millis: Long, now: ZonedDateTime): String {
    val age = Duration.between(Instant.ofEpochMilli(millis), now.toInstant())
    return when {
        age.toMinutes() < 60 -> pluralStringResource(R.plurals.news_ago_minutes, age.toMinutes().toInt().coerceAtLeast(1), age.toMinutes().toInt().coerceAtLeast(1))
        age.toHours() < 24 -> pluralStringResource(R.plurals.news_ago_hours, age.toHours().toInt(), age.toHours().toInt())
        else -> pluralStringResource(R.plurals.news_ago_days, age.toDays().toInt(), age.toDays().toInt())
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ErrorLine(error: AppError) {
    Text(error.userMessage().asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.statusColors.red)
}
