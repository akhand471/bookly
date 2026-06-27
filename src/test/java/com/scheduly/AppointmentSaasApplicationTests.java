package com.scheduly;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration smoke test that verifies the entire Spring application context loads successfully.
 * This test connects to the real PostgreSQL and Redis instances configured in application.yml.
 * If this test passes, all beans are wired correctly.
 */
@SpringBootTest
@TestPropertySource(properties = {
    // Use test-safe JWT secret — base64 encoded 512-bit key
    "app.jwt.secret=dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTNTEyLWFsZ29yaXRobS10ZXN0LW9ubHk=",
    "app.jwt.expiration-ms=3600000",
    "app.jwt.refresh-expiration-ms=86400000"
})
class AppointmentSaasApplicationTests {

    @Test
    void contextLoads() {
        // Verifies all Spring beans wire correctly (Security, JPA, Flyway, Redis, MapStruct)
    }

}
