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
package smile.studio.kernel;

import javax.swing.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import smile.studio.text.OutputArea;

/**
 * Redirect console output stream to an OutputArea.
 *
 * @author Haifeng Li
 */
public class ConsoleOutputStream extends OutputStream {
    /** Kernel running cell. */
    private OutputArea area;
    /** Timestamp of last time updating cell output. */
    private long stamp;

    /** Constructor. */
    public ConsoleOutputStream() {

    }

    @Override
    public void write(int b) {
        if (area != null) {
            StringBuffer buffer = area.buffer();
            buffer.append((char) b);
        }
    }

    @Override
    public void write(byte[] bytes, int off, int len) {
        if (area != null) {
            StringBuffer buffer = area.buffer();
            buffer.append(new String(bytes, off, len, StandardCharsets.UTF_8));
        }
    }

    @Override
    public void flush() {
        long time = System.currentTimeMillis();
        // Throttle the update to avoid too much overhead of updating cell output.
        // Update cell output at most every 100 milliseconds.
        if (time - stamp >= 100) {
            stamp = time;
            SwingUtilities.invokeLater(() -> {
                if (area != null) {
                    area.flush();
                }
            });
        }
    }

    /**
     * Returns the output area.
     * @return the output area for redirected stream.
     */
    public OutputArea getOutputArea() {
        return area;
    }

    /**
     * Sets the output area.
     * @param area the output area for redirected stream.
     */
    public void setOutputArea(OutputArea area) {
        this.area = area;
        stamp = System.currentTimeMillis();
    }

    /**
     * Removes the output area.
     */
    public void removeOutputArea() {
        area = null;
    }
}
