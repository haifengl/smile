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
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
public class ConsoleOutputStreamTest {
    private ConsoleOutputStream stream;
    @BeforeEach
    public void setUp() { stream = new ConsoleOutputStream(); }
    @AfterEach
    public void tearDown() throws Exception { stream.close(); }
    @Test
    public void testWriteByteNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.write('A'));
    }
    @Test
    public void testWriteBytesNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.write("hello".getBytes(StandardCharsets.UTF_8), 0, 5));
    }
    @Test
    public void testFlushNoAreaIsNoOp() {
        assertDoesNotThrow(() -> stream.flush());
    }
    @Test
    public void testGetOutputAreaNullByDefault() {
        assertNull(stream.getOutputArea());
    }
    @Test
    public void testRemoveOutputAreaSetsNull() {
        stream.removeOutputArea();
        assertNull(stream.getOutputArea());
    }
    @Test
    public void testWriteBytesHandlesUTF8Multibyte() {
        byte[] bytes = "\uD83D\uDE00".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> stream.write(bytes, 0, bytes.length));
    }
    @Test
    public void testPrintStreamDoesNotThrow() {
        PrintStream ps = new PrintStream(stream, true, StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> { ps.println("line 1"); ps.flush(); });
    }
    @Test
    public void testWriteSingleByteRange() {
        assertDoesNotThrow(() -> {
            for (int c = 32; c < 127; c++) stream.write(c);
        });
    }
}