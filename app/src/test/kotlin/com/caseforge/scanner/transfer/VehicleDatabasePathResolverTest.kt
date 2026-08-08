package com.caseforge.scanner.transfer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleDatabasePathResolverTest {

    @Test
    fun scanPrefersRootWithMostFiles() {
        val a = File.createTempFile("cn-a", null).apply { delete(); mkdirs() }
        val b = File.createTempFile("cn-b", null).apply { delete(); mkdirs() }
        File(b, "data.bin").writeBytes(ByteArray(100))
        val invA = VehicleDatabasePathResolver.Inventory(a, 0, 0, listOf(a.absolutePath))
        val invB = VehicleDatabasePathResolver.Inventory(b, 1, 100, listOf(b.absolutePath))
        assertTrue(invB.fileCount > invA.fileCount)
        a.deleteRecursively()
        b.deleteRecursively()
    }
}
