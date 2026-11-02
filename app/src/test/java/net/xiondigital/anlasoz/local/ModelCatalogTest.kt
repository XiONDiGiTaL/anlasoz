package net.xiondigital.anlasoz.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalog is what the Settings screen offers, so a typo here becomes a
 * download that cannot be verified or a file the engine cannot open.
 */
class ModelCatalogTest {

    @Test
    fun `every entry is a downloadable gguf over https`() {
        assertTrue(ModelCatalog.downloadable.isNotEmpty())
        ModelCatalog.downloadable.forEach { source ->
            assertTrue("${source.id} must name a .gguf file", source.fileName.endsWith(".gguf"))
            assertTrue("${source.id} must use https", source.url.startsWith("https://"))
            assertTrue("${source.id} needs a note", source.note.isNotBlank())
        }
    }

    @Test
    fun `publisher figures are either absent or complete`() {
        ModelCatalog.downloadable.forEach { source ->
            if (source.bytes != null) {
                assertTrue("${source.id} bytes must be plausible", source.bytes!! > 100_000_000L)
            }
            if (source.sha256 != null) {
                assertEquals("${source.id} hash must be 64 hex chars", 64, source.sha256!!.length)
                assertTrue(source.sha256!!.all { it in "0123456789abcdef" })
            }
        }
    }

    @Test
    fun `exactly one model is recommended`() {
        val recommended = ModelCatalog.downloadable.filter { it.recommended }
        assertEquals(1, recommended.size)
        assertEquals(ModelCatalog.vexiumCoreLight.id, recommended.single().id)
    }

    @Test
    fun `lookup by id and file name agree`() {
        ModelCatalog.downloadable.forEach { source ->
            assertEquals(source, ModelCatalog.byId(source.id))
            assertEquals(source, ModelCatalog.byFileName(source.fileName))
        }
        assertEquals(null, ModelCatalog.byId("not-a-model"))
    }

    @Test
    fun `sizes are formatted the way the settings card shows them`() {
        assertEquals("1.12 GB", ModelCatalog.formatBytes(1_117_320_736L))
        assertEquals("491 MB", ModelCatalog.formatBytes(491_400_032L))
        assertEquals("unknown size", ModelCatalog.formatBytes(0L))
    }
}
