package sg.edu.nus.iss.canmakan.features.family

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.auth.session.AuthAccountKey

class ActiveProfileManagerTest {

    @Test
    fun activeProfileIsBoundToItsAuthenticatedAccount() {
        val manager = ActiveProfileManager()

        manager.switchProfile(accountKey = accountKey(14L, 1L), profileId = 77L)

        assertEquals(
            ActiveProfileManager.Selection(accountKey(14L, 1L), 77L),
            manager.selection.value,
        )
        assertEquals(77L, manager.currentProfileId.value)
        assertTrue(manager.isCurrent(accountKey(14L, 1L), 77L))
    }

    @Test
    fun staleAccountCannotResetNewAccountsProfile() {
        val manager = ActiveProfileManager()
        manager.switchProfile(accountKey = accountKey(22L, 2L), profileId = 88L)

        manager.resetForOwner(accountKey(14L, 1L))

        assertEquals(
            ActiveProfileManager.Selection(accountKey(22L, 2L), 88L),
            manager.selection.value,
        )
        assertEquals(88L, manager.currentProfileId.value)
    }

    @Test
    fun resetClearsBothSelectionAndCompatibilityId() {
        val manager = ActiveProfileManager()
        manager.switchProfile(accountKey = accountKey(14L, 1L), profileId = 77L)

        manager.reset()

        assertNull(manager.selection.value)
        assertEquals(ActiveProfileManager.UNSET_PROFILE_ID, manager.currentProfileId.value)
    }

    @Test
    fun accountAndProfileIdsMustBothBePositive() {
        val manager = ActiveProfileManager()

        assertThrows(IllegalArgumentException::class.java) {
            manager.switchProfile(accountKey = accountKey(0L, 1L), profileId = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            manager.switchProfile(accountKey = accountKey(1L, 1L), profileId = 0L)
        }
    }

    private fun accountKey(userId: Long, generation: Long) =
        AuthAccountKey(userId = userId, sessionGeneration = generation)
}
