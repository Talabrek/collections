package com.blockworlds.collections.web.api.dto;

import java.util.List;

/**
 * DTO for validation error responses.
 *
 * RFC 7807 Problem Details inspired response format for validation failures.
 * Contains list of field-specific errors for client-side form handling.
 *
 * @param type   Error type identifier (always "validation_error")
 * @param title  Human-readable error summary
 * @param status HTTP status code (always 400 for validation errors)
 * @param errors List of field-specific validation errors
 */
public record ValidationErrorResponse(
    String type,
    String title,
    int status,
    List<FieldError> errors
) {
    /**
     * Create a validation error response with the given field errors.
     *
     * @param errors Field-specific validation errors
     * @return ValidationErrorResponse with standard type/title/status
     */
    public static ValidationErrorResponse of(List<FieldError> errors) {
        return new ValidationErrorResponse(
            "validation_error",
            "Validation failed",
            400,
            errors
        );
    }
}
