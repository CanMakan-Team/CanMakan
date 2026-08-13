package com.canmakan.backend.product.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("UC5 Tier C: ProductFeatureVectorStore")
class ProductFeatureVectorStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSparseVectorsFromArtifactFile(@TempDir Path dir) throws Exception {
        Path artifact = dir.resolve("vectors.json");
        Files.writeString(artifact, """
                {
                  "version": 1,
                  "products": {
                    "8888200602857": {"milk": 0.8, "fresh": 0.5},
                    "8850025000521": {"soy": 0.9, "unsweetened": 0.7}
                  }
                }
                """);

        ProductFeatureVectorStore store = new ProductFeatureVectorStore(new ObjectMapper(), artifact.toString());

        assertTrue(store.isLoaded());
        assertTrue(store.getVector("8888200602857").isPresent());
        assertEquals(0.8, store.getVector("8888200602857").orElseThrow().get("milk"));
    }

    @Test
    void emptyPathUsesInlineFallbackOnly() {
        ProductFeatureVectorStore store = new ProductFeatureVectorStore(new ObjectMapper(), "");

        assertFalse(store.isLoaded());
        assertTrue(store.getVector("8888200602857").isEmpty());
    }
}
