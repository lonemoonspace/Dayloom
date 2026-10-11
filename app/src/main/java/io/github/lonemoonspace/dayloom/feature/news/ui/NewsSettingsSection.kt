package io.github.lonemoonspace.dayloom.feature.news.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.error.AppError
import io.github.lonemoonspace.dayloom.core.i18n.asString
import io.github.lonemoonspace.dayloom.core.secret.SecretField
import io.github.lonemoonspace.dayloom.core.secret.SecretState
import io.github.lonemoonspace.dayloom.core.ui.SecretInput
import io.github.lonemoonspace.dayloom.core.ui.theme.statusColors
import io.github.lonemoonspace.dayloom.core.ui.userMessage
import io.github.lonemoonspace.dayloom.feature.news.NewsSettings
import io.github.lonemoonspace.dayloom.feature.news.domain.NewsSyncPolicy
import kotlinx.coroutines.launch

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
    connect: suspend (url: String, token: String?) -> ConnectResult,
    saveLlmKey: (String) -> Unit,
    update: ((NewsSettings) -> NewsSettings) -> Unit,
) {
    Hint(stringResource(R.string.news_server_note))
    ServerForm(saved.serverUrl, token, connect)

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

/** What "Save and connect" found. / 「保存并连接」的结果。 */
internal sealed interface ConnectResult {
    data class Connected(val unread: Int) : ConnectResult
    data class Failed(val error: AppError) : ConnectResult
}

/**
 * Server and token saved together and tried at once: with two separate save buttons it was easy to save only one, and
 * nothing said whether the server could be reached.
 * 服务器与令牌一起保存并立即试连：原来有两个保存按钮，很容易只存了一个，而且没有任何提示说明服务器能不能连上。
 */
@Composable
private fun ServerForm(savedUrl: String, token: SecretState, connect: suspend (url: String, token: String?) -> ConnectResult) {
    var url by rememberSaveable(savedUrl) { mutableStateOf(savedUrl) }
    var tokenInput by rememberSaveable(token.display) { mutableStateOf(token.display) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ConnectResult?>(null) }
    val scope = rememberCoroutineScope()
    val normalized = NewsSyncPolicy.normalizeBaseUrl(url)
    val urlProblem = when {
        url.isBlank() -> null
        NewsSyncPolicy.isPlainHttp(url) -> R.string.news_url_http
        normalized == null -> R.string.news_url_invalid
        else -> null
    }
    val tokenRejected = SecretField.isCiphertext(tokenInput.trim())
    OutlinedTextField(
        value = url,
        onValueChange = { url = it; result = null },
        singleLine = true,
        label = { Text(stringResource(R.string.news_server_url)) },
        isError = urlProblem != null,
        supportingText = urlProblem?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = tokenInput,
        onValueChange = { tokenInput = it; result = null },
        singleLine = true,
        label = { Text(stringResource(R.string.news_token)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = tokenRejected,
        supportingText = when {
            tokenRejected -> { { Text(stringResource(R.string.secret_ciphertext_rejected)) } }
            token.unreadable -> { { Text(stringResource(R.string.secret_unreadable_hint)) } }
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.secret_hide else R.string.secret_show))
        }
        Spacer(Modifier.weight(1f))
        if (working) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        Button(
            onClick = {
                val base = normalized ?: return@Button
                // The field shows the masked display of a saved token; only a changed value is a new token.
                // 输入框里显示的是已存令牌的掩码；只有改过的值才是新令牌。
                val newToken = tokenInput.trim().takeIf { it != token.display }
                working = true
                result = null
                scope.launch {
                    result = connect(base, newToken)
                    working = false
                    url = base
                }
            },
            enabled = normalized != null && tokenInput.isNotBlank() && !tokenRejected && !working,
        ) { Text(stringResource(R.string.news_connect)) }
    }
    when (val r = result) {
        is ConnectResult.Connected -> Text(
            pluralStringResource(R.plurals.news_connected, r.unread, r.unread),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.statusColors.green,
        )
        is ConnectResult.Failed -> Text(
            r.error.userMessage().asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        null -> if (token.isSet && !token.unreadable) Hint(stringResource(R.string.secret_saved_encrypted))
    }
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
