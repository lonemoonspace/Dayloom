package io.github.lonemoonspace.dayloom.feature.news.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.ui.SecretInput
import io.github.lonemoonspace.dayloom.feature.news.NewsSettings
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy

/**
 * Miniflux server and token, then the optional summarizer: endpoint, model and key. Addresses must be https, because the
 * token would otherwise travel in clear text.
 * Miniflux 服务器与令牌，然后是可选的摘要设置：接口地址、模型与 Key。地址必须是 https，否则令牌会明文传输。
 */
@Composable
internal fun NewsSettingsSection(
    saved: NewsSettings,
    token: SecretState,
    llmKey: SecretState,
    saveToken: (String) -> Unit,
    saveLlmKey: (String) -> Unit,
    update: ((NewsSettings) -> NewsSettings) -> Unit,
) {
    Hint(stringResource(R.string.news_server_note))
    UrlField(stringResource(R.string.news_server_url), saved.serverUrl) { url -> update { it.copy(serverUrl = url) } }
    SecretInput(stringResource(R.string.news_token), token, saveToken)

    Text(stringResource(R.string.news_llm_section), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    Hint(stringResource(R.string.news_llm_note))
    UrlField(stringResource(R.string.news_llm_url), saved.llmUrl) { url -> update { it.copy(llmUrl = url) } }
    var model by rememberSaveable(saved.llmModel) { mutableStateOf(saved.llmModel) }
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        singleLine = true,
        label = { Text(stringResource(R.string.news_llm_model)) },
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { update { it.copy(llmModel = model.trim()) } }, enabled = model.trim() != saved.llmModel) {
        Text(stringResource(R.string.common_save))
    }
    SecretInput(stringResource(R.string.news_llm_key), llmKey, saveLlmKey)
}

/** An address field that only saves https addresses. / 只保存 https 地址的输入框。 */
@Composable
private fun UrlField(label: String, saved: String, onSave: (String) -> Unit) {
    var input by rememberSaveable(saved) { mutableStateOf(saved) }
    val normalized = NewsSyncPolicy.normalizeBaseUrl(input)
    val invalid = input.isNotBlank() && normalized == null
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        singleLine = true,
        label = { Text(label) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(R.string.news_url_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(onClick = { onSave(normalized.orEmpty()) }, enabled = !invalid && normalized.orEmpty() != saved) {
        Text(stringResource(R.string.common_save))
    }
}
