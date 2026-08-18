package de.uniwuerzburg.omosim.io

import de.uniwuerzburg.omosim.io.gtfs.clipGTFSFile
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Envelope
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.collections.associateBy
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class GTFSKtTest {
    val tmpDirName = "omosim_cache"
    val tmpDir: Path = Paths.get(tmpDirName)
    val bbox_germany_small = Envelope(49.787, 49.79,9.92, 9.93)
    val bbox_korea_small = Envelope(36.3435, 36.345, 127.38,127.40)

    init {
        Files.createDirectories(tmpDir)
    }

    @Test
    fun clipGTFSGermanTest(){
        val input = Paths.get(javaClass.getResource("/clippedGTFSGermanyBig")!!.toURI())
        val expectedFolder = Paths.get(javaClass.getResource("/clippedGTFSGermanySmall")!!.toURI())

        val outputBaseDir = tmpDir.resolve("test-output")
        Files.createDirectories(tmpDir)
        val actualClippedFolder = outputBaseDir.resolve("clippedGTFS")
        clipGTFSTest(input, expectedFolder, actualClippedFolder, bbox_germany_small)
    }

    @Test
    fun clipGTFSKoreanTest(){
        val input = Paths.get(javaClass.getResource("/clippedGTFSKoreaBig")!!.toURI())
        val expectedFolder = Paths.get(javaClass.getResource("/clippedGTFSKoreaSmall")!!.toURI())
        val outputBaseDir = tmpDir.resolve("test-output")
        Files.createDirectories(tmpDir)
        val actualClippedFolder = outputBaseDir.resolve("clippedGTFS")
        clipGTFSTest(input, expectedFolder, actualClippedFolder, bbox_korea_small)
    }

    @OptIn(ExperimentalPathApi::class)
    fun clipGTFSTest(
        input: Path,
        expectedFolder: Path,
        actualClippedFolder: Path,
        bbBox: Envelope
    ) {
        if (actualClippedFolder.exists()) actualClippedFolder.deleteRecursively()
        val dispatcher = Dispatchers.Default.limitedParallelism(1)
        try {
            clipGTFSFile(
                bbBox,
                input,
                actualClippedFolder,
                dispatcher
            )

            val files1 = actualClippedFolder.listDirectoryEntries("*.txt").associateBy { it.name }
            if (files1.isEmpty()) {
                error("No .txt files found in clipped output")
            }
            val files2 = expectedFolder.listDirectoryEntries("*.txt").associateBy { it.name }
            if (files2.isEmpty()) {
                error("No .txt files found in clipped output")
            }

            assertEquals(files2.keys, files1.keys, "File names differ")

            for (fileName in files1.keys) {
                val actual = files1[fileName]!!.readText()
                    .replace("\r\n", "\n")
                    .split("\n").sorted().joinToString("\n")
                val expected = files2[fileName]!!.readText()
                    .replace("\r\n", "\n")
                    .split("\n").sorted().joinToString("\n")

                assertEquals(expected, actual, "Mismatch in file: $fileName")
            }
        } finally {
            // Clean up after test
            if (actualClippedFolder.exists()) actualClippedFolder.deleteRecursively()
        }
    }
}