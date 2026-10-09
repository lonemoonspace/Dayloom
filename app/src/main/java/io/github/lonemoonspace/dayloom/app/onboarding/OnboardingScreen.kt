package io.github.lonemoonspace.dayloom.app.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.app.settings.HomePlaceCard
import io.github.lonemoonspace.dayloom.app.settings.LanguageCard
import io.github.lonemoonspace.dayloom.app.settings.ModulesCard
import io.github.lonemoonspace.dayloom.app.settings.PlaceEditor
import io.github.lonemoonspace.dayloom.app.settings.SettingsState
import io.github.lonemoonspace.dayloom.app.settings.SharedDataViewModel
import io.github.lonemoonspace.dayloom.core.i18n.LanguageChoice
import io.github.lonemoonspace.dayloom.core.location.Place

/**
 * First-run onboarding (design §13): language, Home, modules. Each step writes straight to the real settings, so leaving
 * halfway loses nothing and the same choices show up in Settings later. The step survives the activity being recreated by a
 * language switch.
 * 首次启动引导（设计文档 §13）：语言、家、模块。每一步都直接写入真正的设置，中途离开不丢任何选择，之后在设置页里看到的
 * 也是同样的内容。切换语言会重建 Activity，当前步骤会被保留。
 */
@Composable
fun OnboardingScreen(
    state: SettingsState,
    onLanguage: (LanguageChoice) -> Unit,
    onModuleEnabled: (String, Boolean) -> Unit,
    places: List<Place>,
    editor: PlaceEditor?,
    sharedVm: SharedDataViewModel,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    val homeSet = places.any { it.id == Place.HOME }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.onboarding_step, step + 1, STEPS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (step) {
                0 -> {
                    Heading(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_body))
                    LanguageCard(state.language, onLanguage)
                }
                1 -> {
                    Heading(stringResource(R.string.onboarding_home_title), stringResource(R.string.onboarding_home_body))
                    HomePlaceCard(places, editor, sharedVm)
                }
                else -> {
                    Heading(stringResource(R.string.onboarding_modules_title), stringResource(R.string.onboarding_modules_body))
                    ModulesCard(state.modules, onModuleEnabled)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
            Spacer(Modifier.weight(1f))
            when {
                step < STEPS - 1 -> {
                    // Home is optional; without it the button says so. / 「家」可以不设；没设时按钮直接写「跳过」。
                    val skip = step == 1 && !homeSet
                    Button(onClick = { step++ }) {
                        Text(stringResource(if (skip) R.string.onboarding_skip else R.string.onboarding_next))
                    }
                }
                else -> Button(onClick = onFinish) { Text(stringResource(R.string.onboarding_done)) }
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val STEPS = 3
