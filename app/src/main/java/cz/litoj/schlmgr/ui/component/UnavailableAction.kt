package cz.litoj.schlmgr.ui.component

import androidx.annotation.StringRes
import cz.litoj.schlmgr.app.UiNotice
import cz.litoj.schlmgr.app.UserMessages

/** Reports why a muted action cannot run through the injected application message owner. */
internal fun reportUnavailable(userMessages: UserMessages, @StringRes reason: Int) {
	userMessages.showNotice(UiNotice.ActionUnavailable(reason))
}
