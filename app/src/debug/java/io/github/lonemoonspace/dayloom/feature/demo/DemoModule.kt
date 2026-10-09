package io.github.lonemoonspace.dayloom.feature.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.i18n.uiText
import io.github.lonemoonspace.dayloom.core.module.FeatureModule
import io.github.lonemoonspace.dayloom.core.module.HomeCard
import io.github.lonemoonspace.dayloom.core.module.ModuleContext
import io.github.lonemoonspace.dayloom.core.module.ModuleInstance
import io.github.lonemoonspace.dayloom.core.module.ModuleTab
import io.github.lonemoonspace.dayloom.core.module.SettingsSection
import io.github.lonemoonspace.dayloom.core.refresh.CachedSource
import io.github.lonemoonspace.dayloom.core.refresh.RefreshCadence
import io.github.lonemoonspace.dayloom.core.refresh.SourceInput
import io.github.lonemoonspace.dayloom.core.refresh.Trigger
import io.github.lonemoonspace.dayloom.core.storage.Snapshot
import io.github.lonemoonspace.dayloom.core.storage.ValueStore
import io.github.lonemoonspace.dayloom.core.time.AppClock
import io.github.lonemoonspace.dayloom.core.ui.InfoCard
import io.github.lonemoonspace.dayloom.core.ui.LocalAppClock
import io.github.lonemoonspace.dayloom.core.ui.SkeletonLines
import io.github.lonemoonspace.dayloom.core.ui.plusBars
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Debug-only module with no network: a settings field, one cached source, a home card, a tab and a settings section.
 * It is the living example of §4.4 of the design: it only touches its own package and one registry line.
 * 仅 Debug 版、不联网的模块：一个设置项、一个带缓存的来源、一张首页卡片、一个标签页与一个设置分区。
 * 它是设计文档 §4.4 的活例子：只动了自己的包和注册表里的一行。
 */
object DemoModule : FeatureModule {
    override val id = "demo"
    override val title = R.string.demo_title
    override val summary = R.string.demo_summary
    override val icon = R.drawable.ic_demo
    override val defaultEnabled = true
    override val notificationIds = 9_000..9_099

    override fun create(ctx: ModuleContext): ModuleInstance = DemoInstance(ctx)
}

@Serializable
data class DemoSettings(val name: String = "")

@Serializable
data class DemoData(val name: String = "", val count: Int = 0)

private class DemoSource(ctx: ModuleContext, settings: ValueStore<DemoSettings>, clock: AppClock) :
    CachedSource<String, DemoData>(ctx.sourceId("greeting"), ctx.snapshots(ctx.sourceId("greeting"), DemoData.serializer()), clock) {

    override val schemaVersion = 1
    override val maxAge: Duration = Duration.ofMinutes(5)
    override val cadence = RefreshCadence(interval = Duration.ofMinutes(15))

    override val inputs: Flow<SourceInput<String>> = settings.flow.map { s ->
        val name = s.name.trim()
        if (name.isEmpty()) SourceInput.Missing(uiText(R.string.demo_name_label)) else SourceInput.Ready(name, key = name)
    }

    override suspend fun fetch(params: String, now: ZonedDateTime, previous: Snapshot<DemoData>?): DemoData {
        // Pretend to be a network call so the refreshing state is visible. / 假装是一次网络请求，好让「刷新中」看得见。
        delay(600)
        return DemoData(name = params, count = (previous?.value?.count ?: 0) + 1)
    }
}

private class DemoInstance(private val ctx: ModuleContext) : ModuleInstance {
    private val settingsStore = ctx.settings(DemoSettings.serializer(), DemoSettings())
    private val source = DemoSource(ctx, settingsStore, ctx.clock)

    override val sources = listOf(source)

    override val homeCards = listOf(
        HomeCard(key = "greeting", title = R.string.demo_title, defaultOrder = 1000) { DemoCard() },
    )

    override val tab = ModuleTab(label = R.string.demo_title, icon = R.drawable.ic_demo) { DemoTab() }

    override val settings = SettingsSection { DemoSettingsSection() }

    @Composable
    private fun DemoCard() {
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        val status by ctx.coordinator.status.collectAsStateWithLifecycle()
        val error = status[source.id]?.lastError
        val clock = LocalAppClock.current
        InfoCard(
            title = stringResource(R.string.demo_title),
            stale = snapshot?.let { source.isStale(it, clock.instant()) } == true,
        ) {
            val data = snapshot
            when {
                data != null -> DemoText(data)
                error == null -> SkeletonLines()
                else -> Unit
            }
            if (error != null) {
                Text(error.userMessage().asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun DemoText(snapshot: Snapshot<DemoData>) {
        val clock = LocalAppClock.current
        val at = Instant.ofEpochMilli(snapshot.fetchedAt).atZone(clock.zone())
        Text(stringResource(R.string.demo_greeting, snapshot.value.name), style = MaterialTheme.typography.titleMedium)
        Text(
            pluralStringResource(
                R.plurals.demo_count,
                snapshot.value.count,
                snapshot.value.count,
                at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    @Composable
    private fun DemoTab() {
        val scope = rememberCoroutineScope()
        val snapshot by source.observe().collectAsStateWithLifecycle(initialValue = null)
        Column(
            Modifier
                .fillMaxSize()
                .padding(androidx.compose.foundation.layout.PaddingValues(16.dp).plusBars(top = true)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.demo_tab_body), style = MaterialTheme.typography.bodyLarge)
            snapshot?.let { DemoText(it) }
            Button(onClick = { scope.launch { ctx.coordinator.refresh(setOf(source.id), Trigger.USER) } }) {
                Text(stringResource(R.string.demo_refresh_now))
            }
        }
    }

    @Composable
    private fun DemoSettingsSection() {
        val saved by settingsStore.flow.collectAsStateWithLifecycle(initialValue = DemoSettings())
        var input by rememberSaveable(saved.name) { mutableStateOf(saved.name) }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            singleLine = true,
            label = { Text(stringResource(R.string.demo_name_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Saved in appScope, not the screen's scope: leaving the screen right after tapping Save must not cancel the write.
        // 在 appScope 而不是页面作用域里保存：点完保存立刻离开页面，也不能取消这次写入。
        TextButton(onClick = { ctx.appScope.launch { settingsStore.update { it.copy(name = input.trim()) } } }) {
            Text(stringResource(R.string.common_save))
        }
    }
}
