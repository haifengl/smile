/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link LocalHosts}.
 *
 * @author Haifeng Li
 */
public class LocalHostsTest {

    @Test
    public void testLoopbackNamesAreLocal() {
        // Given / When / Then
        assertTrue(LocalHosts.isLocalHost("localhost"));
        assertTrue(LocalHosts.isLocalHost("127.0.0.1"));
        assertTrue(LocalHosts.isLocalHost("::1"));
        assertTrue(LocalHosts.isLocalHost("[::1]"));
        assertTrue(LocalHosts.isLocalHost("0.0.0.0"));
    }

    @Test
    public void testCaseAndWhitespaceAreNormalized() {
        // Given / When / Then
        assertTrue(LocalHosts.isLocalHost("  LOCALHOST  "));
    }

    @Test
    public void testMissingHostDefaultsToLocal() {
        // Given / When / Then: a blank host binds locally.
        assertTrue(LocalHosts.isLocalHost(null));
        assertTrue(LocalHosts.isLocalHost(""));
        assertTrue(LocalHosts.isLocalHost("   "));
    }

    @Test
    public void testRemoteHostsAreNotLocal() {
        // Given / When / Then
        assertFalse(LocalHosts.isLocalHost("192.0.2.10"));
        assertFalse(LocalHosts.isLocalHost("serve.internal.example.com"));
        assertFalse(LocalHosts.isLocalHost("10.0.0.5"));
    }

    @Test
    public void testConnectHostRedirectsWildcardToLoopback() {
        // Given: 0.0.0.0 is a listen-everywhere instruction, not a destination.
        // When / Then
        assertEquals("127.0.0.1", LocalHosts.connectHost("0.0.0.0"));
        assertEquals("127.0.0.1", LocalHosts.connectHost("  0.0.0.0 "));
        assertEquals("127.0.0.1", LocalHosts.connectHost("::"));
        assertEquals("127.0.0.1", LocalHosts.connectHost("[::]"));
    }

    @Test
    public void testConnectHostDefaultsToLoopback() {
        // Given / When / Then
        assertEquals("127.0.0.1", LocalHosts.connectHost(null));
        assertEquals("127.0.0.1", LocalHosts.connectHost(""));
        assertEquals("127.0.0.1", LocalHosts.connectHost("   "));
    }

    @Test
    public void testConnectHostKeepsExplicitAddresses() {
        // Given / When / Then: a configured host is always honoured.
        assertEquals("localhost", LocalHosts.connectHost("localhost"));
        assertEquals("127.0.0.1", LocalHosts.connectHost("127.0.0.1"));
        assertEquals("::1", LocalHosts.connectHost("::1"));
        assertEquals("192.0.2.10", LocalHosts.connectHost(" 192.0.2.10 "));
        assertEquals("serve.internal", LocalHosts.connectHost("serve.internal"));
    }
}
