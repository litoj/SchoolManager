package cz.litoj.schlmgr.unit.ui.viewmodel

import cz.litoj.schlmgr.ui.viewmodel.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStateTest {

	@Test
	fun `digitsOnly keeps digits and drops everything else`() {
		assertEquals("", SettingsState.digitsOnly(""))
		assertEquals("18", SettingsState.digitsOnly("18"))
		assertEquals("18", SettingsState.digitsOnly("1a8"))
		assertEquals("", SettingsState.digitsOnly("abc"))
		assertEquals("12", SettingsState.digitsOnly("-12"))
	}

	@Test
	fun `needsRestore is false only for a usable amount`() {
		assertFalse(SettingsState.needsRestore("1"))
		assertFalse(SettingsState.needsRestore("18"))
	}

	@Test
	fun `needsRestore is true for empty zero and unparsable input`() {
		assertTrue(SettingsState.needsRestore(""))
		assertTrue(SettingsState.needsRestore("0"))
		assertTrue(SettingsState.needsRestore("-3"))
		assertTrue(SettingsState.needsRestore("abc"))
		assertTrue(SettingsState.needsRestore("2147483648"))
	}
}
