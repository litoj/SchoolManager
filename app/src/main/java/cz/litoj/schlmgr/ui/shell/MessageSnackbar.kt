package cz.litoj.schlmgr.ui.shell

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.SnackbarDuration
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
 * Renders [GlobalDependencies.messages] as snackbars, with a full text or a share action.
 * - the full text action opens the [MessagePopup] dialog with the whole text.
 * - every activity composes it, so a worker-thread report shows on the foreground screen.
 * - the flow delivers only while it is collected.
 */
@Composable
fun messageSnackbarHost(): SnackbarHostState {
	val snackbar = remember { SnackbarHostState() }
	val ctx = LocalContext.current
	val fullTextLabel = stringResource(R.string.action_full_text)
	val shareLabel = stringResource(R.string.action_share)
	LaunchedEffect(snackbar) {
		GlobalDependencies.messages.collect { msg ->
			// notices resolve here at display time, never at the reporter
			when (msg) {
				is GlobalDependencies.UiMessage.Text -> {
					val action = if (msg.fullText != msg.text)
						snackbar.showSnackbar(msg.text, msg.actionLabel ?: fullTextLabel)
					else snackbar.showSnackbar(msg.text)
					if (action == SnackbarResult.ActionPerformed) MessagePopup(msg.text, msg.fullText)
				}
				is GlobalDependencies.UiMessage.Notice -> {
					val (text, fullText, actionLabel) = noticeMessage(ctx, msg.notice)
					val action = if (fullText != text)
						snackbar.showSnackbar(text, actionLabel ?: fullTextLabel)
					else snackbar.showSnackbar(text)
					if (action == SnackbarResult.ActionPerformed) MessagePopup(text, fullText)
				}
				is GlobalDependencies.UiMessage.ShareFile -> {
					// the share has no other entry point, so the window runs long
					val action = snackbar.showSnackbar(
						msg.text, shareLabel, duration = SnackbarDuration.Long)
					if (action == SnackbarResult.ActionPerformed)
						shareFile(ctx, msg.uri, msg.mime)
				}
			}
		}
	}
	return snackbar
}

/** Opens the system share sheet for the saved export document. */
private fun shareFile(ctx: Context, uri: Uri, mime: String) {
	val send = Intent(Intent.ACTION_SEND).apply {
		type = mime
		putExtra(Intent.EXTRA_STREAM, uri)
		clipData = ClipData.newRawUri(null, uri)
		addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
	}
	ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.action_share)))
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
