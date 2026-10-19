package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.data.db.DbItem

/**
 * Which import worker handles a picked file.
 */
enum class ImportKind {
	WORD_INTO_CONTAINER,
	WORD_AS_SUBJECT,
	JSON_TREE,
}

/**
 * The `onAnyFilePicked` dispatch — the document's own shape first, the
 * backlog position second: a `[`/`{` start is a tree import (its own names,
 * the open container as its anchor), anything else is a word list (a new
 * subject at the root, the open container's content otherwise). The picked
 * name never decides — a provider may report none at all, so a suffix is
 * not a promise.
 */
object ImporterFactory {

	fun decide(jsonContent: Boolean, target: ImportTarget): ImportKind =
		if (jsonContent) ImportKind.JSON_TREE
		else when (target) {
			is ImportTarget.Root -> ImportKind.WORD_AS_SUBJECT
			is ImportTarget.Container -> ImportKind.WORD_INTO_CONTAINER
		}

	/** The backlog position as an [ImportTarget]: no open container is the root. */
	fun targetFor(openContainer: DbItem?): ImportTarget =
		if (openContainer == null) ImportTarget.Root else ImportTarget.Container(openContainer)

	/**
	 * Builds the picked file's worker without starting it:
	 * - [decide] picks the kind, the `when` the worker.
	 * - name, shape and target resolve on the caller thread — workers keep the pick-time snapshots.
	 */
	fun create(
		appContext: Context,
		postToUi: (Runnable) -> Unit,
		uri: Uri,
		fileName: String,
		jsonContent: Boolean,
		target: ImportTarget,
		openContainer: () -> DbItem?,
		atRoot: () -> Boolean,
		onRootReload: () -> Unit,
		onCurrentReload: () -> Unit,
		onContainerReload: () -> Unit,
	): FileWorker = when (decide(jsonContent, target)) {
		ImportKind.WORD_INTO_CONTAINER ->
			WordIntoContainerImport(appContext, postToUi, uri, fileName, openContainer, atRoot, onRootReload, onContainerReload)
		ImportKind.WORD_AS_SUBJECT ->
			WordAsSubjectImport(appContext, postToUi, uri, onRootReload)
		ImportKind.JSON_TREE ->
			JsonTreeImport(appContext, postToUi, uri, openContainer, atRoot, onRootReload, onCurrentReload)
	}
}
