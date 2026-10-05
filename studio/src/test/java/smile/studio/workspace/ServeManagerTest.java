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
package smile.studio.workspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ServeManager} state handling.
 *
 * <p>Process spawning is not exercised here; these tests cover the singleton
 * contract and the not-running state.
 *
 * @author Haifeng Li
 */
public class ServeManagerTest {

    @Test
    public void testSingleton() {
        // Then
        assertSame(ServeManager.getInstance(), ServeManager.getInstance());
    }

    @Test
    public void testNotRunningInitially() {
        // Given: a fresh manager (stop clears any prior state).
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.isRunning());
        assertNull(manager.baseUrl());
        assertNull(manager.client());
    }

    @Test
    public void testLoadedModelIdsEmptyWhenNotRunning() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertEquals(0, manager.loadedModelIds().size());
    }

    @Test
    public void testUnloadReturnsFalseWhenNotRunning() throws Exception {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.unloadModel("iris-1"));
    }

    @Test
    public void testReloadReturnsFalseWhenNotRunning() throws Exception {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertFalse(manager.reloadModel("iris-1"));
    }

    @Test
    public void testLoadModelRejectsNullPath() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then: a null path is rejected before any transport attempt.
        assertThrows(IllegalArgumentException.class, () -> manager.loadModel(null));
    }

    @Test
    public void testLoadModelRejectsBlankPath() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then
        assertThrows(IllegalArgumentException.class, () -> manager.loadModel("   "));
    }

    @Test
    public void testLoadModelThrowsWhenNotRunning() {
        // Given
        var manager = ServeManager.getInstance();
        manager.stop();

        // Then: a valid path with no service running is a transport error.
        assertThrows(java.io.IOException.class, () -> manager.loadModel("iris.sml"));
    }
}
