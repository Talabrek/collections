package com.blockworlds.collections.web.api.dto;

/**
 * DTO for individual field validation errors.
 *
 * Used within ValidationErrorResponse to provide specific error details.
 * Supports nested field paths for array items (e.g., "items[0].material").
 *
 * @param field   Field path with dot notation and array indices (e.g., "items[0].material")
 * @param message Human-readable error message
 * @param code    Machine-readable error code ("required", "invalid_enum", "invalid_material", "invalid_format")
 */
public record FieldError(
    String field,
    String message,
    String code
) {}
