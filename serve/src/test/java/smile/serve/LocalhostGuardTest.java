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
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalhostGuard}.
 */
public class LocalhostGuardTest {

    @Test
    public void testIsLoopbackAddress() {
        assertTrue(LocalhostGuard.isLoopbackAddress("127.0.0.1"));
        assertTrue(LocalhostGuard.isLoopbackAddress("127.0.0.2"));
        assertTrue(LocalhostGuard.isLoopbackAddress("::1"));
        assertTrue(LocalhostGuard.isLoopbackAddress("::ffff:127.0.0.1"));
        assertTrue(LocalhostGuard.isLoopbackAddress("localhost"));

        assertFalse(LocalhostGuard.isLoopbackAddress("192.168.1.100"));
        assertFalse(LocalhostGuard.isLoopbackAddress("10.0.0.1"));
        assertFalse(LocalhostGuard.isLoopbackAddress("8.8.8.8"));
        assertFalse(LocalhostGuard.isLoopbackAddress(null));
        assertFalse(LocalhostGuard.isLoopbackAddress(""));
        assertFalse(LocalhostGuard.isLoopbackAddress("invalid-ip-###"));
    }

    @Test
    public void testRequireLocalhostAcceptsLoopback() {
        RoutingContext ctx = mockRoutingContext("127.0.0.1");
        assertDoesNotThrow(() -> LocalhostGuard.requireLocalhost(ctx));

        RoutingContext ctxV6 = mockRoutingContext("::1");
        assertDoesNotThrow(() -> LocalhostGuard.requireLocalhost(ctxV6));
    }

    @Test
    public void testRequireLocalhostRejectsExternalIp() {
        RoutingContext ctx = mockRoutingContext("192.168.1.50");
        assertThrows(ForbiddenException.class, () -> LocalhostGuard.requireLocalhost(ctx));
    }

    private static RoutingContext mockRoutingContext(String remoteHost) {
        SocketAddress socketAddress = (SocketAddress) Proxy.newProxyInstance(
                SocketAddress.class.getClassLoader(),
                new Class<?>[]{SocketAddress.class},
                (proxy, method, args) -> {
                    if ("hostAddress".equals(method.getName())) {
                        return remoteHost;
                    }
                    return null;
                }
        );

        HttpServerRequest request = (HttpServerRequest) Proxy.newProxyInstance(
                HttpServerRequest.class.getClassLoader(),
                new Class<?>[]{HttpServerRequest.class},
                (proxy, method, args) -> {
                    if ("remoteAddress".equals(method.getName())) {
                        return socketAddress;
                    }
                    return null;
                }
        );

        return (RoutingContext) Proxy.newProxyInstance(
                RoutingContext.class.getClassLoader(),
                new Class<?>[]{RoutingContext.class},
                (proxy, method, args) -> {
                    if ("request".equals(method.getName())) {
                        return request;
                    }
                    return null;
                }
        );
    }
}
