package com.blockworlds.collections.web;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.security.SecureRandom;
import java.util.logging.Level;

/**
 * Configuration utilities for the web panel.
 *
 * Handles password generation, hashing, and validation.
 */
public class WebPanelConfig {

    // Characters for password generation (no ambiguous chars like 0/O, 1/l/I)
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    private static final int PASSWORD_LENGTH = 16;
    private static final int BCRYPT_COST = 12;

    private final SecureRandom secureRandom;

    public WebPanelConfig() {
        this.secureRandom = new SecureRandom();
    }

    /**
     * Ensure a password hash is configured in the config.
     *
     * If no password hash exists, generates a random password, hashes it,
     * saves the hash to config, and logs the plaintext password (WARNING level)
     * so the admin can see it once.
     *
     * @param config The plugin configuration
     * @param plugin The plugin instance for saving config and logging
     */
    public void ensurePasswordConfigured(FileConfiguration config, Plugin plugin) {
        String existingHash = config.getString("web-panel.password-hash", "");

        if (existingHash == null || existingHash.isBlank()) {
            // Generate new random password
            String password = generatePassword();

            // Hash the password
            String hash = hashPassword(password);

            // Save hash to config
            config.set("web-panel.password-hash", hash);
            plugin.saveConfig();

            // Log the generated password (shown only once)
            plugin.getLogger().log(Level.WARNING,
                "=======================================================");
            plugin.getLogger().log(Level.WARNING,
                "WEB PANEL PASSWORD GENERATED");
            plugin.getLogger().log(Level.WARNING,
                "Password: " + password);
            plugin.getLogger().log(Level.WARNING,
                "Save this password! It will not be shown again.");
            plugin.getLogger().log(Level.WARNING,
                "To reset: delete password-hash from config.yml and restart");
            plugin.getLogger().log(Level.WARNING,
                "=======================================================");
        }
    }

    /**
     * Generate a random password using SecureRandom.
     *
     * @return A random 16-character password
     */
    private String generatePassword() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(PASSWORD_CHARS.length());
            password.append(PASSWORD_CHARS.charAt(index));
        }
        return password.toString();
    }

    /**
     * Hash a password using BCrypt.
     *
     * @param password The plaintext password
     * @return The BCrypt hash string
     */
    public static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray());
    }

    /**
     * Verify a password against a BCrypt hash.
     *
     * @param password The plaintext password to verify
     * @param hash The BCrypt hash to verify against
     * @return true if the password matches the hash
     */
    public static boolean verifyPassword(String password, String hash) {
        if (password == null || hash == null || hash.isBlank()) {
            return false;
        }
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        return result.verified;
    }
}
