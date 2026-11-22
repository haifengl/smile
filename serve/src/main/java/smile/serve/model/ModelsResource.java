/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.common.annotation.RunOnVirtualThread;
import smile.chat.ChatService;
import smile.serve.InferenceModel;
import smile.serve.InferenceService;
import smile.serve.LocalhostOnly;
import smile.serve.OnnxModel;
import smile.serve.OnnxService;

/**
 * OpenAI-compatible models API at {@code /v1/models}.
 *
 * <p>Lists and retrieves every loaded model in one catalog: chat LLMs, ONNX
 * graphs, and SMILE {@code .sml} models. Classic SMILE inference remains under
 * {@code /v1/smile/{slug}}; ONNX inference under {@code /v1/onnx/{slug}}.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/retrieve">OpenAI Retrieve model</a>
 */
@Path("/v1/models")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class ModelsResource {

    @Inject
    ModelCatalog catalog;

    @Inject
    InferenceService inferenceService;

    @Inject
    OnnxService onnxService;

    /** Absent in builds that exclude the chat stack (e.g. the native-micro image). */
    @Inject
    Instance<ChatService> chatService;

    /**
     * Dynamically loads a model from disk or Hugging Face into active memory.
     * Accessible only from localhost.
     *
     * <p>For {@code sml} and {@code onnx} models, optional fields {@code slug}
     * (or {@code id}), {@code version}, {@code alias} / {@code aliases}, and
     * {@code set_default} may be specified when loading a single artifact.
     *
     * @param request JSON object specifying {@code model}, optional {@code kind}, and optional configs.
     * @return model loading confirmation.
     */
    @POST
    @Path("/load")
    @LocalhostOnly
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> load(JsonObject request) {
        if (request == null || !request.containsKey("model")
                || request.getString("model") == null || request.getString("model").isBlank()) {
            throw new BadRequestException("Request body must contain non-blank 'model'");
        }
        String modelSpec = request.getString("model").trim();
        String kind = request.getString("kind", "auto").trim().toLowerCase();

        return switch (kind) {
            case "sml", "smile" -> loadSml(modelSpec, parseLoadOptions(request));
            case "onnx" -> loadOnnx(modelSpec, parseLoadOptions(request));
            case "llm", "chat" -> loadChat(modelSpec, request);
            case "auto" -> autoLoad(modelSpec, request);
            default -> throw new BadRequestException("Unsupported kind '" + kind + "'. Supported: auto, sml/smile, onnx, llm/chat");
        };
    }

    private Map<String, Object> autoLoad(String modelSpec, JsonObject request) {
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        if (modelSpec.endsWith(".sml") || (Files.isDirectory(p) && hasSmlFiles(p) && !hasGenAiOrConfig(p))) {
            return loadSml(modelSpec, parseLoadOptions(request));
        } else if (modelSpec.endsWith(".onnx") || (Files.isDirectory(p) && hasOnnxFiles(p) && !hasGenAiOrConfig(p))) {
            return loadOnnx(modelSpec, parseLoadOptions(request));
        } else if (!Files.exists(p) && modelSpec.contains("/")) {
            return loadChat(modelSpec, request);
        } else if (Files.isDirectory(p) && hasGenAiOrConfig(p)) {
            return loadChat(modelSpec, request);
        } else {
            if (Files.exists(p) && Files.isDirectory(p)) {
                if (hasSmlFiles(p)) return loadSml(modelSpec, parseLoadOptions(request));
                if (hasOnnxFiles(p)) return loadOnnx(modelSpec, parseLoadOptions(request));
            }
            if (ModelSpecs.looksLikeHuggingFaceRepoId(modelSpec)) {
                return loadChat(modelSpec, request);
            }
            throw new BadRequestException("Unable to determine model kind for '" + modelSpec + "'. Specify 'kind' explicitly (sml, onnx, or llm).");
        }
    }

    static ModelLoadOptions parseLoadOptions(JsonObject request) {
        if (request == null) {
            return ModelLoadOptions.EMPTY;
        }
        String rawSlug = request.getString("slug");
        String rawId = request.getString("id");
        String slug = null;
        if (rawSlug != null && !rawSlug.isBlank()) {
            slug = rawSlug.strip();
            if (rawId != null && !rawId.isBlank() && !rawId.strip().equals(slug)) {
                throw new BadRequestException("Conflicting 'slug' (" + slug + ") and 'id' (" + rawId.strip() + ")");
            }
        } else if (rawId != null && !rawId.isBlank()) {
            slug = rawId.strip();
        }

        String rawVersion = request.getString("version");
        String version = (rawVersion != null && !rawVersion.isBlank()) ? rawVersion.strip() : null;

        List<String> aliases = new ArrayList<>();
        Object singleAlias = request.getValue("alias");
        if (singleAlias instanceof String s && !s.isBlank()) {
            aliases.add(s.strip());
        } else if (singleAlias != null && !(singleAlias instanceof String)) {
            throw new BadRequestException("'alias' must be a string");
        }
        Object multiAliases = request.getValue("aliases");
        if (multiAliases instanceof JsonArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                Object elem = arr.getValue(i);
                if (!(elem instanceof String s) || s.isBlank()) {
                    throw new BadRequestException("'aliases[" + i + "]' must be a non-blank string");
                }
                aliases.add(s.strip());
            }
        } else if (multiAliases != null) {
            throw new BadRequestException("'aliases' must be a JSON array of strings");
        }

        Boolean setDefault = null;
        Object rawSetDefault = request.containsKey("set_default")
                ? request.getValue("set_default")
                : request.getValue("setDefault");
        if (rawSetDefault instanceof Boolean b) {
            setDefault = b;
        } else if (rawSetDefault != null) {
            throw new BadRequestException("'set_default' must be a boolean");
        }

        try {
            return new ModelLoadOptions(slug, version, aliases, setDefault);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> loadSml(String modelSpec, ModelLoadOptions options) {
        if (options.slug() != null && onnxService.hasSlug(options.slug())) {
            throw new ClientErrorException(
                    "Model slug '" + options.slug() + "' is already registered by the ONNX backend",
                    Response.Status.CONFLICT);
        }
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        List<InferenceModel> loaded = inferenceService.load(p, options);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "loaded");
        result.put("kind", "sml");
        result.put("model", modelSpec);
        List<String> ids = loaded.stream().map(InferenceModel::slug).distinct().toList();
        result.put("ids", ids);
        if (loaded.size() == 1) {
            InferenceModel first = loaded.getFirst();
            result.put("id", first.slug());
            result.put("slug", first.slug());
            result.put("version", first.version());
            result.put("algorithm", first.metadata().algorithm());
        } else {
            result.put("models", loaded.stream()
                    .map(m -> Map.of("slug", m.slug(), "version", m.version()))
                    .toList());
        }
        return result;
    }

    private Map<String, Object> loadOnnx(String modelSpec, ModelLoadOptions options) {
        if (options.slug() != null && inferenceService.hasSlug(options.slug())) {
            throw new ClientErrorException(
                    "Model slug '" + options.slug() + "' is already registered by the SMILE backend",
                    Response.Status.CONFLICT);
        }
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        List<OnnxModel> loaded = onnxService.load(p, options);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "loaded");
        result.put("kind", "onnx");
        result.put("model", modelSpec);
        List<String> ids = loaded.stream().map(OnnxModel::slug).distinct().toList();
        result.put("ids", ids);
        if (loaded.size() == 1) {
            OnnxModel first = loaded.getFirst();
            result.put("id", first.slug());
            result.put("slug", first.slug());
            result.put("version", first.version());
        } else {
            result.put("models", loaded.stream()
                    .map(m -> Map.of("slug", m.slug(), "version", m.version()))
                    .toList());
        }
        return result;
    }

    private Map<String, Object> loadChat(String modelSpec, JsonObject request) {
        if (!chatService.isResolvable()) {
            throw new WebApplicationException("Chat models are not supported in this build", Response.Status.SERVICE_UNAVAILABLE);
        }
        return chatService.get().load(modelSpec, request);
    }

    private static boolean hasSmlFiles(java.nio.file.Path dir) {
        return ModelDiscovery.hasModels(dir, ModelManifest.Kind.SML);
    }

    private static boolean hasOnnxFiles(java.nio.file.Path dir) {
        return ModelDiscovery.hasModels(dir, ModelManifest.Kind.ONNX);
    }

    private static boolean hasGenAiOrConfig(java.nio.file.Path dir) {
        return Files.isRegularFile(dir.resolve("genai_config.json"))
                || Files.isRegularFile(dir.resolve("config.json"))
                || Files.isRegularFile(dir.resolve("params.json"));
    }

    /**
     * Lists all currently available models (chat, ONNX, and SMILE).
     *
     * @return OpenAI-shaped {@code { object: "list", data: [...] }}.
     */
    @GET
    public ModelList list() {
        return ModelList.of(catalog.list(), catalog.listQuarantined());
    }

    /**
     * Retrieves a single model by id (OpenAI retrieve-model parity).
     *
     * <p>The path accepts ids that contain slashes (e.g. Hugging Face repo ids
     * like {@code Qwen/Qwen3.8-27B}, or versioned/aliased selectors like
     * {@code fraud/versions/2} and {@code fraud/aliases/canary}) via a greedy
     * path segment. Inference is <em>not</em> performed here — use the
     * type-specific endpoints for that.
     *
     * @param id the public model id or versioned/aliased selector.
     * @return the model object.
     * @throws NotFoundException if no loaded model matches.
     */
    @GET
    @Path("/{id:.+}")
    public ModelObject retrieve(@PathParam("id") String id) {
        return catalog.find(id, true)
                .orElseThrow(() -> new NotFoundException("Model not found: " + id));
    }
}
