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
    fun canEditProfile_withinSevenDays_returnsFalse() {
        val lastEdit = 1_000_000L
        val threeDaysLater = lastEdit + (3L * 86_400_000)
        assertFalse(ProfileEditPolicy.canEditProfile(threeDaysLater, lastEdit))
    }

    @Test
    fun canEditProfile_afterSevenDays_returnsTrue() {
        val lastEdit = 1_000_000L
        val sevenDaysLater = lastEdit + ProfileEditPolicy.COOLDOWN_MS
        assertTrue(ProfileEditPolicy.canEditProfile(sevenDaysLater, lastEdit))
    }

    @Test
    fun daysUntilNextEdit_roundsUpPartialDays() {
        val lastEdit = 0L
        val oneDayLater = 86_400_000L
        assertTrue(ProfileEditPolicy.daysUntilNextEdit(oneDayLater, lastEdit) == 6)
    }
}
