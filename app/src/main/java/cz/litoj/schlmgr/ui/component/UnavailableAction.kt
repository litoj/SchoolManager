package cz.litoj.schlmgr.ui.component

import androidx.annotation.StringRes
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages

/** Reports why a muted action cannot run, so the action keeps its place instead of disappearing. */
internal fun reportUnavailable(@StringRes reason: Int) {
	UserMessages.showNotice(UiNotice.ActionUnavailable(reason))
}
