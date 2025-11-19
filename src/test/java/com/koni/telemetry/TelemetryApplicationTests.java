package com.koni.telemetry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.kafka.consumer.auto-startup=false"
})
class TelemetryApplicationTests {

	@MockBean
	private KafkaTemplate<?, ?> kafkaTemplate;

	@Test
	void contextLoads() {
		// Simple smoke test to verify Spring context loads successfully with H2
	}

}
