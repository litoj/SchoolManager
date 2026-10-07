package cz.litoj.schlmgr.ui.explorer

import androidx.documentfile.provider.DocumentFile
import cz.litoj.schlmgr.db.DbItem
import cz.litoj.schlmgr.db.ItemRepository
import cz.litoj.schlmgr.db.io.LegacyJsonLoader
import cz.litoj.schlmgr.app.Startup
import java.util.Arrays
import java.util.LinkedList

/**
 * App-wide browsing state: the navigation backlog of the explorer and the
 * legacy-import bookkeeping.
 */
object CurrentData {

	/**
	 * The main explorer's navigation backlog. Initialised (not just constructed):
	 * a fresh [BackLog] has no `onePath` entry, and the first same-path hop would
	 * then pop from an empty list.
	 */
	val backLog = BackLog().apply { clear() }

	/**
	 * The navigation history of the explorer list. `path` is the container
	 * chain currently displayed (root first, the open container last);
	 * [onePath] counts how many same-path hops (search jumps) are stacked.
	 */
	class BackLog {
		var path: EasyList<DbItem> = EasyList()
		private val prevPaths = EasyList<EasyList<DbItem>>()
		val onePath = EasyList<Int>()

		/**
		 * Kinds of the stacked same-path hops, oldest first: `true` = a descent
		 * (grew [path] by one), `false` = a search run (left [path] alone). The
		 * legacy `remove()` told them apart by the then-current adapter type
		 * (`SearchAdapter == HierarchyAdapter` → no path pop); the recorded kind
		 * is its equivalent now that the adapters are gone.
		 */
		private val hopDescents = EasyList<Boolean>()

		/**
		 * Records a navigation step. [newPath] `null` means "decide by comparing
		 * [currPath] with the current one"; `true` pushes a new history entry,
		 * `false`/`null`-same extends the current one (and appends [bd] when given).
		 */
		fun add(newPath: Boolean?, bd: DbItem?, currPath: EasyList<DbItem>?) {
			val isNewPath = newPath ?: run {
				val current = currPath ?: EasyList()
				var same = current.size == path.size
				if (same) for (i in 0 until path.size) {
					if (path.getOrNull(i)?.id != current.getOrNull(i)?.id) {
						same = false
						break
					}
				}
				same
			}
			if (isNewPath) {
				prevPaths.add(path)
				path = currPath ?: EasyList()
				onePath.add(0)
			} else {
				if (bd != null) path.add(bd)
				hopDescents.add(bd != null)
				onePath.add(onePath.removeAt(-1) + 1)
			}
		}

		fun clear() {
			path.clear()
			prevPaths.clear()
			onePath.clear()
			hopDescents.clear()
			onePath.add(0)
		}

		/**
		 * Pops one navigation step. A descent hop unwinds one path element; a
		 * search hop (the legacy SearchAdapter screen) leaves the path alone.
		 *
		 * @return `true` when only a same-path hop was popped (the caller stays in place)
		 */
		fun remove(): Boolean {
			var ret: Boolean
			if ((onePath.getOrNull(-1) ?: 0) > 0) {
				onePath.add(onePath.removeAt(-1) - 1)
				if (hopDescents.removeAt(-1) == true) path.removeAt(-1)
				ret = true
			} else {
				onePath.removeAt(-1)
				if (onePath.isEmpty()) {
					onePath.add(0)
					path = EasyList()
				} else {
					path = prevPaths.removeAt(-1)
				}
				ret = false
			}
			return ret
		}

		/**
		 * Pops the search hops stacked on top (they added no path element), stopping
		 * at the first descent hop — the legacy back-from-search unwound exactly
		 * these. The path is left unchanged.
		 */
		fun removeSearchHops() {
			while ((onePath.getOrNull(-1) ?: 0) > 0 && hopDescents.getOrNull(-1) == false) remove()
		}

		/**
		 * Whether the position the last [remove] landed on is a stacked search run —
		 * i.e. the screen being returned to was the search-result list, so the host
		 * restores it instead of loading the container's browse contents.
		 */
		fun onSearchHop(): Boolean =
			(onePath.getOrNull(-1) ?: 0) > 0 && hopDescents.getOrNull(-1) == false
	}

	/**
	 * [LinkedList] with python-style negative indexing through [getOrNull]:
	 * `getOrNull(-1)` is the last element, `null` when out of range instead
	 * of an exception. The plain `get`/`[]` access inherited from [LinkedList]
	 * throws on a negative index — use [getOrNull] for the indexed reads.
	 */
	class EasyList<T> : LinkedList<T>() {

		fun getOrNull(index: Int): T? {
			if (isEmpty()) return null
			if (index < 0) {
				val fromEnd = size + index
				return if (fromEnd < 0) null else super.get(fromEnd)
			}
			return if (size > index) super.get(index) else null
		}

		override fun removeAt(index: Int): T {
			return if (index < 0) super.removeAt(size + index) else super.removeAt(index)
		}

		companion object {
			@JvmStatic
			fun <E> convert(source: Array<E>): EasyList<E> {
				val ret = EasyList<E>()
				ret.addAll(Arrays.asList(*source))
				return ret
			}
		}
	}

	/**
	 * The one-time migration from the old file format into the database: when
	 * the database is empty, the old-format subjects of the default directory are
	 * loaded and inserted; afterwards the app reads the normal new way.
	 */
	@JvmStatic
	fun ensureMigrated() {
		if (!ItemRepository.initialized) return
		// the root must exist before any subject is linked to it (the migration below too)
		ItemRepository.ensureRoot()
		// the guard inside decides — only an empty database gets the conversion
		LegacyJsonLoader.migrateIfNeeded(DocumentFile.fromFile(Startup.defaultSubjectsDir))
	}
}
