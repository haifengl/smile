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
package smile.serve.model;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import jakarta.ws.rs.ClientErrorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import smile.data.formula.Formula;
import smile.io.Paths;
import smile.io.Read;
import smile.model.Model;
import smile.serve.InferenceModel;
import smile.serve.InferenceService;
import smile.serve.InferenceServiceConfig;
import smile.serve.OnnxModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VersionComparator}, {@link ModelRef}, {@link ModelRegistry},
 * {@link ModelManifest}, {@link ModelDiscovery}, and startup/reload identity rules.
 */
public class ModelVersioningUnitTest {

    @TempDir
    Path tempDir;

    // --------------------------------------------------------------- VersionComparator

    @Test
    public void testVersionComparatorOrdersNumericAndSemVerCorrectly() {
        // Given
        List<String> versions = new ArrayList<>(List.of(
                "10", "2", "1", "1.10.0", "1.2.0", "1.2.0-rc1", "1.2.0-alpha"));

        // When
        versions.sort(VersionComparator.INSTANCE);

        // Then
        assertEquals(List.of("1", "1.2.0-alpha", "1.2.0-rc1", "1.2.0", "1.10.0", "2", "10"), versions);
    }

    // --------------------------------------------------------------- ModelRef

    @Test
    public void testModelRefValidatesSlugsVersionsAndAliases() {
        // Valid
        ModelRef ref = new ModelRef("fraud_cls-v1.0", "2.10.0-rc1+build4");
        assertEquals("fraud_cls-v1.0", ref.slug());
        assertEquals("2.10.0-rc1+build4", ref.version());
        assertEquals("fraud_cls-v1.0:2.10.0-rc1+build4", ref.canonicalId());
        assertEquals("canary-1", ModelRef.validateAlias("canary-1"));

        // Invalid slugs
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug(null));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug("   "));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug("org/model"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug("bad..slug"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug("has space"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateSlug("-leading-dash"));

        // Invalid versions & aliases
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateVersion("1/2"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateVersion("1..2"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateAlias("1numeric"));
        assertThrows(IllegalArgumentException.class, () -> ModelRef.validateAlias("a/b"));
    }

    // --------------------------------------------------------------- ModelRegistry

    @Test
    public void testRegistryMultiVersionDefaultsAliasesAndUnload() {
        // Given
        ModelRegistry<String> registry = new ModelRegistry<>();
        ModelRef v1 = new ModelRef("fraud", "1");
        ModelRef v2 = new ModelRef("fraud", "2");
        ModelRef v10 = new ModelRef("fraud", "10");

        // When: register v1, v2, v10
        registry.register(new ModelRegistry.Entry<>(v1, "m1", ModelSource.of(tempDir.resolve("f1.sml"))));
        registry.register(new ModelRegistry.Entry<>(v2, "m2", ModelSource.of(tempDir.resolve("f2.sml"))));
        registry.register(new ModelRegistry.Entry<>(v10, "m10", ModelSource.of(tempDir.resolve("f10.sml"))));

        // Then: latest and unpinned default resolve to numeric max "10"
        assertEquals("m10", registry.resolve("fraud").orElseThrow().model());
        assertEquals("m10", registry.resolveAlias("fraud", "latest").orElseThrow().model());
        assertEquals(List.of("1", "2", "10"), registry.versionsInfo("fraud").orElseThrow().versions());

        // When: pin default to v2 and add canary -> v10
        registry.setAlias("fraud", "default", "2");
        registry.setAlias("fraud", "canary", "10");

        // Then
        assertEquals("m2", registry.resolve("fraud").orElseThrow().model());
        assertEquals("m2", registry.resolveAlias("fraud", "default").orElseThrow().model());
        assertEquals("m10", registry.resolveAlias("fraud", "canary").orElseThrow().model());
        assertEquals("m10", registry.resolveAlias("fraud", "latest").orElseThrow().model());

        // Reserved alias 'latest' cannot be assigned or deleted
        assertThrows(IllegalArgumentException.class, () -> registry.setAlias("fraud", "latest", "1"));
        assertThrows(IllegalArgumentException.class, () -> registry.deleteAlias("fraud", "latest"));

        // When: remove v2 (pinned default) -> default falls back to latest (v10)
        registry.removeVersion(v2);
        assertEquals("m10", registry.resolve("fraud").orElseThrow().model());
        assertEquals(List.of("1", "10"), registry.versionsInfo("fraud").orElseThrow().versions());

        // When: remove v10 -> canary alias cleaned up and default falls back to v1
        registry.removeVersion(v10);
        assertTrue(registry.resolveAlias("fraud", "canary").isEmpty());
        assertEquals("m1", registry.resolve("fraud").orElseThrow().model());

        // When: remove entire slug -> reload target remembers last active default
        registry.removeSlug("fraud");
        assertFalse(registry.containsSlug("fraud"));
        assertEquals(Optional.of(v1), registry.reloadTargetForSlug("fraud"));
    }

    @Test
    public void testRegistryAtomicBatchAndConflictDetection() {
        // Given
        ModelRegistry<String> registry = new ModelRegistry<>();
        ModelRef v1 = new ModelRef("fraud", "1");
        registry.register(new ModelRegistry.Entry<>(v1, "m1", ModelSource.of(tempDir.resolve("f1.sml"))));

        // When: batch contains one new version and one duplicate -> whole batch fails
        var batch = List.of(
                new ModelRegistry.Entry<>(new ModelRef("fraud", "2"), "m2", ModelSource.of(tempDir.resolve("f2.sml"))),
                new ModelRegistry.Entry<>(v1, "dup", ModelSource.of(tempDir.resolve("dup.sml"))));
        assertThrows(ModelRegistry.ConflictException.class,
                () -> registry.registerBatch(batch, Map.of(), Map.of(), Set.of()));

        // Then: v2 was not committed
        assertFalse(registry.contains(new ModelRef("fraud", "2")));

        // When: pinExistingDefaultSlugs preserves v1 as default when adding v2 with set_default=false
        var batch2 = List.of(
                new ModelRegistry.Entry<>(new ModelRef("fraud", "2"), "m2", ModelSource.of(tempDir.resolve("f2.sml"))));
        registry.registerBatch(batch2, Map.of(), Map.of("fraud", Map.of("canary", "2")), Set.of("fraud"));

        // Then: default stays pinned on v1 while latest & canary point to v2
        assertEquals("1", registry.resolve("fraud").orElseThrow().ref().version());
        assertEquals("2", registry.resolveAlias("fraud", "canary").orElseThrow().ref().version());
        assertEquals("2", registry.resolveAlias("fraud", "latest").orElseThrow().ref().version());
    }

    // --------------------------------------------------------------- ModelManifest

    @Test
    public void testManifestLoadsValidMultiVersionAndSinglePathEntries() throws Exception {
        // Given
        Path manifestPath = tempDir.resolve("models.json");
        Files.writeString(manifestPath, """
                {
                  "auto_discover": false,
                  "models": [
                    {
                      "slug": "fraud",
                      "kind": "sml",
                      "versions": {
                        "1": "fraud_v1.sml",
                        "2": "fraud_v2.sml"
                      },
                      "default": "1",
                      "aliases": {
                        "canary": "2",
                        "stable": "1"
                      }
                    },
                    {
                      "slug": "vision",
                      "path": "sub/squeezenet.onnx",
                      "version": "1.2"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        // When
        ModelManifest manifest = ModelManifest.load(manifestPath);

        // Then
        assertFalse(manifest.autoDiscover());
        assertEquals(2, manifest.entries().size());
        assertEquals(1, manifest.entriesFor(ModelManifest.Kind.SML).size());
        assertEquals(1, manifest.entriesFor(ModelManifest.Kind.ONNX).size());

        var fraud = manifest.entriesFor(ModelManifest.Kind.SML).getFirst();
        assertEquals("fraud", fraud.slug());
        assertEquals("1", fraud.defaultVersion());
        assertEquals(Map.of("canary", "2", "stable", "1"), fraud.aliases());
        assertEquals(tempDir.resolve("fraud_v1.sml").toAbsolutePath().normalize(), fraud.versions().get("1"));

        var vision = manifest.entriesFor(ModelManifest.Kind.ONNX).getFirst();
        assertEquals("vision", vision.slug());
        assertEquals(ModelManifest.Kind.ONNX, vision.kind());
        assertEquals(tempDir.resolve("sub/squeezenet.onnx").toAbsolutePath().normalize(), vision.versions().get("1.2"));
    }

    @Test
    public void testManifestRejectsInvalidDeclarations() throws Exception {
        // Reserved alias 'latest'
        Path badAlias = tempDir.resolve("bad-alias.json");
        Files.writeString(badAlias, """
                {"models":[{"slug":"m","path":"a.sml","aliases":{"latest":"1"}}]}
                """, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ModelManifest.load(badAlias));

        // Default version not in versions
        Path badDefault = tempDir.resolve("bad-default.json");
        Files.writeString(badDefault, """
                {"models":[{"slug":"m","versions":{"1":"a.sml"},"default":"2"}]}
                """, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ModelManifest.load(badDefault));

        // Duplicate slug
        Path dupSlug = tempDir.resolve("dup-slug.json");
        Files.writeString(dupSlug, """
                {"models":[{"slug":"m","path":"a.sml"},{"slug":"m","path":"b.sml"}]}
                """, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ModelManifest.load(dupSlug));

        // Mismatched kind vs extension
        Path badKind = tempDir.resolve("bad-kind.json");
        Files.writeString(badKind, """
                {"models":[{"slug":"m","kind":"onnx","path":"a.sml"}]}
                """, StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> ModelManifest.load(badKind));
    }

    // --------------------------------------------------------------- Dual Discovery & Startup

    @Test
    public void testStartupDualDiscoveryAndManifestAndHfExclusion() throws Exception {
        // Given: a model dir with:
        //   - flat file flat_model.sml
        //   - versioned tree fraud/1/model.sml and fraud/2/model.sml
        //   - models.json declaring custom slug + alias for custom.sml
        //   - huggingface/org/1/ignored.sml (must be ignored!)
        //   - llm_dir/1/ignored.sml with genai_config.json (must be ignored!)
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var baseModel = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());

        Model.write(baseModel, tempDir.resolve("flat_model.sml"));
        Model.write(baseModel, tempDir.resolve("custom_file.sml"));

        Path fraudV1 = tempDir.resolve("fraud/1");
        Path fraudV2 = tempDir.resolve("fraud/2");
        Files.createDirectories(fraudV1);
        Files.createDirectories(fraudV2);
        Model.write(baseModel, fraudV1.resolve("model.sml"));
        Model.write(baseModel, fraudV2.resolve("model.sml"));

        Path hfDir = tempDir.resolve("huggingface/1");
        Files.createDirectories(hfDir);
        Model.write(baseModel, hfDir.resolve("should_ignore.sml"));

        Path genaiDir = tempDir.resolve("phi4");
        Path genaiSub = genaiDir.resolve("1");
        Files.createDirectories(genaiSub);
        Files.writeString(genaiDir.resolve("genai_config.json"), "{}");
        Model.write(baseModel, genaiSub.resolve("should_ignore.sml"));

        Files.writeString(tempDir.resolve("models.json"), """
                {
                  "auto_discover": true,
                  "models": [
                    {
                      "slug": "custom-slug",
                      "kind": "sml",
                      "path": "custom_file.sml",
                      "version": "3.0",
                      "aliases": { "prod": "3.0" }
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        // When
        InferenceService service = new InferenceService(configFor(tempDir, null));

        // Then
        assertTrue(service.listQuarantinedModels().isEmpty());
        assertEquals(Set.of("custom-slug", "flat_model", "fraud"), service.models().keySet());
        assertEquals("3.0", service.getModel("custom-slug").version());
        assertEquals("3.0", service.getModelAlias("custom-slug", "prod").version());
        assertEquals("1", service.getModel("flat_model").version());
        assertEquals("2", service.getModel("fraud").version());
        assertEquals(List.of("1", "2"), service.getVersions("fraud").versions());
    }

    @Test
    public void testStartupQuarantinesDuplicateModelVersionsInsteadOfSilentOverwrite() throws Exception {
        // Given: two .sml files with identical Model.ID and Model.VERSION
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var model = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        model.setTag(Model.ID, "dup-slug");
        model.setTag(Model.VERSION, "1");
        Model.write(model, tempDir.resolve("a.sml"));
        Model.write(model, tempDir.resolve("b.sml"));

        // When
        InferenceService service = new InferenceService(configFor(tempDir, null));

        // Then: first loads, second is quarantined as a conflict
        assertEquals(1, service.models().size());
        assertTrue(service.hasSlug("dup-slug"));
        assertEquals(1, service.listQuarantinedModels().size());
        assertTrue(service.listQuarantinedModels().getFirst().path().endsWith("b.sml"));
    }

    @Test
    public void testReloadPreservesServedModelIdOverride() throws Exception {
        // Given: single .sml file started with served-model-id="fraud-prod"
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var model = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        model.setTag(Model.ID, "internal-name");
        model.setTag(Model.VERSION, "1");
        Path smlFile = tempDir.resolve("artifact.sml");
        Model.write(model, smlFile);

        InferenceService service = new InferenceService(configFor(smlFile, "fraud-prod"));
        assertTrue(service.hasSlug("fraud-prod"));
        assertFalse(service.hasSlug("internal-name"));

        // When: reloadModel("fraud-prod") runs
        InferenceModel reloaded = service.reloadModel("fraud-prod");

        // Then: explicit slug override is preserved across reload
        assertEquals("fraud-prod", reloaded.slug());
        assertEquals("1", reloaded.version());
        assertTrue(service.hasSlug("fraud-prod"));
        assertFalse(service.hasSlug("internal-name"));
    }

    @Test
    public void testReloadRejectsCollisionWhenVersionTagChangesToOccupiedVersion() throws Exception {
        // Given: two files for slug "clash" at versions 1 and 2
        var iris = Read.arff(Paths.getTestData("weka/iris.arff"));
        var v1 = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        v1.setTag(Model.ID, "clash");
        v1.setTag(Model.VERSION, "1");
        Path file1 = tempDir.resolve("clash_v1.sml");
        Model.write(v1, file1);

        var v2 = Model.classification("logistic", Formula.lhs("class"), iris, null, new Properties());
        v2.setTag(Model.ID, "clash");
        v2.setTag(Model.VERSION, "2");
        Path file2 = tempDir.resolve("clash_v2.sml");
        Model.write(v2, file2);

        InferenceService service = new InferenceService(configFor(tempDir, null));
        assertEquals(List.of("1", "2"), service.getVersions("clash").versions());

        // When: file1's version tag is edited to "2" (already occupied by file2) and reloaded
        Model.write(v2, file1);

        // Then: reload rejects with 409 Conflict and both original versions remain intact
        ClientErrorException ex = assertThrows(ClientErrorException.class,
                () -> service.reloadModelVersion("clash", "1"));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals(List.of("1", "2"), service.getVersions("clash").versions());
    }

    @Test
    public void testOnnxRefResolutionPrecedence() {
        Path file = tempDir.resolve("squeezenet.onnx");

        // 1. Filename fallback + default version 1 when onnx version is 0
        var emptyMeta = new smile.onnx.ModelMetadata("pt", "torch_jit", "", "", "", 0L, Map.of());
        assertEquals(new ModelRef("squeezenet", "1"), OnnxModel.resolveRef(emptyMeta, ModelSource.of(file)));

        // 2. ONNX protobuf version > 0 used when customMetadata has no version
        var protoVerMeta = new smile.onnx.ModelMetadata("pt", "torch_jit", "", "", "", 7L, Map.of());
        assertEquals(new ModelRef("squeezenet", "7"), OnnxModel.resolveRef(protoVerMeta, ModelSource.of(file)));

        // 3. Custom metadata id/name & version take precedence over filename and protobuf version
        var customMeta = new smile.onnx.ModelMetadata("pt", "torch_jit", "", "", "", 7L,
                Map.of("name", "vision-net", "version", "2.3.0"));
        assertEquals(new ModelRef("vision-net", "2.3.0"), OnnxModel.resolveRef(customMeta, ModelSource.of(file)));

        // 4. Explicit source override wins over everything
        assertEquals(new ModelRef("prod-vision", "9.0"),
                OnnxModel.resolveRef(customMeta, new ModelSource(file, "prod-vision", "9.0")));
    }

    private static InferenceServiceConfig configFor(Path modelPath, String servedModelId) {
        return new InferenceServiceConfig() {
            @Override
            public String model() {
                return modelPath.toString();
            }

            @Override
            public Optional<String> servedModelId() {
                return Optional.ofNullable(servedModelId);
            }

            @Override
            public boolean allowEmpty() {
                return false;
            }

            @Override
            public boolean failFast() {
                return true;
            }

            @Override
            public boolean allowQuarantined() {
                return false;
            }
        };
    }
}
