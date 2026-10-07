package cz.litoj.schlmgr.ui.component

/**
 * The position in `1..[maxPosition]` whose text takes the most horizontal space under
 * [digitWidth], a pixel width per digit 0–9. The largest position is not always the
 * widest: digits have different widths, so a wide low digit can outrun the maximum,
 * and a short number of wide digits can outrun a long one of narrow digits. Picking
 * the widest reachable combination from the digit widths avoids walking the whole
 * list. Among equally wide positions the largest is returned.
 */
internal fun widestPosition(maxPosition: Int, digitWidth: (Int) -> Int): Int {
	val max = maxPosition.coerceAtLeast(1)
	val digits = max.toString()
	val count = digits.length

	// The widest digit for a leading slot (never 0) and for any slot after it.
	val lead = widestDigit(1..9, digitWidth)
	val rest = widestDigit(0..9, digitWidth)

	var best = max
	var bestWidth = digits.sumOf { digitWidth(it - '0') }

	// Keeps the widest candidate seen so far; the larger number wins a width tie.
	fun consider(candidate: Int, width: Int) {
		if (width > bestWidth || (width == bestWidth && candidate > best)) {
			bestWidth = width
			best = candidate
		}
	}

	// A shorter number can still be the widest: a narrow 1 lets 999 outrun 1000.
	for (length in 1 until count) {
		consider(
			("$lead" + "$rest".repeat(length - 1)).toInt(),
			digitWidth(lead) + (length - 1) * digitWidth(rest),
		)
	}

	// Same-length numbers below max: keep max's prefix, dip below its digit once,
	// then take the widest digits freely.
	var prefixWidth = 0
	for (i in 0 until count) {
		val limit = digits[i] - '0'
		val lowest = if (i == 0) 1 else 0
		if (limit > lowest) {
			val dip = widestDigit(lowest until limit, digitWidth)
			consider(
				(digits.take(i) + "$dip" + "$rest".repeat(count - i - 1)).toInt(),
				prefixWidth + digitWidth(dip) + (count - i - 1) * digitWidth(rest),
			)
		}
		prefixWidth += digitWidth(limit)
	}
	return best
}

/** The digit of [range] with the widest glyph under [digitWidth]; the larger digit wins a tie. */
private fun widestDigit(range: IntRange, digitWidth: (Int) -> Int): Int {
	var best = range.first
	var bestWidth = digitWidth(best)
	for (digit in range) {
		val width = digitWidth(digit)
		if (width > bestWidth || (width == bestWidth && digit > best)) {
			bestWidth = width
			best = digit
		}
	}
	return best
}
