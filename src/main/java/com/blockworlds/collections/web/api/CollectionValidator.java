package com.blockworlds.collections.web.api;

import com.blockworlds.collections.model.CollectibleTier;
import com.blockworlds.collections.web.api.dto.CollectionRequest;
import com.blockworlds.collections.web.api.dto.FieldError;
import com.blockworlds.collections.web.api.dto.ItemRequest;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates CollectionRequest and returns field-specific errors.
 *
 * Provides detailed validation with path notation for nested fields
 * (e.g., "items[0].material") for client-side form error display.
 */
public class CollectionValidator {

    /**
     * Pattern for valid collection/item IDs.
     * Lowercase alphanumeric and underscores only.
     * Prevents path traversal (no dots, slashes, or special characters).
     */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]+$");

    /**
     * Result of validation containing validity flag and any errors.
     *
     * @param isValid Whether the request passed all validation rules
     * @param errors  List of field-specific errors (empty if valid)
     */
    public record ValidationResult(boolean isValid, List<FieldError> errors) {
        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<FieldError> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }

    /**
     * Validate a collection request.
     *
     * @param request The collection request to validate
     * @return ValidationResult with validity flag and any field errors
     */
    public ValidationResult validate(CollectionRequest request) {
        List<FieldError> errors = new ArrayList<>();

        if (request == null) {
            errors.add(new FieldError("request", "Request body is required", "required"));
            return ValidationResult.invalid(errors);
        }

        // Validate id: required, valid format
        validateId(request.id(), "id", errors);

        // Validate name: required, non-blank
        if (request.name() == null || request.name().isBlank()) {
            errors.add(new FieldError("name", "Name is required", "required"));
        }

        // Validate tier: if provided, must be valid enum
        if (request.tier() != null && !request.tier().isBlank()) {
            if (!isValidTier(request.tier())) {
                errors.add(new FieldError("tier",
                    "Invalid tier. Must be one of: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, EVENT",
                    "invalid_enum"));
            }
        }

        // Validate icon: if provided, must be valid Material
        if (request.icon() != null && !request.icon().isBlank()) {
            if (!isValidMaterial(request.icon())) {
                errors.add(new FieldError("icon",
                    "Invalid material: " + request.icon(),
                    "invalid_material"));
            }
        }

        // Validate items: required, at least 1 item
        if (request.items() == null || request.items().isEmpty()) {
            errors.add(new FieldError("items", "At least one item is required", "required"));
        } else {
            // Validate each item
            for (int i = 0; i < request.items().size(); i++) {
                validateItem(request.items().get(i), i, errors);
            }
        }

        // Rewards are optional - no validation required

        // Zones and requires are optional lists - no validation required

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(errors);
    }

    /**
     * Validate an item request.
     *
     * @param item   The item to validate
     * @param index  Item index for error path
     * @param errors Error list to add to
     */
    private void validateItem(ItemRequest item, int index, List<FieldError> errors) {
        String prefix = "items[" + index + "]";

        if (item == null) {
            errors.add(new FieldError(prefix, "Item cannot be null", "required"));
            return;
        }

        // Validate id: required, valid format
        validateId(item.id(), prefix + ".id", errors);

        // Validate name: required, non-blank
        if (item.name() == null || item.name().isBlank()) {
            errors.add(new FieldError(prefix + ".name", "Item name is required", "required"));
        }

        // Validate material: if provided, must be valid Material
        if (item.material() != null && !item.material().isBlank()) {
            if (!isValidMaterial(item.material())) {
                errors.add(new FieldError(prefix + ".material",
                    "Invalid material: " + item.material(),
                    "invalid_material"));
            }
        }

        // Validate weight: if provided, must be > 0
        if (item.weight() != null && item.weight() <= 0) {
            errors.add(new FieldError(prefix + ".weight",
                "Weight must be greater than 0",
                "invalid_value"));
        }
    }

    /**
     * Validate an ID field.
     *
     * @param id     The ID value to validate
     * @param field  Field path for error message
     * @param errors Error list to add to
     */
    private void validateId(String id, String field, List<FieldError> errors) {
        if (id == null || id.isBlank()) {
            errors.add(new FieldError(field, "ID is required", "required"));
            return;
        }

        if (!ID_PATTERN.matcher(id).matches()) {
            errors.add(new FieldError(field,
                "ID must contain only lowercase letters, numbers, and underscores",
                "invalid_format"));
        }
    }

    /**
     * Check if a string is a valid CollectibleTier enum value.
     *
     * @param tier Tier name to check (case-insensitive)
     * @return true if valid tier
     */
    private boolean isValidTier(String tier) {
        try {
            CollectibleTier.valueOf(tier.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if a string is a valid Material enum value.
     *
     * @param material Material name to check (case-insensitive)
     * @return true if valid material
     */
    private boolean isValidMaterial(String material) {
        try {
            Material.valueOf(material.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
