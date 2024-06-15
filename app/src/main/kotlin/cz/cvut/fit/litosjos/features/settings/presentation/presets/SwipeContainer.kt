package cz.cvut.fit.litosjos.features.settings.presentation.presets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import cz.cvut.fit.litosjos.features.settings.theme.Sizes
import kotlinx.coroutines.delay


@Composable
fun SwipeContainer(
	onDelete: () -> Unit,
	onUpdate: () -> Unit,
	animationDuration: Int = 500,
	content: @Composable () -> Unit
) {
	var isRemoved by remember { mutableStateOf(false) }
	val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
		when (value) {
			SwipeToDismissBoxValue.EndToStart -> {
				isRemoved = true
				true
			}

			SwipeToDismissBoxValue.StartToEnd -> {
				onUpdate()
				false
			}

			else -> false
		}
	})

	LaunchedEffect(key1 = isRemoved) {
		if (isRemoved) {
			delay(animationDuration.toLong())
			onDelete()
		}
	}

	AnimatedVisibility(
		visible = !isRemoved, exit = shrinkVertically(
			animationSpec = tween(durationMillis = animationDuration), shrinkTowards = Alignment.Top
		) + fadeOut()
	) {
		SwipeToDismissBox(
			state = state,
			backgroundContent = {
				DeleteBackground(state = state)
			},
		) {
			content()
		}
	}
}

@Composable
fun DeleteBackground(
	state: SwipeToDismissBoxState
) {
	if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Red),
			contentAlignment = Alignment.CenterEnd
		) {
			Icon(
				imageVector = Icons.Default.Delete,
				tint = Color.White,
				modifier = Modifier.offset(x = -Sizes.padding)
			)
		}
	} else if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Blue),
			contentAlignment = Alignment.CenterStart
		) {
			Icon(
				imageVector = Icons.Default.Edit,
				tint = Color.White,
				modifier = Modifier.offset(x = Sizes.padding)
			)
		}
	}
}