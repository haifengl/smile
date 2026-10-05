/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.ForbiddenException;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Utility for verifying that administrative/control requests originate from localhost.
 *
 * <p>To prevent header spoofing (e.g. forged {@code X-Forwarded-For} or {@code Host}),
 * this guard inspects the direct connection remote address from the underlying socket.
 *
 * @author Haifeng Li
 */
public final class LocalhostGuard {

    private LocalhostGuard() {}

    /**
     * Asserts that the incoming request originates from a loopback/localhost address.
     *
     * @param routingContext the Vert.x routing context.
     * @throws ForbiddenException if the remote address is not localhost/loopback.
     */
    public static void requireLocalhost(RoutingContext routingContext) {
        if (!isLocalhost(routingContext)) {
            throw new ForbiddenException("Forbidden: endpoint is only accessible from localhost");
        }
    }

    /**
     * Checks whether the request socket connection is from localhost / loopback.
     *
     * @param routingContext the Vert.x routing context.
     * @return {@code true} if loopback address.
     */
    public static boolean isLocalhost(RoutingContext routingContext) {
        if (routingContext == null) {
            return false;
        }
        HttpServerRequest request = routingContext.request();
        if (request == null) {
            return false;
        }
        SocketAddress remoteAddress = request.remoteAddress();
        if (remoteAddress == null) {
            return false;
        }
        String host = remoteAddress.hostAddress();
        return isLoopbackAddress(host);
    }

    /**
     * Checks if the host address string is a loopback address.
     *
     * @param hostAddress the IP address string.
     * @return {@code true} if loopback.
     */
    public static boolean isLoopbackAddress(String hostAddress) {
        if (hostAddress == null || hostAddress.isBlank()) {
            return false;
        }
        String cleanHost = hostAddress.trim();
        if (cleanHost.regionMatches(true, 0, "::ffff:", 0, 7)) {
            cleanHost = cleanHost.substring(7);
        }
        if ("localhost".equalsIgnoreCase(cleanHost) || "127.0.0.1".equals(cleanHost) || "::1".equals(cleanHost) || "0:0:0:0:0:0:0:1".equals(cleanHost)) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(cleanHost);
            return address.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
