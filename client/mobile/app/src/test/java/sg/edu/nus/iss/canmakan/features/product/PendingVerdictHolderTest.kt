package sg.edu.nus.iss.canmakan.features.product

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sg.edu.nus.iss.canmakan.features.product.model.Product
import sg.edu.nus.iss.canmakan.features.product.model.ScanVerdict
import sg.edu.nus.iss.canmakan.features.product.model.VerdictDetail

class PendingVerdictHolderTest {

    @Test
    fun setThenClearReplacesTheHeldVerdict() {
        val holder = PendingVerdictHolder()
        val detail = VerdictDetail(
            product = Product(productName = "Rice", barcode = "111"),
            verdict = ScanVerdict.SAFE,
        )

        assertNull(holder.pendingVerdict.value)
        holder.set(detail)
        assertEquals(detail, holder.pendingVerdict.value)
        holder.clear()
        assertNull(holder.pendingVerdict.value)
    }
}
