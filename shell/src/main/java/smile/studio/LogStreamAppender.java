/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio;

import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import ch.qos.logback.core.OutputStreamAppender;

/**
 * Logback appender for LogArea.
 * @param <E> Event type.
 * @author Haifeng Li
 */
public class LogStreamAppender<E> extends OutputStreamAppender<E> {
    private static final DelegatingOutputStream DELEGATING_OUTPUT_STREAM = new DelegatingOutputStream(null);

    @Override
    public void start() {
        setOutputStream(DELEGATING_OUTPUT_STREAM);
        super.start();
    }

    public static void setStaticOutputStream(OutputStream out) {
        DELEGATING_OUTPUT_STREAM.setOutputStream(out);
    }

    /** Redirects system streams. */
    public void redirectSystemStreams(OutputStream out) {
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    private static class DelegatingOutputStream extends FilterOutputStream {
        /**
         * Creates a delegating NO-OP OutputStream.
         */
        public DelegatingOutputStream(OutputStream out) {
            super(new OutputStream() {
                @Override
                public void write(int b) {}
            });
        }

        void setOutputStream(OutputStream out) {
            this.out = out;
        }
    }
}
