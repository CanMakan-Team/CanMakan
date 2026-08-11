package sg.edu.nus.iss.canmakan.features.auth.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PendingOnboardingStoreTest {
    @Test
    fun pendingIntentSurvivesNormalNavigationThroughSharedStoreInstance() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("  Person Name  ", "  Person@Example.COM ")

        val valueSeenAfterNavigation = store.peek()

        assertEquals("Person Name", valueSeenAfterNavigation?.profileName)
        assertEquals("person@example.com", valueSeenAfterNavigation?.accountEmail)
    }

    @Test
    fun pendingDataContainsNoPasswordTokenOrUserId() {
        val fields = PendingDietaryOnboarding::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }

        assertEquals(listOf("profileName", "accountEmail", "requestId"), fields)
        assertFalse(fields.any { it.contains("password", true) })
        assertFalse(fields.any { it.contains("token", true) })
        assertFalse(fields.any { it.contains("userId", true) })
    }

    @Test
    fun clearRemovesPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("Person Name", "person@example.com")

        store.clear()

        assertNull(store.peek())
    }

    @Test
    fun laterAuthenticatedSetupMayStartWithAnEmptyEditableName() {
        val store = PendingOnboardingStore()

        store.requestDietarySetup("", "person@example.com")

        assertEquals("", store.peek()?.profileName)
    }

    @Test
    fun matchingAccountCanReadPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("Person Name", "person@example.com")

        assertEquals(
            "Person Name",
            store.peekForAccount(" PERSON@example.com ")?.profileName,
        )
    }

    @Test
    fun differentAccountCannotReadAndInvalidatesPendingIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("Person Name", "person@example.com")

        assertNull(store.peekForAccount("other@example.com"))
        assertNull(store.peek())
    }

    @Test
    fun staleAccountCannotClearNewerAccountsIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("New Person", "new@example.com")

        store.clearForAccount("old@example.com")

        assertEquals("New Person", store.peekForAccount("new@example.com")?.profileName)
    }

    @Test
    fun staleSameAccountRequestCannotClearNewerIntent() {
        val store = PendingOnboardingStore()
        store.requestDietarySetup("Old Setup", "person@example.com")
        val oldRequest = requireNotNull(store.peek())
        store.requestDietarySetup("New Setup", "person@example.com")

        store.clearIfCurrent(oldRequest)

        assertEquals("New Setup", store.peekForAccount("person@example.com")?.profileName)
    }
}
