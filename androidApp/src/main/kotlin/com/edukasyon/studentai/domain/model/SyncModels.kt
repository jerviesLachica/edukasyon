package com.edukasyon.studentai.domain.model

data class SyncSummary(
    val pushed: Int = 0,
    val pulled: Int = 0,
    val syncedAt: Long = System.currentTimeMillis(),
)

sealed class SyncResult {
    data class Success(val summary: SyncSummary) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data object Offline : SyncResult()
    data object NotAuthenticated : SyncResult()
}
