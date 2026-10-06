package com.mccal.folio

/**
 * The live root hinge feed (ADR 0010): runs the helper through `su` and hands each reading on, until [stop] or until it is lost.
 * It starts only when the owner has tested root and switched the feed on, and runs only while the fold animation is listening.
 * [onSample] and [onLost] are called on the feed's own thread; the caller moves them to the thread it needs.
 */
internal class RootHingeFeed(
    private val launcher: SuLauncher,
    private val apkPath: String,
    private val suPath: String,
    private val onSample: (HingeSample) -> Unit,
    private val onLost: (String) -> Unit,
    private val now: () -> Long,
) {
    @Volatile private var stopped = false
    @Volatile private var process: SuProcess? = null
    private var thread: Thread? = null

    fun start() {
        if (thread != null) return
        thread = Thread({ run() }, "folio-root-hinge").apply { isDaemon = true; start() }
    }

    /** Ends the feed at once: closing the process closes the helper's input, which is how it knows to stop. */
    fun stop() {
        stopped = true
        process?.close()
    }

    private fun run() {
        val command = RootHingeRunner.shellCommand(apkPath, RootHingeHelper.MAX_SECONDS) ?: return onLost("path")
        val gate = RootHingeProtocol.Gate()
        while (!stopped) {
            val p = try { launcher.start(listOf(suPath, "-c", command)) } catch (_: Exception) { return finish("no-su") }
            process = p
            if (stopped) { p.close(); return }
            val startedAt = now()
            var ready = false
            try {
                while (!stopped) {
                    when (val line = p.next(1_000L)) {
                        SuLine.Timeout -> if (!ready && now() - startedAt > READY_WAIT_MS) return finish("no-answer")
                        SuLine.Eof -> {
                            // The helper ends by itself at its lifetime: that is a restart, not a loss. Anything sooner is a loss.
                            if (ready && now() - startedAt >= RESTART_AFTER_MS) break
                            return finish("ended")
                        }
                        is SuLine.Text -> when (val m = RootHingeProtocol.parse(line.line)) {
                            RootHingeProtocol.Message.Ready -> ready = true
                            is RootHingeProtocol.Message.Reading -> { ready = true; if (gate.accept(m.sample)) onSample(m.sample) }
                            is RootHingeProtocol.Message.Stopped -> return finish(m.reason)
                            null -> Unit
                        }
                    }
                }
            } finally {
                p.close()
            }
        }
    }

    private fun finish(reason: String) { if (!stopped) onLost(reason) }

    companion object {
        const val READY_WAIT_MS = 10_000L
        /** The helper's own lifetime is [RootHingeHelper.MAX_SECONDS]; one that ends close to it is simply done, so it is started again. */
        const val RESTART_AFTER_MS = (RootHingeHelper.MAX_SECONDS - 10) * 1_000L
    }
}
