package com.tunneller.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoutingRule JSON Deserialization Test")
class RoutingRuleDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should deserialize full JSON to RoutingRule")
    void testFullJsonDeserialization() throws Exception {
        String json = "{\"pathPattern\":\"/admin/*\",\"targetHost\":\"localhost\",\"targetPort\":8080,\"priority\":100,\"description\":\"Admin Service\",\"useSSL\":false,\"stripPrefix\":false,\"forwardHost\":false}";

        RoutingRule rule = objectMapper.readValue(json, RoutingRule.class);

        assertNotNull(rule);
        assertEquals("/admin/*", rule.getPathPattern());
        assertEquals("localhost", rule.getTargetHost());
        assertEquals(8080, rule.getTargetPort());
        assertEquals(100, rule.getPriority());
        assertEquals("Admin Service", rule.getDescription());
        assertFalse(rule.isUseSSL());
        assertFalse(rule.isStripPrefix());
        assertFalse(rule.isForwardHost());
    }

    @Test
    @DisplayName("Should deserialize minimal JSON with defaults")
    void testMinimalJsonWithDefaults() throws Exception {
        // User's exact JSON from error message
        String json = "{\"pathPattern\":\"/admin/*\",\"targetHost\":\"localhost\",\"targetPort\":8080,\"priority\":100,\"description\":\"\",\"useSSL\":false,\"stripPrefix\":false,\"forwardHost\":false}";

        RoutingRule rule = objectMapper.readValue(json, RoutingRule.class);

        assertNotNull(rule);
        assertEquals("/admin/*", rule.getPathPattern());
        assertEquals("localhost", rule.getTargetHost());
        assertEquals(8080, rule.getTargetPort());
        assertEquals(100, rule.getPriority());
        assertEquals("", rule.getDescription());
        assertFalse(rule.isUseSSL());
        assertFalse(rule.isStripPrefix());
        assertFalse(rule.isForwardHost());
    }

    @Test
    @DisplayName("Should deserialize JSON with all boolean flags true")
    void testJsonWithAllFlagsTrue() throws Exception {
        String json = "{\"pathPattern\":\"/api/*\",\"targetHost\":\"backend\",\"targetPort\":3000,\"priority\":1,\"description\":\"API Gateway\",\"useSSL\":true,\"stripPrefix\":true,\"forwardHost\":true}";

        RoutingRule rule = objectMapper.readValue(json, RoutingRule.class);

        assertNotNull(rule);
        assertEquals("/api/*", rule.getPathPattern());
        assertEquals("backend", rule.getTargetHost());
        assertEquals(3000, rule.getTargetPort());
        assertEquals(1, rule.getPriority());
        assertEquals("API Gateway", rule.getDescription());
        assertTrue(rule.isUseSSL());
        assertTrue(rule.isStripPrefix());
        assertTrue(rule.isForwardHost());
    }

    @Test
    @DisplayName("Should serialize RoutingRule to JSON")
    void testSerialization() throws Exception {
        RoutingRule rule = new RoutingRule("/test/*", "localhost", 9090, "Test Service", true, 50, true, true);

        String json = objectMapper.writeValueAsString(rule);

        assertNotNull(json);
        assertTrue(json.contains("\"pathPattern\":\"/test/*\""));
        assertTrue(json.contains("\"targetHost\":\"localhost\""));
        assertTrue(json.contains("\"targetPort\":9090"));
        assertTrue(json.contains("\"priority\":50"));
        assertTrue(json.contains("\"description\":\"Test Service\""));
        assertTrue(json.contains("\"stripPrefix\":true"));
        assertTrue(json.contains("\"forwardHost\":true"));
        assertTrue(json.contains("\"useSSL\":true"));
    }

    @Test
    @DisplayName("Should round-trip: serialize then deserialize")
    void testRoundTrip() throws Exception {
        RoutingRule original = new RoutingRule("/data/*", "dataserver", 5432, "Data API", false, 25, false, true);

        // Serialize
        String json = objectMapper.writeValueAsString(original);

        // Deserialize
        RoutingRule deserialized = objectMapper.readValue(json, RoutingRule.class);

        // Verify they match
        assertEquals(original.getPathPattern(), deserialized.getPathPattern());
        assertEquals(original.getTargetHost(), deserialized.getTargetHost());
        assertEquals(original.getTargetPort(), deserialized.getTargetPort());
        assertEquals(original.getPriority(), deserialized.getPriority());
        assertEquals(original.getDescription(), deserialized.getDescription());
        assertEquals(original.isStripPrefix(), deserialized.isStripPrefix());
        assertEquals(original.isForwardHost(), deserialized.isForwardHost());
        assertEquals(original.isUseSSL(), deserialized.isUseSSL());
    }
}
