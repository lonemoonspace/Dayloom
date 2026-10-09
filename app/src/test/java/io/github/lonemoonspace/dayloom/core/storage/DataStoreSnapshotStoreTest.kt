package io.github.lonemoonspace.dayloom.core.storage

import java.io.File
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** DataStore does not need Android, so these run on the plain JVM with temp files. / DataStore 不依赖 Android，直接用临时文件跑纯 JVM 测试。 */
class DataStoreSnapshotStoreTest {

    @Serializable
    data class Sample(val text: String, val count: Int = 0)

    @get:Rule
    val tmp = TemporaryFolder()

    private val jobs = mutableListOf<CompletableJob>()

    @After
    fun tearDown() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    private fun file(): File = File(tmp.root, "snapshot_test_sample")

    private fun newStore(file: File): Pair<DataStoreSnapshotStore<Sample>, CompletableJob> {
        val job = SupervisorJob().also(jobs::add)
        return DataStoreSnapshotStore(Sample.serializer(), CoroutineScope(Dispatchers.IO + job)) { file } to job
    }

    private fun snapshot(text: String, fetchedAt: Long = 1_000L) =
        Snapshot(Sample(text, 1), fetchedAt = fetchedAt, paramsKey = "k", schema = 1)

    @Test
    fun `missing file reads as null`() = runBlocking {
        val (store, _) = newStore(file())
        assertNull(store.read())
        assertNull(store.flow.first())
    }

    @Test
    fun `a written snapshot survives a new store instance on the same file`() = runBlocking {
        val f = file()
        val (first, job) = newStore(f)
        first.write(snapshot("a"))
        assertEquals(snapshot("a"), first.read())
        // Only one active DataStore per file: close the first before opening the second. / 同一文件同时只能有一个活跃 DataStore。
        job.cancelAndJoin()

        val (second, _) = newStore(f)
        assertEquals(snapshot("a"), second.read())
    }

    @Test
    fun `garbage content decodes to null instead of throwing, and can be overwritten`() = runBlocking {
        val f = file().apply { writeText("{ this is not json") }
        val (store, _) = newStore(f)

        assertNull(store.read())
        store.write(snapshot("fresh"))
        assertEquals(snapshot("fresh"), store.read())
    }

    @Test
    fun `json of an incompatible shape decodes to null`() = runBlocking {
        val f = file().apply { writeText("""{"snapshot":{"temperature":3.0}}""") }
        val (store, _) = newStore(f)
        assertNull(store.read())
    }

    @Test
    fun `clear empties the store`() = runBlocking {
        val (store, _) = newStore(file())
        store.write(snapshot("a"))
        store.clear()
        assertNull(store.read())
    }

    @Test
    fun `flow emits the newly written snapshot`() = runBlocking {
        val (store, _) = newStore(file())
        assertNull(store.flow.first())
        store.write(snapshot("b", fetchedAt = 2_000L))
        assertEquals(snapshot("b", fetchedAt = 2_000L), store.flow.filterNotNull().first())
    }

    /** A read failure only shows "no cache" briefly; snapshots written after recovery still arrive. / 读失败只短暂显示「无缓存」，恢复后写入的快照照样送达。 */
    @Test
    fun `flow survives a read failure and delivers snapshots written after recovery`() = runBlocking {
        // A directory at the path makes reads throw IOException; deleting it "recovers". / 路径上放目录让读取抛 IOException，删掉即恢复。
        val f = file().also { it.mkdirs() }
        val (store, _) = newStore(f)
        val seen = Channel<Snapshot<Sample>?>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) { store.flow.collect { seen.send(it) } }

        assertNull(withTimeout(10_000) { seen.receive() })

        assertTrue(f.delete())
        store.write(snapshot("after", fetchedAt = 3_000L))

        var received: Snapshot<Sample>? = null
        withTimeout(10_000) {
            while (received == null) received = seen.receive()
        }
        assertEquals(snapshot("after", fetchedAt = 3_000L), received)
        collector.cancelAndJoin()
    }
}
