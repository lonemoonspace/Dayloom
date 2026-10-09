package io.github.lonemoonspace.dayloom.core.storage

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoresTest {

    @Serializable
    data class WeatherSettings(val place: String = "", val hours: Int = 6)

    @Serializable
    data class OtherSettings(val flag: Boolean = false)

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    private val prefs by lazy { PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "module_settings.preferences_pb") } }

    @Test
    fun `module settings are stored as JSON under the module id and read back`() = runBlocking {
        val errors = mutableListOf<String>()
        val store = ModuleSettingsStore(prefs) { id, _ -> errors += id }
        val weather = store.forModule("weather", WeatherSettings.serializer(), WeatherSettings())

        assertEquals(WeatherSettings(), weather.get())
        weather.update { it.copy(place = "Oslo") }

        assertEquals(WeatherSettings(place = "Oslo"), weather.get())
        assertEquals("""{"place":"Oslo","hours":6}""", prefs.data.first()[stringPreferencesKey("weather")])
        assertEquals(emptyList<String>(), errors)
    }

    @Test
    fun `modules do not see each other's settings`() = runBlocking {
        val store = ModuleSettingsStore(prefs)
        val weather = store.forModule("weather", WeatherSettings.serializer(), WeatherSettings())
        val other = store.forModule("other", OtherSettings.serializer(), OtherSettings())

        weather.update { it.copy(hours = 3) }
        other.update { it.copy(flag = true) }

        assertEquals(WeatherSettings(hours = 3), weather.get())
        assertEquals(OtherSettings(flag = true), other.get())
    }

    @Test
    fun `a broken entry falls back to the defaults and is reported`() = runBlocking {
        prefs.edit { it[stringPreferencesKey("weather")] = "{ broken" }
        val errors = mutableListOf<String>()
        val weather = ModuleSettingsStore(prefs) { id, _ -> errors += id }
            .forModule("weather", WeatherSettings.serializer(), WeatherSettings())

        assertEquals(WeatherSettings(), weather.get())
        assertEquals(listOf("weather"), errors)
    }

    @Test
    fun `unknown and missing fields decode with defaults`() = runBlocking {
        prefs.edit { it[stringPreferencesKey("weather")] = """{"place":"Bergen","removedField":1}""" }
        val weather = ModuleSettingsStore(prefs).forModule("weather", WeatherSettings.serializer(), WeatherSettings())
        assertEquals(WeatherSettings(place = "Bergen"), weather.get())
    }

    @Test
    fun `app settings decode from older JSON and fall back to defaults when corrupt`() = runBlocking {
        val good = File(tmp.root, "app_settings").apply { writeText("""{"timeZoneOverride":"Asia/Shanghai"}""") }
        val errors = mutableListOf<Throwable>()
        val store = AppStores.jsonValueStore(AppSettings.serializer(), AppSettings(), scope, { errors += it }) { good }
        assertEquals(AppSettings(timeZoneOverride = "Asia/Shanghai"), store.get())

        val bad = File(tmp.root, "app_settings_bad").apply { writeText("not json") }
        val broken = AppStores.jsonValueStore(AppSettings.serializer(), AppSettings(), scope, { errors += it }) { bad }
        assertEquals(AppSettings(), broken.get())
        assertEquals(1, errors.size)
    }
}
