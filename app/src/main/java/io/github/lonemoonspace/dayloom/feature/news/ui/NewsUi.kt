package io.github.lonemoonspace.dayloom.feature.news.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import io.github.lonemoonspace.dayloom.core.ui.rememberTimeFormatter
import io.github.lonemoonspace.dayloom.core.ui.theme.appSurfaces
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleFilter
import io.github.lonemoonspace.dayloom.feature.news.domain.ArticleItem
import io.github.lonemoonspace.dayloom.feature.news.domain.Headline
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import io.github.lonemoonspace.dayloom.feature.news.domain.StoredArticle
import io.github.lonemoonspace.dayloom.feature.news.domain.SyncSummary
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What the tab needs from the module. / 标签页需要模块提供的东西。 */
internal interface NewsActions {
    /** Opens an article and marks it read if it was not. / 打开文章，未读时标为已读。 */
    fun open(article: StoredArticle)
    fun setRead(id: Long, read: Boolean)
    fun setStarred(id: Long, starred: Boolean)

    /** Throws an [AppError] when the summarizer is not set up or fails. / 摘要未设置或失败时抛出 [AppError]。 */
    suspend fun summarize(article: StoredArticle, language: String)

    /** Fetches and saves the article's full text in the background. / 在后台抓取并保存文章全文。 */
    fun fetchFullText(article: StoredArticle)
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
 * The tab, laid out like the original app: All / Unread / Starred with their counts, a search over titles, sources and text,
 * and one card per article; or one article with its AI summary. Back from an article returns to the list.
 * 标签页，沿用原 App 的布局：带数量的「全部 / 未读 / 收藏」、搜索标题、来源与正文的搜索框、每篇文章一张卡片；或者一篇文章及其
 * AI 摘要。从文章按返回键回到列表。
 */
@Composable
internal fun NewsTab(
    configured: Boolean,
    filter: ArticleFilter,
    onFilter: (ArticleFilter) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    articles: List<ArticleItem>?,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!configured) {
            item { InfoCard(title = stringResource(R.string.news_title), icon = R.drawable.ic_news) { Hint(stringResource(R.string.news_tab_setup)) } }
            return@LazyColumn
        }
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton(filter == ArticleFilter.ALL, stringResource(R.string.news_filter_all), counts?.total) { onFilter(ArticleFilter.ALL) }
                FilterButton(filter == ArticleFilter.UNREAD, stringResource(R.string.news_filter_unread), counts?.unread) { onFilter(ArticleFilter.UNREAD) }
                FilterButton(filter == ArticleFilter.STARRED, stringResource(R.string.news_filter_starred), counts?.starred, star = true) {
                    onFilter(ArticleFilter.STARRED)
                }
            }
        }
        item(key = "search") { SearchField(query, onQuery) }
        if (error != null) item(key = "error") { ErrorLine(error) }
        when {
            articles == null -> item { SkeletonLines(lines = 4) }
            articles.isEmpty() -> item {
                Hint(
                    stringResource(
                        when {
                            query.isNotBlank() -> R.string.news_no_matches
                            filter == ArticleFilter.UNREAD -> R.string.news_all_read
                            else -> R.string.news_none
                        },
                    ),
                )
            }
            else -> items(articles, key = { it.article.id }) { item ->
                ArticleCard(item, now, onClick = { actions.open(item.article) }) { actions.setStarred(item.article.id, !item.article.starred) }
            }
        }
    }
}

/** A filter with its count, filled when chosen. / 带数量的筛选按钮，选中时填充。 */
@Composable
private fun FilterButton(selected: Boolean, label: String, count: Int?, star: Boolean = false, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (count != null) "$label $count" else label, style = MaterialTheme.typography.titleSmall) },
        leadingIcon = if (star) {
            { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

/**
 * A compact 44dp search bar; Material's outlined field with its floating label is too tall above a list.
 * 紧凑的 44dp 搜索条；Material 带浮动标签的输入框放在列表上方太高。
 */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val hint = stringResource(R.string.news_search_hint)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(MaterialTheme.appSurfaces.card, MaterialTheme.shapes.small)
            .padding(start = 12.dp),
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = hint },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.news_search_clear), modifier = Modifier.size(18.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * One article: an unread dot and the title, a star to toggle at the top right, a preview, then source, time and whether its
 * full text is saved. Read articles fall back to the secondary text colour rather than fading, so small text keeps its
 * contrast.
 * 一篇文章：未读圆点与标题，右上角可切换的星标，一段预览，然后是来源、时间以及全文是否已缓存。已读文章的文字退成次要色而不是
 * 整体变淡，小字的对比度才够。
 */
@Composable
private fun ArticleCard(item: ArticleItem, now: ZonedDateTime, onClick: () -> Unit, onToggleStar: () -> Unit) {
    val article = item.article
    val read = article.read
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.appSurfaces.card, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(Modifier.padding(end = 32.dp)) {
                if (!read) {
                    Box(
                        Modifier
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.preview.isNotBlank()) {
                Text(
                    item.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(
                    article.feedTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                Text(moment(article.publishedAt, now), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                if (item.fullText) {
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.news_full_text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.statusColors.amber, maxLines = 1)
                }
            }
        }
        // Floats over the corner so its 48dp touch target does not make the title row taller. / 浮在角上，48dp 的触控区不会把标题行撑高。
        IconButton(onClick = onToggleStar, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(if (article.starred) R.string.news_unstar else R.string.news_star),
                tint = if (article.starred) MaterialTheme.statusColors.amber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Today's articles by their time, older ones with the date too. / 今天的文章只写时间，更早的加上日期。 */
@Composable
private fun moment(millis: Long, now: ZonedDateTime): String {
    val at = Instant.ofEpochMilli(millis).atZone(now.zone)
    val time = at.format(rememberTimeFormatter())
    return if (at.toLocalDate() == now.toLocalDate()) time else at.format(rememberPatternFormatter("MMMd")) + " " + time
}

@Composable
private fun ArticleDetail(article: StoredArticle, now: ZonedDateTime, onClose: () -> Unit, actions: NewsActions) {
    val uri = LocalUriHandler.current
    val language = currentLocale().language
    val scope = rememberCoroutineScope()
    var summarizing by remember(article.id) { mutableStateOf(false) }
    var summaryError by remember(article.id) { mutableStateOf<AppError?>(null) }
    val content = remember(article.contentHtml) { AnnotatedString.fromHtml(article.contentHtml) }
    // An excerpt asks for the full text once when opened; the text updates as soon as it is saved. / 只有摘要的文章打开时请求一次全文，保存后正文随即更新。
    LaunchedEffect(article.id) { if (NewsSyncPolicy.isStub(article.contentHtml)) actions.fetchFullText(article) }
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
