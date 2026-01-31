package com.tunneller.integration;

import com.tunneller.router.RouterConfig;
import com.tunneller.router.RoutingRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple configuration tests without Testcontainers
 */
public class SimpleConfigTest {

    @Test
    @DisplayName("Test: Routing Rule Priority Sorting")
    void testPrioritySorting() {
        RouterConfig config = RouterConfig.getInstance();
        config.getRoutingRules().clear();

        // Add rules in random order
        RoutingRule rule100 = new RoutingRule("/*", "localhost", 8080, "Fallback", false, 100);
        RoutingRule rule1 = new RoutingRule("/api/*", "localhost", 8081, "API", false, 1);
        RoutingRule rule50 = new RoutingRule("/admin/*", "localhost", 8083, "Admin", false, 50);

        config.addRoutingRule(rule100);
        config.addRoutingRule(rule1);
        config.addRoutingRule(rule50);

        // Get sorted rules
        var rules = config.getRoutingRules();

        // Verify ascending order (lower number = higher priority)
        assertTrue(rules.size() >= 3);
        assertEquals(1, rules.get(0).getPriority(), "First rule should have priority 1");
        assertEquals(50, rules.get(1).getPriority(), "Second rule should have priority 50");
        assertEquals(100, rules.get(2).getPriority(), "Third rule should have priority 100");

        System.out.println("✅ Priority sorting test passed (ASC: 1, 50, 100)");
    }

    @Test
    @DisplayName("Test: Route Matching Logic")
    void testRouteMatching() {
        RoutingRule apiRule = new RoutingRule("/api/*", "localhost", 8081, "API", false, 1);
        RoutingRule exactRule = new RoutingRule("/admin", "localhost", 8083, "Admin");
        RoutingRule fallbackRule = new RoutingRule("/*", "localhost", 8080, "Fallback");

        // Test wildcard matching
        assertTrue(apiRule.matches("/api/users"));
        assertTrue(apiRule.matches("/api/posts/123"));
        assertFalse(apiRule.matches("/admin"));

        // Test exact matching
        assertTrue(exactRule.matches("/admin"));
        assertFalse(exactRule.matches("/admin/settings"));

        // Test fallback matching
        assertTrue(fallbackRule.matches("/"));
        assertTrue(fallbackRule.matches("/anything"));

        System.out.println("✅ Route matching test passed");
    }
}
