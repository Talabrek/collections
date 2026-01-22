package com.blockworlds.collections.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validating an import file.
 */
public class ValidationResult {
    private final List<String> errors = new ArrayList<>();
    private int playerCount = 0;
    private int formatVersion = 0;
    private boolean valid = true;

    public void addError(String error) {
        errors.add(error);
        valid = false;
    }

    public void setPlayerCount(int count) {
        this.playerCount = count;
    }

    public void setFormatVersion(int version) {
        this.formatVersion = version;
    }

    public boolean isValid() {
        return valid && errors.isEmpty();
    }

    public List<String> getErrors() {
        return List.copyOf(errors);
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public int getFormatVersion() {
        return formatVersion;
    }
}
