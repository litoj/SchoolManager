package cz.litoj.schlmgr.ui.shell

/**
 * The import mover hands the main screen its destination.
 * - [path] holds the container ids, root first and the open container last.
 * - [scrollToBottom] reveals the rows the import just added.
 */
data class MainLaunchTarget(val path: List<Int>, val scrollToBottom: Boolean)
