package com.azka.notifications.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
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

	@Test
	void createWithConflictingBodyReturns409() throws Exception {
		when(service.create(eq("key-1"), any())).thenThrow(new IdempotencyConflictException("key-1"));

		mockMvc.perform(post("/api/notifications").header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isConflict());
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
