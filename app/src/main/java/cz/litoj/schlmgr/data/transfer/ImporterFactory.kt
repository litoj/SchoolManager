package cz.litoj.schlmgr.data.transfer

import android.content.Context
import android.net.Uri
import cz.litoj.schlmgr.data.db.DbItem

enum class ImportKind {
	WORD_INTO_CONTAINER,
	WORD_AS_SUBJECT,
	JSON_TREE,
}

/**
 * The picked-file dispatch, shared by the import mover and the subjects screen.
 * - an opening square bracket or an opening curly brace starts a tree import.
 * - that tree import keeps its own names and takes the open container as its anchor.
 * - the picked name never decides, since a provider may report none at all.
 * - a suffix is thus not a promise.
 */
object ImporterFactory {

	fun decide(jsonContent: Boolean, target: ImportTarget): ImportKind =
		if (jsonContent) ImportKind.JSON_TREE
		else when (target) {
			is ImportTarget.Root -> ImportKind.WORD_AS_SUBJECT
			is ImportTarget.Container -> ImportKind.WORD_INTO_CONTAINER
		}

	fun targetFor(openContainer: DbItem?): ImportTarget =
		if (openContainer == null) ImportTarget.Root else ImportTarget.Container(openContainer)

	/**
	 * Builds the import worker of a picked document.
	 * - the document shape and the provider name resolve inside, so the
	 *   caller wraps the call on io.
	 * - an empty [fileName] reads the provider name here, a known one skips
	 *   the query.
	 */
	fun createPickedImport(
		appContext: Context,
		postToUi: (Runnable) -> Unit,
		uri: Uri,
		fileName: String,
		openContainer: () -> DbItem?,
		atRoot: () -> Boolean,
		onRootReload: () -> Unit,
		onCurrentReload: () -> Unit,
		onContainerReload: () -> Unit,
	): FileWorker {
		val jsonContent = FileGateway.startsAsJson(appContext, uri)
		val name = fileName.ifEmpty { FileGateway.displayName(appContext, uri, "") }
		return create(
			appContext, postToUi, uri, name, jsonContent,
			targetFor(openContainer()), openContainer, atRoot,
			onRootReload, onCurrentReload, onContainerReload,
		)
	}

	/**
	 * - name, shape and target resolve on the caller thread.
	 * - workers keep the pick-time snapshots.
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
