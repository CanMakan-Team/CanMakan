package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UC5: PythonTfidfRankClient")
class PythonTfidfRankClientTest {

    @Test
    void isNotConfiguredWhenRankerUrlEmpty() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                new SubstituteDiscoveryProfiles(), "", 500, 2000);
        assertFalse(client.isConfigured());
    }

    @Test
    void isConfiguredWhenRankerUrlPresent() {
        PythonTfidfRankClient client = new PythonTfidfRankClient(
                new SubstituteDiscoveryProfiles(), "http://127.0.0.1:8091", 500, 2000);
        assertTrue(client.isConfigured());
    }
}
