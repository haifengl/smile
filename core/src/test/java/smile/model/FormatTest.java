/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model;

import java.io.EOFException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Format} ({@code SMLM} v1 envelope), {@link Model#write},
 * {@link Model#read}, legacy raw {@code ObjectOutputStream} compatibility,
 * and golden-artifact compatibility across all five {@link Model} record types.
 */
public class FormatTest {

    @TempDir
    Path tempDir;

    private static DataFrame sampleClassificationData() {
        return DataFrame.of(new double[][] {
                {0.0, 1.0}, {1.0, 1.2}, {2.0, 0.8}, {3.0, 1.1}, {4.0, 0.9},
                {5.0, 5.1}, {6.0, 4.9}, {7.0, 5.2}, {8.0, 5.0}, {9.0, 4.8}
        }, "x1", "x2").add(new IntVector("y", new int[] {0, 0, 0, 0, 0, 1, 1, 1, 1, 1}));
    }

    private static DataFrame sampleRegressionData() {
        return DataFrame.of(new double[][] {
                {0.0}, {1.0}, {2.0}, {3.0}, {4.0},
                {5.0}, {6.0}, {7.0}, {8.0}, {9.0}
        }, "x").add(new DoubleVector("y",
                new double[] {1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0}));
    }

    @Test
    public void testGivenClassificationModelWhenWrittenAndReadThenHeaderIsSmlmV1AndRoundTrips() throws Exception {
        // Given
        MathEx.setSeed(19650218);
        DataFrame df = sampleClassificationData();
        Properties params = new Properties();
        ClassificationModel model = Model.classification("logistic", Formula.lhs("y"), df, null, params);
        model.setTag(Model.ID, "test-cls");
        model.setTag(Model.VERSION, "1");
        Path file = tempDir.resolve("cls.sml");

        // When
        Model.write(model, file);
        Format.Header header = Format.inspect(file);
        Model loaded = Model.read(file);

        // Then
        assertFalse(header.legacy());
        assertEquals(Format.CURRENT_VERSION, header.version());
        assertEquals(0, header.flags());

        byte[] raw = Files.readAllBytes(file);
        assertTrue(raw.length > Format.HEADER_SIZE);
        assertEquals((byte) 'S', raw[0]);
        assertEquals((byte) 'M', raw[1]);
        assertEquals((byte) 'L', raw[2]);
        assertEquals((byte) 'M', raw[3]);

        assertInstanceOf(ClassificationModel.class, loaded);
        ClassificationModel restored = (ClassificationModel) loaded;
        assertEquals("logistic", restored.algorithm());
        assertEquals("test-cls", restored.getTag(Model.ID));
        assertEquals("1", restored.getTag(Model.VERSION));
        Tuple probe = df.get(0);
        assertEquals(model.predict(probe), restored.predict(probe));

        // And Read.object(Path) transparently strips the SMLM header for legacy callers
        Object viaReadObject = Read.object(file);
        assertInstanceOf(ClassificationModel.class, viaReadObject);
    }

    @Test
    public void testGivenFlippedHeaderByteWhenReadThenRejectsWithCrcMismatch() throws Exception {
        // Given
        DataFrame df = sampleRegressionData();
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), df, null, new Properties());
        Path file = tempDir.resolve("corrupt-crc.sml");
        Model.write(model, file);

        // When: flip the last byte of the 4-byte CRC in the 11-byte header
        byte[] bytes = Files.readAllBytes(file);
        bytes[10] ^= 0xFF;
        Files.write(file, bytes);

        // Then
        StreamCorruptedException ex = assertThrows(StreamCorruptedException.class, () -> Model.read(file));
        assertTrue(ex.getMessage().contains("CRC-32 mismatch"),
                "Expected CRC-32 mismatch message, got: " + ex.getMessage());
    }

    @Test
    public void testGivenFutureFormatVersionWhenReadThenRejectsWithVersionSpecificMessage() throws Exception {
        // Given
        DataFrame df = sampleRegressionData();
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), df, null, new Properties());
        Path validFile = tempDir.resolve("valid.sml");
        Model.write(model, validFile);
        byte[] validBytes = Files.readAllBytes(validFile);

        // Construct a v3 header with a valid CRC-32 over the 7 prefix bytes
        byte[] v3Header = Format.buildHeader((short) 3, (byte) 0);
        System.arraycopy(v3Header, 0, validBytes, 0, Format.HEADER_SIZE);
        Path v3File = tempDir.resolve("future-v3.sml");
        Files.write(v3File, validBytes);

        // When / Then
        StreamCorruptedException ex = assertThrows(StreamCorruptedException.class, () -> Model.read(v3File));
        assertTrue(ex.getMessage().contains("v3 .sml") && ex.getMessage().contains("reads v1–v1"),
                "Expected actionable version message, got: " + ex.getMessage());
    }

    @Test
    public void testGivenNonZeroFlagsInV1WhenReadThenRejectsWithFlagsMessage() throws Exception {
        // Given
        DataFrame df = sampleRegressionData();
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), df, null, new Properties());
        Path validFile = tempDir.resolve("valid-flags.sml");
        Model.write(model, validFile);
        byte[] bytes = Files.readAllBytes(validFile);

        byte[] badFlagsHeader = Format.buildHeader((short) 1, Format.FLAG_COMPRESSION);
        System.arraycopy(badFlagsHeader, 0, bytes, 0, Format.HEADER_SIZE);
        Path badFlagsFile = tempDir.resolve("bad-flags.sml");
        Files.write(badFlagsFile, bytes);

        // When / Then
        StreamCorruptedException ex = assertThrows(StreamCorruptedException.class, () -> Model.read(badFlagsFile));
        assertTrue(ex.getMessage().contains("flags 0x01"),
                "Expected flags error message, got: " + ex.getMessage());
    }

    @Test
    public void testGivenTruncatedHeaderWhenReadThenThrowsEofException() throws Exception {
        // Given: a file containing only 6 bytes starting with "SMLM"
        Path truncated = tempDir.resolve("truncated.sml");
        Files.write(truncated, new byte[] {'S', 'M', 'L', 'M', 0x00, 0x01});

        // When / Then
        assertThrows(EOFException.class, () -> Model.read(truncated));
    }

    @Test
    public void testGivenNonModelPayloadWhenReadThenThrowsStreamCorruptedException() throws Exception {
        // Given: a raw serialized String (not a Model)
        Path notAModel = tempDir.resolve("not-a-model.sml");
        Write.object("just-a-string", notAModel);

        // When / Then
        StreamCorruptedException ex = assertThrows(StreamCorruptedException.class, () -> Model.read(notAModel));
        assertTrue(ex.getMessage().contains("does not contain a valid SMILE Model"));
    }

    @Test
    public void testGivenLegacyRawObjectOutputStreamArtifactWhenReadThenLoadsAndLogsOnce() throws Exception {
        // Given: a raw ObjectOutputStream model (no SMLM magic)
        Format.resetLegacyWarningForTests();
        DataFrame df = sampleRegressionData();
        RegressionModel model = Model.regression("ols", Formula.lhs("y"), df, null, new Properties());
        Path legacyFile = tempDir.resolve("legacy.sml");
        Write.object(model, legacyFile);

        // Verify the file begins with 0xACED0005
        byte[] prefix = Files.readAllBytes(legacyFile);
        assertEquals(0xACED0005, ByteBuffer.wrap(prefix, 0, 4).getInt());
        assertFalse(Format.isLegacyWarningLogged());

        // When: reading the legacy artifact twice
        Format.Header inspected = Format.inspect(legacyFile);
        Model loaded1 = Model.read(legacyFile);
        assertTrue(Format.isLegacyWarningLogged(), "Deprecation warning flag must be set on first legacy load");
        Model loaded2 = Model.read(legacyFile);

        // Then
        assertTrue(inspected.legacy());
        assertEquals(0, inspected.version());
        assertInstanceOf(RegressionModel.class, loaded1);
        assertInstanceOf(RegressionModel.class, loaded2);
        assertEquals(model.predict(df.get(5)), ((RegressionModel) loaded1).predict(df.get(5)), 1E-10);
    }

    @Test
    public void testGoldenModelArtifactsLoadAndRoundTripAcrossAllFiveModelTypes() throws Exception {
        // Given: golden v1 .sml fixtures for all 5 Model record types committed in test resources
        DataFrame clsData = sampleClassificationData();
        DataFrame regData = sampleRegressionData();

        String[] goldenResources = {
                "/smile/model/golden/classification-v1.sml",
                "/smile/model/golden/regression-v1.sml",
                "/smile/model/golden/clustering-v1.sml",
                "/smile/model/golden/quantizer-v1.sml",
                "/smile/model/golden/anomaly-v1.sml"
        };

        for (String res : goldenResources) {
            try (InputStream in = FormatTest.class.getResourceAsStream(res)) {
                assertNotNull(in, "Missing golden model fixture: " + res);
                Model loaded = Format.read(in);
                assertNotNull(loaded);
                assertNotNull(loaded.algorithm());
                assertNotNull(loaded.schema());
                assertNotNull(loaded.tags());

                // Round-trip write & read
                Path tmp = Model.write(loaded);
                try {
                    Model roundTripped = Model.read(tmp);
                    assertEquals(loaded.getClass(), roundTripped.getClass());
                    assertEquals(loaded.algorithm(), roundTripped.algorithm());
                    assertEquals(loaded.getTag(Model.ID), roundTripped.getTag(Model.ID));

                    switch (roundTripped) {
                        case ClassificationModel cm ->
                                assertEquals(((ClassificationModel) loaded).predict(clsData.get(0)),
                                        cm.predict(clsData.get(0)));
                        case RegressionModel rm ->
                                assertEquals(((RegressionModel) loaded).predict(regData.get(3)),
                                        rm.predict(regData.get(3)), 1E-10);
                        case ClusteringModel clm ->
                                assertEquals(((ClusteringModel) loaded).predict(clsData.get(0)),
                                        clm.predict(clsData.get(0)));
                        case VectorQuantizerModel vqm ->
                                assertArrayEquals(((VectorQuantizerModel) loaded).quantize(clsData.get(0)),
                                        vqm.quantize(clsData.get(0)), 1E-10);
                        case AnomalyModel am ->
                                assertEquals(((AnomalyModel) loaded).score(clsData.get(0)),
                                        am.score(clsData.get(0)), 1E-10);
                        default -> fail("Unexpected model type: " + roundTripped.getClass());
                    }
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        }
    }
}
