/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import javax.swing.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import smile.studio.view.OutputArea;

/**
 * Redirect console output stream to an OutputArea.
 *
 * @author Haifeng Li
 */
public class ConsoleOutputStream extends OutputStream {
    /** Kernel running cell. */
    private OutputArea area;

    /**
     * Constructor.
     */
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
        SwingUtilities.invokeLater(() -> {
            if (area != null) {
                area.flush();
            }
        });
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
    }

    /**
     * Removes the output area.
     */
    public void removeOutputArea() {
        area = null;
    }
}
