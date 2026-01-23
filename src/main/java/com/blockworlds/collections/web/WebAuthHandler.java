package com.blockworlds.collections.web;

import at.favre.lib.crypto.bcrypt.BCrypt;
import io.javalin.Javalin;
import io.javalin.security.BasicAuthCredentials;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

/**
 * HTTP Basic Authentication handler for API routes.
 *
 * Validates requests against a BCrypt-hashed password stored in config.
 * Static files are NOT protected (served before route matching).
 */
public class WebAuthHandler {

    private final String passwordHash;

    /**
     * Create an auth handler with the given BCrypt password hash.
     *
     * @param passwordHash The BCrypt hash to validate passwords against
     */
    public WebAuthHandler(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Registers authentication middleware for API routes.
     *
     * Uses beforeMatched() which runs after static file resolution,
     * so static files at / are public while /api/* requires auth.
     *
     * @param app The Javalin application instance
     */
    public void register(Javalin app) {
        app.beforeMatched(ctx -> {
            // Only protect /api/ routes
            if (ctx.path().startsWith("/api/")) {
                validateAuth(ctx);
            }
        });
    }

    /**
     * Validate HTTP Basic Auth credentials against stored hash.
     *
     * @param ctx The Javalin context
     * @throws UnauthorizedResponse if authentication fails
     */
    private void validateAuth(Context ctx) {
        // Get Authorization header - returns null if not present or invalid format
        BasicAuthCredentials creds = ctx.basicAuthCredentials();

        if (creds == null) {
            challengeAuth(ctx);
            return;
        }

        // Username is ignored - single password auth (any username works)
        BCrypt.Result result = BCrypt.verifyer()
            .verify(creds.getPassword().toCharArray(), passwordHash);

        if (!result.verified) {
            challengeAuth(ctx);
        }
    }

    /**
     * Send 401 challenge with WWW-Authenticate header.
     *
     * This triggers the browser's native login dialog.
     *
     * @param ctx The Javalin context
     * @throws UnauthorizedResponse always thrown after setting headers
     */
    private void challengeAuth(Context ctx) {
        ctx.header("WWW-Authenticate", "Basic realm=\"Collections Admin\", charset=\"UTF-8\"");
        ctx.status(401);
        throw new UnauthorizedResponse("Authentication required");
    }
}
