package cz.litoj.schlmgr.ui.shell

import android.content.Context
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.ui.popup.MessagePopup

/**
 * Renders [GlobalDependencies.messages] as snackbars, with a full text action.
 * - the action opens the [MessagePopup] dialog with the whole text.
 * - every activity composes it, so a worker-thread report shows on the foreground screen.
 * - the flow delivers only while it is collected.
 */
@Composable
fun messageSnackbarHost(): SnackbarHostState {
	val snackbar = remember { SnackbarHostState() }
	val ctx = LocalContext.current
	val fullTextLabel = stringResource(R.string.action_full_text)
	LaunchedEffect(snackbar) {
		GlobalDependencies.messages.collect { msg ->
			// notices resolve here at display time, never at the reporter
			val (text, fullText, actionLabel) = when (msg) {
				is GlobalDependencies.UiMessage.Text -> Triple(msg.text, msg.fullText, msg.actionLabel)
				is GlobalDependencies.UiMessage.Notice -> noticeMessage(ctx, msg.notice)
			}
			val action = if (fullText != text)
				snackbar.showSnackbar(text, actionLabel ?: fullTextLabel)
			else snackbar.showSnackbar(text)
			if (action == SnackbarResult.ActionPerformed) MessagePopup(text, fullText)
		}
	}
	return snackbar
}

/** The one place where a notice becomes display strings, kept next to its surface. */
private fun noticeMessage(ctx: Context, notice: UiNotice): Triple<String, String, String?> =
	when (notice) {
		is UiNotice.PasteInsideItself ->
			ctx.getString(R.string.fail_paste_inside).let { Triple(it, it, null) }
		is UiNotice.PasteNoTarget ->
			ctx.getString(R.string.fail_paste_target).let { Triple(it, it, null) }
		is UiNotice.PasteOtherSubject ->
			ctx.getString(R.string.fail_paste_other_subject).let { Triple(it, it, null) }
		is UiNotice.PasteAlreadyPresent ->
			ctx.getString(R.string.fail_paste_present).let { Triple(it, it, null) }
		is UiNotice.NoteCopied ->
			ctx.getString(R.string.note_copied).let { Triple(it, it, null) }
		is UiNotice.BadPattern ->
			ctx.getString(R.string.pattern_err, notice.detail).let { Triple(it, it, null) }
		is UiNotice.FlipBlankName ->
			ctx.getString(R.string.flip_conflicts_blank).let { Triple(it, it, null) }
		is UiNotice.OneSubjectOnly -> {
			// the named form tells the user which subject to return to
			val text = if (notice.subject != null)
				ctx.getString(R.string.test_one_subject_of, notice.subject)
			else ctx.getString(R.string.test_one_subject)
			Triple(text, text, null)
		}
	}
