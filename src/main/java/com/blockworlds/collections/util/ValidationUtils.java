package com.blockworlds.collections.util;

import java.util.regex.Pattern;

/**
 * Utility class for validating collection and item identifiers.
 */
public final class ValidationUtils {

    /**
     * Pattern for valid IDs: lowercase letter followed by lowercase alphanumeric or underscore.
     * Examples: "forest_floor", "acorn_cap", "ancient_depths", "item1"
     */
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private ValidationUtils() {
        // Private constructor prevents instantiation
    }

    /**
     * Check if a string is a valid collection/item ID.
     * Valid IDs: lowercase alphanumeric with underscores, starting with a letter.
     *
     * @param id The ID to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidId(String id) {
        return id != null && VALID_ID_PATTERN.matcher(id).matches();
    }

    /**
     * Validate and return an ID, throwing if invalid.
     *
     * @param id      The ID to validate
     * @param context Description for error message (e.g., "Collection", "Item")
     * @return The validated ID (unchanged)
     * @throws IllegalArgumentException if ID is null, blank, or invalid format
     */
    public static String requireValidId(String id, String context) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(context + " ID cannot be null or blank");
        }
        if (!isValidId(id)) {
            throw new IllegalArgumentException(
                context + " ID must be lowercase alphanumeric with underscores, starting with a letter (e.g., 'forest_floor'), got: '" + id + "'"
            );
        }
        return id;
    }
}
