package cz.litoj.schlmgr.ui.popup

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.testing.AnswerComparison
import cz.litoj.schlmgr.testing.AnswerMark
import cz.litoj.schlmgr.ui.component.ExplorerColors

/** An omitted letter fades against the body text, so the typed answer keeps its shape. */
private const val MissingAlpha = 0.45f

/**
 * One typed answer marked against its nearest variant.
 * - [hasAnswer] false shows the no-answer label in red.
 */
@Composable
internal fun AnswerDiffText(
	prefix: String,
	comparisons: List<AnswerComparison>,
	hasAnswer: Boolean,
	modifier: Modifier = Modifier,
) {
	val body = ExplorerColors.body
	val missing = body.copy(alpha = MissingAlpha)
	val noAnswer = stringResource(R.string.test_no_answer)
	Text(
		buildAnnotatedString {
			append(prefix)
			if (!hasAnswer) withStyle(SpanStyle(color = ExplorerColors.failure)) { append(noAnswer) }
			else comparisons.forEachIndexed { index, comparison ->
				if (index > 0) append("/")
				for (part in comparison.parts) {
					val color = when (part.mark) {
						AnswerMark.CORRECT -> ExplorerColors.header
						AnswerMark.MISSING -> missing
						AnswerMark.EXTRA -> ExplorerColors.failure
					}
					withStyle(SpanStyle(color = color)) { append(part.text) }
				}
			}
		},
		modifier = modifier,
		style = TextStyle(fontSize = 15.sp, color = body),
	)
}

/** The complete expected answer, faded under the marked answer. */
@Composable
internal fun ExpectedAnswerText(expected: String, modifier: Modifier = Modifier) {
	Text(
		buildAnnotatedString {
			append(stringResource(R.string.test_correct, ""))
			withStyle(SpanStyle(color = ExplorerColors.description)) { append(expected) }
		},
		modifier = modifier,
		style = TextStyle(fontSize = 15.sp, color = ExplorerColors.body),
	)
}
