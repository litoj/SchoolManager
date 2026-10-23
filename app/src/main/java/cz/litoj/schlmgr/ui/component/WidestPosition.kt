package cz.litoj.schlmgr.ui.component

/**
 * The position up to maxPosition with the widest text, since digit widths differ.
 * - the longest number is not always the widest.
 * - chosen without walking the list.
 */
internal fun widestPosition(maxPosition: Int, digitWidth: (Int) -> Int): Int {
	val max = maxPosition.coerceAtLeast(1)
	val digits = max.toString()
	val count = digits.length

	// the widest digit for a leading slot, which is never 0, and for any slot after it.
	val lead = widestDigit(1..9, digitWidth)
	val rest = widestDigit(0..9, digitWidth)

	var best = max
	var bestWidth = digits.sumOf { digitWidth(it - '0') }

	// keeps the widest candidate, and the larger number wins a width tie.
	fun consider(candidate: Int, width: Int) {
		if (width > bestWidth || (width == bestWidth && candidate > best)) {
			bestWidth = width
			best = candidate
		}
	}

	// a shorter number can still be widest, since a narrow 1 lets 999 outrun 1000.
	for (length in 1 until count) {
		consider(
			("$lead" + "$rest".repeat(length - 1)).toInt(),
			digitWidth(lead) + (length - 1) * digitWidth(rest),
		)
	}

	// same-length numbers below max keep the prefix of max, dip once below its digit,
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
