package sg.edu.nus.iss.canmakan.features.auth.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PendingOnboardingStoreTest {
    @Test
    fun pendingIntentSurvivesNormalNavigationThroughSharedStoreInstance() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("  Person@Example.COM ")

        val valueSeenAfterNavigation = store.peek()

        assertEquals("person@example.com", valueSeenAfterNavigation?.accountEmail)
    }

    @Test
    fun pendingDataContainsNoPasswordTokenOrUserId() {
        val fields = PendingDietaryOnboarding::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }

        assertEquals(listOf("accountEmail", "requestId"), fields)
        assertFalse(fields.any { it.contains("password", true) })
        assertFalse(fields.any { it.contains("token", true) })
        assertFalse(fields.any { it.contains("userId", true) })
    }

    @Test
    fun clearRemovesPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("person@example.com")

        store.clear()

        assertNull(store.peek())
    }

    @Test
    fun matchingAccountCanReadPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("person@example.com")

        assertEquals(
            "person@example.com",
            store.peekForAccount(" PERSON@example.com ")?.accountEmail,
        )
    }

    @Test
    fun differentAccountCannotReadAndInvalidatesPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("person@example.com")

        assertNull(store.peekForAccount("other@example.com"))
        assertNull(store.peek())
    }

    @Test
    fun staleAccountCannotClearNewerAccountsIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("new@example.com")

        store.clearForAccount("old@example.com")

        assertEquals("new@example.com", store.peekForAccount("new@example.com")?.accountEmail)
    }

    @Test
    fun staleSameAccountRequestCannotClearNewerIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("person@example.com")
        val oldRequest = requireNotNull(store.peek())
        store.requestDietarySetup("person@example.com")

        store.clearIfCurrent(oldRequest)

        assertFalse(store.peekForAccount("person@example.com")?.requestId == oldRequest.requestId)
    }
}
