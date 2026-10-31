package com.azka.notifications.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

	private static final String VALID_BODY = """
			{ "producerId": "billing", "recipientEmail": "max@example.com", "useCase": "dunning", "locale": "de" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private NotificationService service;

	@Test
	void createReturns202WithIdAndStatus() throws Exception {
		var id = UUID.randomUUID();
		when(service.create(eq("key-1"), any()))
			.thenReturn(new CreateNotificationResponse(id, NotificationStatus.PENDING));

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.id").value(id.toString()))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void createWithoutIdempotencyKeyReturns400() throws Exception {
		mockMvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
	}

	@Test
	void createWithInvalidFieldsReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "producerId": "", "recipientEmail": "not-an-email", "useCase": "dunning", "locale": "es" }
						"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.producerId").exists())
			.andExpect(jsonPath("$.errors.recipientEmail").value("recipientEmail must be a valid email address"))
			.andExpect(jsonPath("$.errors.locale").value("locale must be one of de, en, fr, it, nl"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Billing", "bill ing", "billing ", "-billing", ".billing", "bill/ing", "billé",
			"bill\\nx" })
	void createWithNonSlugProducerOrUseCaseReturns400(String value) throws Exception {
		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(value, value)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.producerId").value("producerId " + CreateNotificationRequest.SLUG_RULE))
			.andExpect(jsonPath("$.errors.useCase").value("useCase " + CreateNotificationRequest.SLUG_RULE));
	}

	@Test
	void createWithOver64CharsReturns400() throws Exception {
		var tooLong = "a".repeat(65);

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(tooLong, tooLong)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.producerId").exists())
			.andExpect(jsonPath("$.errors.useCase").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = { "billing", "password-reset", "order_v2.eu", "9lives" })
	void createAcceptsSlugProducerAndUseCase(String value) throws Exception {
		when(service.create(eq("key-1"), any()))
			.thenReturn(new CreateNotificationResponse(UUID.randomUUID(), NotificationStatus.PENDING));

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(value, value)))
			.andExpect(status().isAccepted());
	}

	@Test
	void createAcceptsExactly64Chars() throws Exception {
		var max = "a".repeat(64);
		when(service.create(eq("key-1"), any()))
			.thenReturn(new CreateNotificationResponse(UUID.randomUUID(), NotificationStatus.PENDING));

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(max, max)))
			.andExpect(status().isAccepted());
	}

	private static String body(String producerId, String useCase) {
		return """
				{ "producerId": "%s", "recipientEmail": "max@example.com", "useCase": "%s", "locale": "de" }
				""".formatted(producerId, useCase);
	}

	@Test
	void createWithConflictingBodyReturns409() throws Exception {
		when(service.create(eq("key-1"), any())).thenThrow(new IdempotencyConflictException("key-1"));

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isConflict());
	}

	@Test
	void listUsesDefaultPageAndSizeAndReturnsPageShape() throws Exception {
		var item = new NotificationResponse(UUID.randomUUID(), "billing", "max@example.com", "dunning", "de",
				NotificationStatus.PENDING, Instant.parse("2026-10-31T10:00:00Z"));
		when(service.list(null, 0, 20)).thenReturn(new PageResponse<>(List.of(item), 0, 20, 41, 3));

		mockMvc.perform(get("/api/notifications"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(item.id().toString()))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(41))
			.andExpect(jsonPath("$.totalPages").value(3));
	}

	@Test
	void listPassesStatusPageAndSize() throws Exception {
		when(service.list(NotificationStatus.BOUNCED, 2, 50)).thenReturn(new PageResponse<>(List.of(), 2, 50, 0, 0));

		mockMvc.perform(get("/api/notifications").param("status", "BOUNCED").param("page", "2").param("size", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(2));
	}

	@Test
	void listWithTooLargeSizeReturns400() throws Exception {
		when(service.list(null, 0, 101)).thenThrow(new InvalidPageRequestException("size must be between 1 and 100"));

		mockMvc.perform(get("/api/notifications").param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
	}

	@Test
	void listWithNonNumericPageReturns400() throws Exception {
		mockMvc.perform(get("/api/notifications").param("page", "abc")).andExpect(status().isBadRequest());
	}

	@Test
	void listWithInvalidStatusReturns400() throws Exception {
		mockMvc.perform(get("/api/notifications").param("status", "NOPE")).andExpect(status().isBadRequest());
	}

	@Test
	void eventOnUnknownIdReturns404() throws Exception {
		var id = UUID.randomUUID();
		when(service.applyEvent(id, "sent")).thenThrow(new NotificationNotFoundException(id));

		mockMvc.perform(post("/api/notifications/{id}/events", id).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"event\": \"sent\" }"))
			.andExpect(status().isNotFound());
	}

	@Test
	void unknownEventReturns400() throws Exception {
		var id = UUID.randomUUID();
		when(service.applyEvent(id, "opened")).thenThrow(new UnknownEventException("opened"));

		mockMvc.perform(post("/api/notifications/{id}/events", id).contentType(MediaType.APPLICATION_JSON)
				.content("{ \"event\": \"opened\" }"))
			.andExpect(status().isBadRequest());
	}

}
