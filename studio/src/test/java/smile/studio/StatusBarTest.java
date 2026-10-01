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

import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StatusBar}.
 */
class StatusBarTest {

    @Test
    void initialStatusIsReady() {
        StatusBar statusBar = new StatusBar();
        ResourceBundle bundle = ResourceBundle.getBundle(StatusBar.class.getName(), Locale.getDefault());
        assertEquals(bundle.getString("Ready"), statusBar.getStatus());
    }

    @Test
    void setStatusUpdatesStatusMessage() {
        StatusBar statusBar = new StatusBar();
        statusBar.setStatus("Test status message");
        assertEquals("Test status message", statusBar.getStatus());
    }

    @Test
    void setStatusClearsStatusMessage() {
        StatusBar statusBar = new StatusBar();
        statusBar.setStatus("Something");
        assertEquals("Something", statusBar.getStatus());
        statusBar.setStatus("");
        assertEquals("", statusBar.getStatus());
    }
}
