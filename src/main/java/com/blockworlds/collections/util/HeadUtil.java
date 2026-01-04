package com.blockworlds.collections.util;

import com.blockworlds.collections.model.CollectibleTier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for creating custom player heads with Base64 textures.
 */
public class HeadUtil {

    // Cache for created profiles to avoid recreating them
    private static final Map<String, PlayerProfile> profileCache = new ConcurrentHashMap<>();

    // Default head textures for each tier (yellow/green/blue/purple question marks)
    private static final Map<CollectibleTier, String> DEFAULT_TEXTURES = Map.of(
            CollectibleTier.COMMON, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNlODM1OTM1ZTk1YTNiNGVhNjc0NmEyZThiZWI5MGRmYzc5ZDI3OWEzNjdmNmRjM2YzM2U1ZjVlZjQifX19",
            CollectibleTier.UNCOMMON, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGE5OTM0MmUyYzczNDVkN2I0MWYzMmI4YjJlYTI5NjY0YzA4NzNkYzM3NDg5ZTQwZDFiMTUzZmQ0MDRjIn19fQ==",
            CollectibleTier.RARE, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzM2ODZhZTUzYjNiY2RhYzQ2OTNhMzg0MjI3MTJkZGE4NWI2ZDQyNTNkMzVjNGI5ZjY5NGNkODMzNTU1OGMifX19",
            CollectibleTier.EVENT, "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY0N2I3ZWZiNTJlZjIzYjY3OTQ2MzllNTRmZTIzNDU5ZTBlOGExMzJiY2QzNTY1MTAzY2IzOTc1ZGM4ZjcifX19"
    );

    /**
     * Create a player head with a custom texture.
     *
     * @param base64Texture The Base64 encoded texture data
     * @return ItemStack of the custom head
     */
    public static ItemStack createHead(String base64Texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta == null) {
            return head;
        }

        PlayerProfile profile = getOrCreateProfile(base64Texture);
        if (profile != null) {
            meta.setOwnerProfile(profile);
        }

        head.setItemMeta(meta);
        return head;
    }

    /**
     * Create a collectible head for a specific tier.
     *
     * @param tier The collectible tier
     * @return ItemStack of the tier-appropriate head
     */
    public static ItemStack createCollectibleHead(CollectibleTier tier) {
        String texture = DEFAULT_TEXTURES.get(tier);
        return createHead(texture);
    }

    /**
     * Create a collectible head with a custom texture, falling back to tier default.
     *
     * @param tier          The collectible tier (for fallback)
     * @param customTexture Custom Base64 texture, or null/empty to use tier default
     * @return ItemStack of the head
     */
    public static ItemStack createCollectibleHead(CollectibleTier tier, String customTexture) {
        if (customTexture == null || customTexture.isBlank()) {
            return createCollectibleHead(tier);
        }

        // Try custom texture first, fall back to tier default if it fails
        PlayerProfile profile = getOrCreateProfile(customTexture);
        if (profile == null) {
            // Custom texture failed, use tier default
            Bukkit.getLogger().warning("[Collections] Invalid head texture, using tier default");
            return createCollectibleHead(tier);
        }

        // Create head with the custom profile
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(profile);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Get or create a PlayerProfile with the given texture.
     *
     * @param base64Texture The Base64 encoded texture data
     * @return The PlayerProfile, or null if creation failed
     */
    private static PlayerProfile getOrCreateProfile(String base64Texture) {
        return profileCache.computeIfAbsent(base64Texture, texture -> {
            try {
                // Decode the Base64 to get the URL
                String decoded = new String(Base64.getDecoder().decode(texture));
                // Extract URL from JSON: {"textures":{"SKIN":{"url":"..."}}}
                String url = extractTextureUrl(decoded);

                if (url == null) {
                    return null;
                }

                // Create a new profile with a random UUID
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();
                textures.setSkin(URI.create(url).toURL());
                profile.setTextures(textures);

                return profile;
            } catch (MalformedURLException | IllegalArgumentException e) {
                return null;
            }
        });
    }

    /**
     * Extract the texture URL from the decoded Base64 JSON.
     *
     * @param json The decoded JSON string
     * @return The texture URL, or null if not found
     */
    private static String extractTextureUrl(String json) {
        // Simple extraction without a full JSON parser
        // Looking for: "url":"http://textures.minecraft.net/texture/..."
        int urlStart = json.indexOf("\"url\":\"");
        if (urlStart == -1) {
            return null;
        }

        urlStart += 7; // Skip past "url":"
        int urlEnd = json.indexOf("\"", urlStart);
        if (urlEnd == -1) {
            return null;
        }

        return json.substring(urlStart, urlEnd);
    }

    /**
     * Get the default texture for a tier.
     *
     * @param tier The tier
     * @return Base64 encoded texture
     */
    public static String getDefaultTexture(CollectibleTier tier) {
        return DEFAULT_TEXTURES.get(tier);
    }

    /**
     * Clear the profile cache (for reloading).
     */
    public static void clearCache() {
        profileCache.clear();
    }
}
