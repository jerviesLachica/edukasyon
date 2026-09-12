package com.edukasyon.studentai.widget.update

/**
 * Last-refresh breadcrumbs for the on-device Diagnose panel.
 * Process-local only — cleared on restart.
 */
object WidgetV2Diagnostics {
    @Volatile var lastRefreshIds: String = "-"
    @Volatile var lastRefreshReason: String = "-"
    @Volatile var lastRefreshVersion: Int = -1
    @Volatile var lastRefreshGen: Long = 0L
    @Volatile var lastRefreshTimeMs: Long = 0L

    fun recordRefresh(appWidgetId: Int, reason: String, version: Int, generatedAt: Long) {
        lastRefreshIds = appWidgetId.toString()
        lastRefreshReason = reason
        lastRefreshVersion = version
        lastRefreshGen = generatedAt
        lastRefreshTimeMs = System.currentTimeMillis()
    }

    fun recordPaint(painted: Collection<Int>, hosted: Collection<Int>) {
        if (painted.isNotEmpty()) {
            lastRefreshIds = painted.sorted().joinToString(",")
        }
    }

    fun summary(): String {
        if (lastRefreshTimeMs == 0L) return "v2: no refresh yet"
        val ageSec = (System.currentTimeMillis() - lastRefreshTimeMs) / 1000
        return "v2: ids=[$lastRefreshIds] reason=$lastRefreshReason " +
            "v=$lastRefreshVersion gen=$lastRefreshGen ${ageSec}s ago"
    }
}
