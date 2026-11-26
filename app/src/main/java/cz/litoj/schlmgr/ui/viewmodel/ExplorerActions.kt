package cz.litoj.schlmgr.ui.viewmodel

import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.search.SearchTag

/** Typed identities and presentation metadata shared by list and selection menus. */
enum class ExplorerAction(
	val menuId: Int,
	val label: Int = 0,
	val icon: Int = 0,
	val description: Int = 0,
	val filterTag: SearchTag? = null,
) {
	NEW_SUBJECT(1),
	NEW_CHAPTER(2),
	NEW_WORD(3),
	NEW_NOTE(4),
	IMPORT_FILE(6),
	EXPORT(7, R.string.more_export, R.drawable.ic_export_black, R.string.setts_act_export),
	FLIP(9, R.string.action_flip, R.drawable.ic_flip_black, R.string.setts_act_flip),
	REFERENCE(10, R.string.action_reference, R.drawable.ic_reference_black, R.string.setts_act_reference),
	SORT_ALPHA_AZ(11, R.string.sort_alpha_AZ),
	SORT_ALPHA_ZA(12, R.string.sort_alpha_ZA),
	SORT_SF_01(13, R.string.sort_sf_01),
	SORT_SF_10(14, R.string.sort_sf_10),
	SORT_LENGTH_01(15, R.string.sort_length_01),
	SORT_LENGTH_10(16, R.string.sort_length_10),
	SORT_DEFAULT(17, R.string.sort_default),
	SORT_PRIORITY(19, R.string.sort_priority),
	TEST(20, R.string.action_add_to_test, R.drawable.ic_test, R.string.setts_act_test),
	CUT(21, R.string.cut, R.drawable.ic_move, R.string.setts_act_cut),
	EDIT(22, R.string.edit, R.drawable.ic_edit, R.string.setts_act_edit),
	GOTO(23, R.string.more_go_to, R.drawable.ic_goto, R.string.setts_act_goto),
	DELETE(24, R.string.delete, R.drawable.ic_delete, R.string.setts_act_delete),
	CLEAR_FILTER(25, R.string.clear_filter, R.drawable.ic_cancel),
	FILTER_NAME(26, filterTag = SearchTag.NAME),
	FILTER_TYPE(27, filterTag = SearchTag.TYPE),
	FILTER_RATE(28, filterTag = SearchTag.RATE),
	FILTER_TESTED(29, filterTag = SearchTag.TESTED),
	SELECT_ALL(30, R.string.select_all, R.drawable.ic_check_all),
	SORT_RANDOM(31, R.string.sort_random),
	;

	companion object {
		fun of(menuId: Int): ExplorerAction? = entries.firstOrNull { it.menuId == menuId }
	}
}
