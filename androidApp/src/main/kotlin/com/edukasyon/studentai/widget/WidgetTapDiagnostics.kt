package com.edukasyon.studentai.widget

/**
 * Last-tap / last-paint breadcrumbs for the on-device Diagnose panel.
 *
 * Logged at ERROR level in the tap path too, so the milestones stay visible
 * even in error-filtered logcats. Process-local only — cleared on restart.
 */
object WidgetTapDiagnostics {
    @Volatile var lastTapTaskId: String? = null
    @Volatile var lastTapRendered: Boolean? = null
    @Volatile var lastTapTimeMs: Long = 0L
    @Volatile var lastOptimisticPainted: Boolean = false
    @Volatile var lastReconcileDone: Boolean = false
    @Volatile var lastReconcileError: String? = null
    @Volatile var lastPaintedIds: String = "-"
    @Volatile var lastHostedIds: String = "-"

    fun recordTap(taskId: String, rendered: Boolean? = null) {
        lastTapTaskId = taskId
        lastTapRendered = rendered
        lastTapTimeMs = System.currentTimeMillis()
        lastOptimisticPainted = false
        lastReconcileDone = false
        lastReconcileError = null
    }

    fun recordOptimistic(painted: Boolean) {
        lastOptimisticPainted = painted
    }

    fun recordReconcile(done: Boolean, error: String? = null) {
        lastReconcileDone = done
        lastReconcileError = error
    }

    fun recordPaintCoverage(paintedIds: Collection<Int>, hostedIds: Collection<Int>) {
        lastPaintedIds = paintedIds.sorted().joinToString(",").ifBlank { "-" }
        lastHostedIds = hostedIds.sorted().joinToString(",").ifBlank { "-" }
    }

    fun summary(): String {
        if (lastTapTaskId == null) return "lastTap=none"
        val ageSec = (System.currentTimeMillis() - lastTapTimeMs) / 1000
        return "lastTap task=${lastTapTaskId} rendered=$lastTapRendered ${ageSec}s ago " +
            "optimistic=$lastOptimisticPainted reconcile=$lastReconcileDone" +
            (lastReconcileError?.let { " err=$it" } ?: "") +
            " painted=[$lastPaintedIds] hosted=[$lastHostedIds]"
    }
}
