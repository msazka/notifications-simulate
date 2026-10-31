package com.azka.notifications.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse validation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return new ErrorResponse("Validation failed", errors);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse missingHeader(MissingRequestHeaderException ex) {
		return ErrorResponse.of(ex.getHeaderName() + " header is required");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse typeMismatch(MethodArgumentTypeMismatchException ex) {
		return ErrorResponse.of("Invalid value for '" + ex.getName() + "': " + ex.getValue());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse unreadable(HttpMessageNotReadableException ex) {
		return ErrorResponse.of("Malformed request body");
	}

	@ExceptionHandler({ MissingIdempotencyKeyException.class, UnknownEventException.class,
			InvalidPageRequestException.class })
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse badRequest(RuntimeException ex) {
		return ErrorResponse.of(ex.getMessage());
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse notFound(NotificationNotFoundException ex) {
		return ErrorResponse.of(ex.getMessage());
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse conflict(IdempotencyConflictException ex) {
		return ErrorResponse.of(ex.getMessage());
	}

}
