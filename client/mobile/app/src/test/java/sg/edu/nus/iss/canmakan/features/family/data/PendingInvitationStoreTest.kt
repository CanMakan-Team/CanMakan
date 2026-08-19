package sg.edu.nus.iss.canmakan.features.family.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingInvitationStoreTest {

    @Test
    fun offerIgnoresBlankAndKeepsTrimmedToken() = runTest {
        val store = PendingInvitationStore()
        store.offer("  ")
        store.offer(null)
        assertNull(store.peek())
        store.offer("  tok-1  ")
        assertEquals("tok-1", store.peek())
        assertEquals("tok-1", store.token.first())
    }

    @Test
    fun consumeClearsCurrentToken() {
        val store = PendingInvitationStore()
        store.offer("tok")
        assertEquals("tok", store.consume())
        assertNull(store.peek())
        assertNull(store.consume())
    }

    @Test
    fun clearIfCurrentOnlyRemovesMatchingToken() {
        val store = PendingInvitationStore()
        store.offer("tok-a")
        assertFalse(store.clearIfCurrent("tok-b"))
        assertEquals("tok-a", store.peek())
        assertTrue(store.clearIfCurrent("tok-a"))
        assertNull(store.peek())
        store.offer("tok-c")
        store.clear()
        assertNull(store.peek())
    }
}
