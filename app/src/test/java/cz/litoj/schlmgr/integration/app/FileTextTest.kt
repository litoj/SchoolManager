package cz.litoj.schlmgr.integration.app

import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.app.FileText
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.data.Progress
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * - reads strip a UTF-8 byte order mark, so the first parsed name stays clean.
 * - reads count their bytes for the progress bar.
 * - writes round-trip multi-byte text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileTextTest {

	@Before
	fun setUp() {
		GlobalDependencies.appContext = RuntimeEnvironment.getApplication()
	}

	@Test
	fun `readText strips a UTF-8 byte order mark`() {
		val file = File(GlobalDependencies.appContext.cacheDir, "bom.txt")
		file.writeText("\uFEFFchapter {\nword;trl\n}")

		val content = FileText.readText(DocumentFile.fromFile(file).uri)

		// a BOM left in would prefix the first parsed name invisibly
		assertEquals("chapter {\nword;trl\n}", content)
	}

	@Test
	fun `readText reports the bytes it reads`() {
		val file = File(GlobalDependencies.appContext.cacheDir, "list.txt")
		val text = "chapter {\nword;trl\n}"
		file.writeText(text)
		var reported = 0

		val content = FileText.readText(
			DocumentFile.fromFile(file).uri, Progress { reported += it })

		assertEquals(text, content)
		assertEquals(file.length(), reported.toLong())
	}

	@Test
	fun `writeText round-trips multi-byte text`() {
		val target = File(GlobalDependencies.appContext.cacheDir, "out.txt")
		val text = buildString { repeat(4_000) { append("číše řecká;") } }

		assertTrue(FileText.writeText(DocumentFile.fromFile(target).uri, text))
		assertEquals(text, FileText.readText(DocumentFile.fromFile(target).uri))
	}
}
