package cz.litoj.schlmgr.ui.popup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.ui.component.BareTextFieldStyle
import cz.litoj.schlmgr.ui.component.WideTextField

/**
 * The shared look of the small dialogs: a theme-background card, only as tall as its content.
 * - it grows to the full height between the status-bar-height gaps.
 * - a centered [Box] hugs it, so no dead space surrounds the bar.
 * - a scrollable middle takes weight 1f without fill and fills the free height first.
 */
@Composable
internal fun PopupCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
	// the band height gaps both ends: the 30dp margin measures from the status bar above.
	// an equally tall gap sits above the screen bottom
	val statusBar = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
	Box(
		Modifier
			.fillMaxSize()
			.padding(top = statusBar, bottom = statusBar),
		contentAlignment = Alignment.Center,
	) {
		Column(
			Modifier
				.padding(30.dp)
				.fillMaxWidth()
				.background(colorResource(R.color.colorPrimaryBg))
		) {
			content()
		}
	}
}

/**
 * The centered title line of a popup card.
 * - the trailing slot carries icons at the row end.
 */
@Composable
internal fun PopupTitle(text: String, trailing: (@Composable BoxScope.() -> Unit)? = null) {
	Box(
		Modifier
			.fillMaxWidth()
			.padding(top = 5.dp)
	) {
		Text(
			text,
			Modifier.fillMaxWidth(),
			style = TextStyle(fontSize = 25.sp, color = TextColor),
			textAlign = TextAlign.Center,
		)
		trailing?.invoke(this)
	}
}

/**
 * The scrollable text body of a popup card.
 * - the weight grows it to the free screen height, so the button row stays at the bottom.
 */
@Composable
internal fun ColumnScope.PopupScrollBody(text: String) {
	Box(
		Modifier
			.weight(1f, fill = false)
			.verticalScroll(rememberScrollState())
	) {
		Text(
			text,
			Modifier.padding(5.dp),
			style = TextStyle(fontSize = 20.sp, color = TextColor),
		)
	}
}

/** The bottom button row of a popup card, one flat bar of the shared height. */
@Composable
internal fun PopupButtonRow(content: @Composable RowScope.() -> Unit) {
	Row(Modifier.fillMaxWidth().height(50.dp), content = content)
}

@Composable
internal fun VerticalButtonSeparator() {
	Spacer(
		Modifier
			.width(0.5.dp)
			.fillMaxHeight()
			.padding(vertical = 10.dp)
			.background(colorResource(R.color.colorStripDivider))
	)
}

@Composable
internal fun PopupButton(label: String, modifier: Modifier, onClick: () -> Unit) {
	Box(
		modifier
			.fillMaxHeight()
			.clickable(onClick = onClick),
		contentAlignment = Alignment.Center,
	) {
		Text(
			label,
			Modifier.padding(horizontal = 5.dp),
			style = TextStyle(fontSize = 18.sp, color = TextColor),
		)
	}
}

internal val TextColor @Composable get() = colorResource(R.color.colorPrimaryFg)

/**
 * A text field inside the thin border of the popup card, reusing [WideTextField].
 */
@Composable
internal fun BorderedTextField(
	value: String,
	onValueChange: (String) -> Unit,
	@StringRes label: Int,
	textColor: Color,
	modifier: Modifier = Modifier,
	singleLine: Boolean = false,
	onImeAction: (() -> Unit)? = null,
	imeAction: ImeAction? = null,
	focusRequester: FocusRequester? = null,
	horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
	Box(
		modifier
			.fillMaxWidth()
			.padding(horizontal = horizontalPadding, vertical = 5.dp)
			.border(0.8.dp, colorResource(R.color.colorControlLine))
			.padding(5.dp)
	) {
		WideTextField(
			value = value,
			onValueChange = onValueChange,
			fontSize = 17.sp,
			style = BareTextFieldStyle(textColor = textColor, cursorColor = textColor, underline = false),
			focusRequester = focusRequester,
			imeAction = imeAction
				?: if (onImeAction != null) ImeAction.Next else ImeAction.Default,
			hint = stringResource(label),
			singleLine = singleLine,
			onImeAction = onImeAction,
		)
	}
}
