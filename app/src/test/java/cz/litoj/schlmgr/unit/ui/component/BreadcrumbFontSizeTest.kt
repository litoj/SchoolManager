package cz.litoj.schlmgr.unit.ui.component

import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.ui.component.CrumbSize
import cz.litoj.schlmgr.ui.component.breadcrumbFontSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbFontSizeTest {

	@Test
	fun `normal and spacious share the header font, so more height never means bigger text`() {
		assertEquals(17.sp, breadcrumbFontSize(CrumbSize.COMPACT))
		assertEquals(20.sp, breadcrumbFontSize(CrumbSize.NORMAL))
		assertEquals(breadcrumbFontSize(CrumbSize.NORMAL), breadcrumbFontSize(CrumbSize.SPACIOUS))
	}
}
