/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package smile.auth;

/**
 * Validates post-OAuth browser redirects to same-origin SPA entry points.
 */
public final class OAuthReturnPath {
    /** Standalone chat UI. */
    public static final String CHAT = "/chat/";
    /** Inference shell at site root (in-app model selection, no path segment). */
    public static final String INFER = "/";

    private OAuthReturnPath() {}

    /**
     * Normalizes a client-supplied return path to an allowed SPA route.
     *
     * @param returnTo optional path from the login request.
     * @return safe path beginning with {@code /}.
     */
    public static String normalize(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return CHAT;
        }
        String path = returnTo.trim();
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        if (!path.startsWith("/") || path.startsWith("//")) {
            return CHAT;
        }
        if (path.contains("..") || path.contains("\\")) {
            return CHAT;
        }
        return switch (path) {
            case "/", "/index.html" -> INFER;
            case "/chat", "/chat/", "/chat/index.html" -> CHAT;
            default -> CHAT;
        };
    }
}
