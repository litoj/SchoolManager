package cz.litoj.schlmgr.app

import java.util.IdentityHashMap

/** Throwable chain helpers shared by crash reporting and user messaging. */
object Throwables {

	/** Message of the innermost cause in the chain, or null when it has none. */
	@JvmStatic
	fun getFirstCause(e: Throwable): String? {
		// a cyclic chain ends at the deepest distinct node, and the seen-set
		// stops the walk where it starts repeating
		val seen = IdentityHashMap<Throwable, Unit>()
		var deepest = e
		var t = e
		while (seen.put(t, Unit) == null) {
			deepest = t
			t = t.cause ?: return deepest.message
		}
		return deepest.message
	}
}
