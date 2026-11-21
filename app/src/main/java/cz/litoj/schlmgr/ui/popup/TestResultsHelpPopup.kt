package cz.litoj.schlmgr.ui.popup

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.litoj.schlmgr.R
import cz.litoj.schlmgr.testing.AnswerComparison
import cz.litoj.schlmgr.ui.component.HairlineDivider
import cz.litoj.schlmgr.ui.component.positionColumnWidth
import cz.litoj.schlmgr.ui.viewmodel.ExplorerRowVariant
import cz.litoj.schlmgr.ui.viewmodel.StaticRow
import cz.litoj.schlmgr.ui.viewmodel.RowKind

/**
 * The results help, where every caption is followed by the real thing it describes.
 * - the band rows are report rows, so their colour and count are the report's own.
 * - the answer sample renders through the report renderer.
 */
class TestResultsHelpPopup : DialogSpec {

	/** One band example with the count that paints it. */
	private class BandExample(@StringRes val label: Int, val passed: Int, val tested: Int)

	private val bands = listOf(
		BandExample(R.string.test_results_help_low, 0, 9),
		BandExample(R.string.test_results_help_mid, 5, 10),
		BandExample(R.string.test_results_help_high, 9, 9),
	)

	init {
		Dialogs.show(this)
	}

	@Composable
	override fun Content() {
		// the number size and band width the report gives its own rows
		val positionWidth = positionColumnWidth(bands.size, ExplorerRowVariant.Browse)
		PopupCard {
			PopupTitle(stringResource(R.string.help_test_results))
			HairlineDivider()
			PopupScrollBody {
				PopupBodyText(stringResource(R.string.test_results_help_band))
				bands.forEachIndexed { index, band ->
					BandRow(index + 1, band, positionWidth)
				}
				PopupBodyText(
					stringResource(R.string.test_results_help_marks),
					Modifier.padding(top = 10.dp),
				)
				AnswerSample()
			}
			HairlineDivider()
			PopupButtonRow {
				PopupButton(stringResource(R.string.ok), Modifier.weight(1f)) {
					Dialogs.dismiss(this@TestResultsHelpPopup)
				}
			}
		}
	}

	/** A report row, so the band and the count show the pair the caption names. */
	@Composable
	private fun BandRow(position: Int, band: BandExample, positionWidth: Dp) {
		ReportRow(
			StaticRow(
				key = "help-band-$position",
				position = position,
				name = stringResource(band.label),
				description = "",
				kind = RowKind.WORD,
				ratio = band.passed * 100 / band.tested,
				isReference = false,
				selected = false,
			),
			band.passed,
			band.tested,
			positionWidth,
		)
	}

	/** Renders through the report renderer, so the help cannot drift from the marks. */
	@Composable
	private fun AnswerSample() {
		val target = stringResource(R.string.test_results_help_target)
		Column(Modifier.padding(top = 4.dp)) {
			AnswerDiffText(
				stringResource(R.string.test_your_answer, ""),
				listOf(AnswerComparison.compare(stringResource(R.string.test_results_help_answer), target)),
				true,
			)
			ExpectedAnswerText(target)
		}
	}
}
