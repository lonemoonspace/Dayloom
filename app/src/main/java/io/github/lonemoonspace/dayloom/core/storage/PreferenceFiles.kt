package io.github.lonemoonspace.dayloom.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// The Preferences files are declared only here: a file may have only one DataStore instance per process,
// and top-level delegates are process-wide singletons. File names are frozen from v1.0.0.
// Preferences 文件只在这里声明：同一文件在进程内只能有一个 DataStore 实例，顶层委托就是进程级单例。文件名从 v1.0.0 起冻结。

/** Per-module settings, see [ModuleSettingsStore]. / 各模块设置，见 [ModuleSettingsStore]。 */
val Context.moduleSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "module_settings")

/** Encrypted credentials, see `SecretStore`. / 加密的凭据，见 `SecretStore`。 */
val Context.secretsDataStore: DataStore<Preferences> by preferencesDataStore(name = "secrets")

/** Notification rule state. / 通知规则状态。 */
val Context.notifyStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "notify_state")
