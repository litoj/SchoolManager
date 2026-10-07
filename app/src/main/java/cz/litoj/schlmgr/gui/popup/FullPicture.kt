package cz.litoj.schlmgr.gui.popup

import android.graphics.Bitmap
import android.view.ViewGroup
import android.widget.FrameLayout

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity

import cz.litoj.schlmgr.gui.list.ImageItemModel
import cz.litoj.schlmgr.gui.engine.IOSystem.Formatter
import cz.litoj.schlmgr.gui.engine.objects.Picture

/**
 * Fullscreen viewer for a single picture: wide images scroll horizontally, tall ones
 * vertically (matching the old dual-scroll layout), tapping anywhere dismisses.
 */
class FullPicture : AbstractPopup {

	constructor(picture: Picture) : this(picture.file)

	constructor(img: Formatter.IOSystem.GeneralPath) : super(0, false) {
		Runtime.getRuntime().gc()
		pic = ImageItemModel.getScaledBitmap(img, size.toFloat(), true)
		wide = (pic?.width ?: 0) > (pic?.height ?: 0)
		create()
	}

	private val pic: Bitmap?
	private val wide: Boolean

	override fun addContent(view: ViewGroup) {
		val compose = ComposeView(view.context)
		(view as FrameLayout).addView(
			compose,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
		compose.setContent { FullPictureScreen() }
	}

/** One bitmap pixel as a dp value at the current screen density. */
@Composable
private fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

@Composable
private fun FullPictureScreen() {
	val bitmap = pic ?: return
		Box(
			Modifier
				.fillMaxSize()
				.clickable { dismiss() },
			contentAlignment = Alignment.Center
		) {
			if (wide) {
				Box(
					Modifier
						.fillMaxSize()
						.horizontalScroll(rememberScrollState())
				) {
					// Height matches the screen, the width follows the aspect ratio (the old
					// adjustViewBounds + fitXY combination), so wide images can be panned.
					Image(
						bitmap = bitmap.asImageBitmap(),
						contentDescription = null,
						// The bitmap is already scaled to the bigger display dimension (FullPicture.size),
						// so 1 bitmap pixel ≈ 1 dp and the raw dimensions give a natural 1:1 pannable
						// image (the old adjustViewBounds + fitXY behaviour).
						modifier = Modifier
							.height(bitmap.height.pxToDp())
							.width(bitmap.width.pxToDp()),
						contentScale = ContentScale.Fit,
					)
				}
			} else {
				Box(
					Modifier
						.fillMaxSize()
						.verticalScroll(rememberScrollState())
				) {
					Image(
						bitmap = bitmap.asImageBitmap(),
						contentDescription = null,
						modifier = Modifier.fillMaxSize(),
						contentScale = ContentScale.Fit,
					)
				}
			}
		}
	}

	companion object {

		/** The bigger value of the display dimensions. */
		@JvmField
		var size: Int = 0
	}
}
