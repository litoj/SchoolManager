package cz.litoj.schlmgr.unit.ui.component

import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.ui.component.CrumbSize
import cz.litoj.schlmgr.ui.component.breadcrumbHeight
import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbHeightTest {

	@Test
	fun `the three breadcrumb looks use literal height presets`() {
		assertEquals(25.dp, breadcrumbHeight(CrumbSize.COMPACT))
		assertEquals(25.dp, breadcrumbHeight(CrumbSize.NORMAL))
		assertEquals(40.dp, breadcrumbHeight(CrumbSize.SPACIOUS))
	}
}
