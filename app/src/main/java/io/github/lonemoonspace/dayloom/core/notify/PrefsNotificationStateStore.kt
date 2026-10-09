package io.github.lonemoonspace.dayloom.core.notify

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

class PrefsNotificationStateStore(private val dataStore: DataStore<Preferences>) : NotificationStateStore {
    override suspend fun read(key: String): String? = dataStore.data.first()[stringPreferencesKey(key)]

    override suspend fun write(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }
}
