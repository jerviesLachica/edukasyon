package com.edukasyon.studentai.core.profile

import com.edukasyon.studentai.domain.model.ProfileEditPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditPolicyTest {

    @Test
    fun canEditProfile_whenNeverEdited_returnsTrue() {
        assertTrue(ProfileEditPolicy.canEditProfile(now = 1_000_000L, lastEditAt = null))
    }

    @Test
    fun canEditProfile_withinOneDay_returnsFalse() {
        val lastEdit = 1_000_000L
        val hoursLater = lastEdit + (12L * 3_600_000)
        assertFalse(ProfileEditPolicy.canEditProfile(hoursLater, lastEdit))
    }

    @Test
    fun canEditProfile_afterOneDay_returnsTrue() {
        val lastEdit = 1_000_000L
        val oneDayLater = lastEdit + ProfileEditPolicy.COOLDOWN_MS
        assertTrue(ProfileEditPolicy.canEditProfile(oneDayLater, lastEdit))
    }

    @Test
    fun daysUntilNextEdit_returnsOneWhenPartialDayRemains() {
        val lastEdit = 0L
        // 1 hour after the edit, 23 hours (a partial day) of cooldown remain -> 1.
        val oneHourLater = 3_600_000L
        assertTrue(ProfileEditPolicy.daysUntilNextEdit(oneHourLater, lastEdit) == 1)
    }
}
