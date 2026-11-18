package cz.litoj.schlmgr.ui.shell

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.Snackbar
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.app.GlobalDependencies
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.ui.popup.MessagePopup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch

/**
 * Renders [GlobalDependencies.messages] as snackbars, with a full text, share or undo action.
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
	val undoLabel = stringResource(R.string.action_undo)
	val lifecycle = LocalLifecycleOwner.current.lifecycle
	LaunchedEffect(snackbar, lifecycle) {
		// a paused activity never consumes a message, and it never queues another copy
		lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
			// only the last undoable message undoes, while the loop below sits in a showing snackbar.
			// so it needs its own collector, and the host still queues it behind other messages
			launch {
				GlobalDependencies.messages
					.filterIsInstance<GlobalDependencies.UiMessage.Undoable>()
					.collectLatest { msg ->
						val (text) = noticeMessage(ctx, msg.notice)
						val action = snackbar.showSnackbar(
							text, undoLabel, duration = SnackbarDuration.Indefinite)
						if (action == SnackbarResult.ActionPerformed) msg.onUndo()
					}
			}
			GlobalDependencies.messages.collect { msg ->
				// notices resolve here at display time, never at the reporter
				when (msg) {
					is GlobalDependencies.UiMessage.Text -> {
						val action = if (msg.fullText != msg.text)
							snackbar.showSnackbar(
								msg.text, msg.actionLabel ?: fullTextLabel,
								duration = SnackbarDuration.Indefinite)
						else snackbar.showSnackbar(msg.text, duration = SnackbarDuration.Indefinite)
						if (action == SnackbarResult.ActionPerformed) MessagePopup(msg.text, msg.fullText)
					}
					is GlobalDependencies.UiMessage.Notice -> {
						val (text, fullText, actionLabel) = noticeMessage(ctx, msg.notice)
						val action = if (fullText != text)
							snackbar.showSnackbar(
								text, actionLabel ?: fullTextLabel,
								duration = SnackbarDuration.Indefinite)
						else snackbar.showSnackbar(text, duration = SnackbarDuration.Indefinite)
						if (action == SnackbarResult.ActionPerformed) MessagePopup(text, fullText)
					}
					is GlobalDependencies.UiMessage.Undoable -> Unit
					is GlobalDependencies.UiMessage.ShareFile -> {
						val action = snackbar.showSnackbar(
							msg.text, shareLabel, duration = SnackbarDuration.Indefinite)
						if (action == SnackbarResult.ActionPerformed)
							shareFile(ctx, msg.uri, msg.mime)
					}
				}
			}
		}
	}
	return snackbar
}

/** Every message stays for [SNACKBAR_MS], so no notice lingers over the screen it reports. */
private const val SNACKBAR_MS = 2_000L

/** A message without an action dismisses on a tap, while action buttons keep their clicks. */
@Composable
fun MessageSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
	val current = hostState.currentSnackbarData
	val accessibility = LocalAccessibilityManager.current
	// Material offers only fixed durations, so the host owns the short window
	LaunchedEffect(current, accessibility) {
		if (current != null) {
			// a screen reader setting may stretch the short window
			val timeout = accessibility?.calculateRecommendedTimeoutMillis(
				SNACKBAR_MS, true, true, current.actionLabel != null) ?: SNACKBAR_MS
			delay(timeout)
			current.dismiss()
		}
	}
	SnackbarHost(hostState, modifier) { data ->
		Snackbar(data, Modifier.clickable(enabled = data.actionLabel == null) { data.dismiss() })
	}
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
		is UiNotice.FlipBlankName ->
			ctx.getString(R.string.flip_conflicts_blank).let { Triple(it, it, null) }
		is UiNotice.ActionUnavailable ->
			ctx.getString(notice.reason).let { Triple(it, it, null) }
		is UiNotice.SourceRemoved ->
			ctx.getString(R.string.test_source_removed, notice.name).let { Triple(it, it, null) }
		is UiNotice.OneSubjectOnly -> {
			// the named form tells the user which subject to return to
			val text = if (notice.subject != null)
				ctx.getString(R.string.test_one_subject_of, notice.subject)
			else ctx.getString(R.string.test_one_subject)
			Triple(text, text, null)
		}
	}
