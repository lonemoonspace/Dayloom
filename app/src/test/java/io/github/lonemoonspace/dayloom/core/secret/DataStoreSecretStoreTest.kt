package io.github.lonemoonspace.dayloom.core.secret

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Ciphertext on disk, empty usable value when decryption fails, and display never returning ciphertext.
 * 落盘的是密文、解密失败时 usable 为空、display 永不返回密文。
 */
class DataStoreSecretStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cipher = FakeCipher()
    private val dataStore by lazy { PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "secrets.preferences_pb") } }
    private val store by lazy { DataStoreSecretStore(dataStore, cipher) }

    @After
    fun tearDown() = scope.cancel()

    private suspend fun raw(id: String): String? = dataStore.data.first()[stringPreferencesKey(id)]

    @Test
    fun `put stores ciphertext and reads back the plaintext`() = runBlocking {
        store.put("traffic.google_maps", "g-key")

        val stored = raw("traffic.google_maps")!!
        assertTrue(SecretField.isCiphertext(stored))
        assertFalse(stored.contains("g-key"))
        assertEquals("g-key", store.usable("traffic.google_maps"))
        assertEquals(SecretState("g-key", isSet = true, unreadable = false), store.current("traffic.google_maps"))
    }

    @Test
    fun `an undecryptable value is set and unreadable, usable and display are empty`() = runBlocking {
        val foreign = FakeCipher.foreignCiphertext("llm")
        dataStore.edit { it[stringPreferencesKey("news.llm_api_key")] = foreign }

        val state = store.current("news.llm_api_key")
        assertEquals("", store.usable("news.llm_api_key"))
        assertEquals("", state.display)
        assertTrue(state.isSet)
        assertTrue(state.unreadable)
        assertEquals("reading must not alter the ciphertext", foreign, raw("news.llm_api_key"))
    }

    @Test
    fun `putting a blank value removes the secret`() = runBlocking {
        store.put("news.miniflux_token", "token")
        store.put("news.miniflux_token", "  ")

        assertNull(raw("news.miniflux_token"))
        assertEquals(SecretState.EMPTY, store.current("news.miniflux_token"))
    }

    @Test
    fun `a failing encryption leaves the stored value untouched`() = runBlocking {
        store.put("football.football_data", "old")
        cipher.failEncrypt = true

        assertTrue(runCatching { store.put("football.football_data", "new") }.isFailure)
        assertEquals("old", store.usable("football.football_data"))
    }

    @Test
    fun `secret state never prints the plaintext`() {
        assertFalse(SecretState("hunter2", isSet = true, unreadable = false).toString().contains("hunter2"))
    }

    @Test
    fun `SecretField maps raw values`() {
        assertEquals(SecretState.EMPTY, SecretField.read("", cipher::decrypt))
        assertEquals(SecretState("plain", true, false), SecretField.read("plain", cipher::decrypt))
        assertEquals(SecretState("k", true, false), SecretField.read(FakeCipher.ciphertextOf("k"), cipher::decrypt))
        assertEquals(SecretState("", true, true), SecretField.read(FakeCipher.foreignCiphertext("x"), cipher::decrypt))
        assertTrue(SecretField.isCiphertext("v1:abc"))
    }
}
