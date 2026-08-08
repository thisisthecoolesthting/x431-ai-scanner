package com.caseforge.scanner.transfer

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class VehicleDatabaseZipperTest {

    @Test
    fun streamsZipWithVehicleDatabasePrefix() = runBlocking {
        val root = File.createTempFile("vehicle-db-root", null)
        root.delete()
        root.mkdirs()
        File(root, "nested/a.txt").apply { parentFile?.mkdirs(); writeText("hello") }

        val zipper = VehicleDatabaseZipper(root)
        val out = ByteArrayOutputStream()
        val progress = zipper.zipProgressFlow(out).toList()
        assertTrue(progress.isNotEmpty())

        val names = mutableListOf<String>()
        ZipInputStream(out.toByteArray().inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zis.nextEntry
            }
        }
        assertTrue(names.any { it == "vehicle-database/nested/a.txt" })
        val payload = ZipInputStream(out.toByteArray().inputStream()).use {
            checkNotNull(it.nextEntry)
            it.readBytes().decodeToString()
        }
        assertEquals("hello", payload)
        root.deleteRecursively()
    }
}
