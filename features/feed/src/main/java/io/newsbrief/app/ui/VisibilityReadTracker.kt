package io.newsbrief.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tracks the uninterrupted time each article is substantially visible. */
internal class VisibilityReadTracker(
    private val scope: CoroutineScope,
    private val onVisibilityChanged: (List<Long>) -> Unit,
    private val markRead: suspend (List<Long>) -> Unit,
) {
    private val pending = mutableMapOf<Long, Job>()
    private var handled = emptySet<Long>()

    fun update(visible: Set<Long>, eligible: Set<Long>) {
        onVisibilityChanged(visible.toList())
        handled = handled.intersect(visible)
        (pending.keys - eligible).forEach { pending.remove(it)?.cancel() }
        (eligible - handled - pending.keys).forEach { id ->
            pending[id] = scope.launch {
                delay(2_000)
                markRead(listOf(id))
                handled = handled + id
                pending.remove(id)
            }
        }
    }

    fun close() {
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}
