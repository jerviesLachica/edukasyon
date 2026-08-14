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
        val oneDayLater = 86_400_000L
        assertTrue(ProfileEditPolicy.daysUntilNextEdit(oneDayLater, lastEdit) == 1)
    }
}
