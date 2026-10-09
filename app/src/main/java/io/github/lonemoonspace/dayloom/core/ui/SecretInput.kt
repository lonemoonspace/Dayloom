package io.github.lonemoonspace.dayloom.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import io.github.lonemoonspace.dayloom.R
import io.github.lonemoonspace.dayloom.core.secret.SecretField
import io.github.lonemoonspace.dayloom.core.secret.SecretState

/**
 * Input for one credential. It only ever shows [SecretState.display] (never ciphertext), hides the value unless asked, and
 * refuses a pasted `v1:` ciphertext, which could never be decrypted. A blank save deletes the credential.
 * 单个凭据的输入框。只回填 [SecretState.display]（永不显示密文），默认隐藏内容，拒绝粘贴进来的 `v1:` 密文（那永远解不开）。
 * 保存空白即删除凭据。
 */
@Composable
fun SecretInput(label: String, state: SecretState, onSave: (String) -> Unit) {
    var input by rememberSaveable(state.display) { mutableStateOf(state.display) }
    var visible by rememberSaveable { mutableStateOf(false) }
    val rejected = SecretField.isCiphertext(input.trim())
    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = rejected,
        supportingText = when {
            rejected -> { { Text(stringResource(R.string.secret_ciphertext_rejected)) } }
            state.unreadable -> { { Text(stringResource(R.string.secret_unreadable_hint)) } }
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row {
        TextButton(onClick = { visible = !visible }) {
            Text(stringResource(if (visible) R.string.secret_hide else R.string.secret_show))
        }
        TextButton(onClick = { onSave(input.trim()) }, enabled = !rejected && input.trim() != state.display) {
            Text(stringResource(R.string.common_save))
        }
    }
    if (state.isSet && !state.unreadable) {
        Text(
            text = stringResource(R.string.secret_saved_encrypted),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
