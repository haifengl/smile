/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

import java.io.InvalidClassException;
import java.io.StreamCorruptedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.data.formula.Formula;
import smile.io.Paths;
import smile.io.Read;
import smile.model.Model;
import smile.serve.model.QuarantinedModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ModelArtifactVerifier} pre-deserialization gating,
 * corrupt/truncated {@code .sml} quarantine tracking, and fatal vs. non-fatal
 * failure classification in {@link InferenceService}.
 */
public class ModelArtifactVerifierTest {

    @TempDir
    Path tempDir;

    private static InferenceServiceConfig configFor(Path modelPath) {
        return new InferenceServiceConfig() {
            @Override
            public String model() {
                return modelPath.toString();
            }

            @Override
            public boolean allowEmpty() {
                return false;
            }
        };
    }

    @Test
    public void testGivenTruncatedSmlFileAtStartupWhenLoadedThenQuarantinedAndVisible() throws Exception {
        // Given: a truncated/corrupt .sml file in the startup model directory
        Path badFile = tempDir.resolve("corrupt.sml");
        Files.writeString(badFile, "SMLM\u0000", StandardCharsets.US_ASCII);

        // When: InferenceService scans the directory at startup
        InferenceService service = new InferenceService(configFor(tempDir), List.of(), List.of(), List.of());

        // Then: the model is not in active models, and the failure is recorded in quarantined
        assertTrue(service.models().isEmpty());
        List<QuarantinedModel> quarantined = service.listQuarantinedModels();
        assertEquals(1, quarantined.size());
        QuarantinedModel q = quarantined.getFirst();
        assertEquals(ModelMetricsBinder.SMILE_NAMESPACE, q.namespace());
        assertEquals(badFile.toAbsolutePath().normalize().toString(), q.path());
        assertTrue(q.fatal(), "Truncated SMLM header must be classified as fatal");
    }

    @Test
    public void testGivenRejectingVerifierWhenLoadingModelThenBlocksBeforeDeserialization() throws Exception {
        // Given: a valid .sml file on disk and a verifier that rejects it
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var model = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        model.setTag(Model.ID, "iris-signed");
        model.setTag(Model.VERSION, "1");
        Path smlFile = tempDir.resolve("iris-signed.sml");
        Model.write(model, smlFile);

        AtomicInteger preVerifyCalls = new AtomicInteger();
        AtomicInteger postVerifyCalls = new AtomicInteger();
        ModelArtifactVerifier rejectingVerifier = new ModelArtifactVerifier() {
            @Override
            public void verify(String namespace, Path artifactPath) throws SecurityException {
                preVerifyCalls.incrementAndGet();
                throw new SecurityException("Missing detached .sig for " + artifactPath.getFileName());
            }

            @Override
            public void verifyLoadedSmileModel(Path artifactPath, Model m) throws SecurityException {
                postVerifyCalls.incrementAndGet();
            }
        };

        // When: startup scan runs with the rejecting verifier
        InferenceService service = new InferenceService(
                configFor(smlFile), List.of(), List.of(), List.of(rejectingVerifier));

        // Then: pre-load verify ran, post-load verify never ran, and the model is quarantined as fatal
        assertEquals(1, preVerifyCalls.get());
        assertEquals(0, postVerifyCalls.get());
        assertTrue(service.models().isEmpty());
        assertEquals(1, service.listQuarantinedModels().size());
        assertTrue(service.listQuarantinedModels().getFirst().fatal());

        // And dynamic load throws 403 ForbiddenException when verifier rejects
        assertThrows(ForbiddenException.class, () -> service.load(smlFile));
    }

    @Test
    public void testGivenPassingVerifierWhenDynamicLoadRunsThenBothHooksExecuteAndQuarantineClears() throws Exception {
        // Given: a valid .sml file and a verifier that records both pre- and post-load hooks
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var model = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        model.setTag(Model.ID, "iris-verified");
        model.setTag(Model.VERSION, "2");
        Path smlFile = tempDir.resolve("iris-verified.sml");
        Model.write(model, smlFile);

        AtomicInteger preVerifyCalls = new AtomicInteger();
        AtomicInteger postVerifyCalls = new AtomicInteger();
        ModelArtifactVerifier acceptingVerifier = new ModelArtifactVerifier() {
            @Override
            public void verify(String namespace, Path artifactPath) {
                assertEquals(ModelMetricsBinder.SMILE_NAMESPACE, namespace);
                preVerifyCalls.incrementAndGet();
            }

            @Override
            public void verifyLoadedSmileModel(Path artifactPath, Model loaded) {
                assertEquals("iris-verified", loaded.getTag(Model.ID));
                assertEquals("2", loaded.getTag(Model.VERSION));
                postVerifyCalls.incrementAndGet();
            }
        };

        // When
        InferenceService service = new InferenceService(
                configFor(tempDir.resolve("empty")), List.of(), List.of(), List.of(acceptingVerifier));
        var loaded = service.load(smlFile);

        // Then
        assertEquals(1, loaded.size());
        assertEquals("iris-verified-2", loaded.getFirst().id());
        assertEquals(1, preVerifyCalls.get());
        assertEquals(1, postVerifyCalls.get());
        assertTrue(service.listQuarantinedModels().isEmpty());
    }

    @Test
    public void testFatalLoadFailureClassificationDistinguishesFilterRejectionsFromUidMismatch() {
        // Benign serialVersionUID mismatch -> non-fatal (can be quarantined when fail-fast=false)
        InvalidClassException uidMismatch = new InvalidClassException(
                "smile.model.ClassificationModel",
                "local class incompatible: stream classdesc serialVersionUID = 2, local class serialVersionUID = 3");
        assertFalse(InferenceService.isFatalLoadFailure(uidMismatch));

        // ObjectInputFilter security rejection -> always fatal
        InvalidClassException filterRejected = new InvalidClassException(
                "untrusted.GadgetPayload",
                "filter status: REJECTED");
        assertTrue(InferenceService.isFatalLoadFailure(filterRejected));

        // SecurityException and StreamCorruptedException -> always fatal
        assertTrue(InferenceService.isFatalLoadFailure(new SecurityException("Bad signature")));
        assertTrue(InferenceService.isFatalLoadFailure(new StreamCorruptedException("Unsupported .sml format")));
    }

    @Test
    public void testCorruptDynamicLoadThrowsBadRequest() throws Exception {
        // Given
        Path badFile = tempDir.resolve("bad-dynamic.sml");
        Files.writeString(badFile, "not-an-sml-file", StandardCharsets.US_ASCII);
        InferenceService service = new InferenceService(
                configFor(tempDir.resolve("empty")), List.of(), List.of(), List.of());

        // When / Then
        assertThrows(BadRequestException.class, () -> service.load(badFile));
    }
}
