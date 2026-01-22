package com.blockworlds.collections.model;

/**
 * Constants for the JSON export format.
 */
public final class ExportFormat {

    /**
     * Current format version for export files.
     * Increment when making breaking changes to the JSON structure.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * Export type for single player exports.
     */
    public static final String EXPORT_TYPE_SINGLE = "SINGLE";

    /**
     * Export type for full database exports.
     */
    public static final String EXPORT_TYPE_FULL = "FULL";

    private ExportFormat() {
        // Prevent instantiation
    }
}
