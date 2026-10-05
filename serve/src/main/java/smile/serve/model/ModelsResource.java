/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve.model;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;
import smile.chat.ChatService;
import smile.serve.InferenceModel;
import smile.serve.InferenceService;
import smile.serve.LocalhostOnly;
import smile.serve.OnnxModel;
import smile.serve.OnnxService;

/**
 * OpenAI-compatible models API at {@code /api/v1/models}.
 *
 * <p>Lists and retrieves every loaded model in one catalog: chat LLMs, ONNX
 * graphs, and SMILE {@code .sml} models. Classic SMILE inference remains under
 * {@code /api/v1/smile/{id}}; ONNX inference under {@code /api/v1/onnx/{id}}.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/retrieve">OpenAI Retrieve model</a>
 */
@Path("/models")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class ModelsResource {

    @Inject
    ModelCatalog catalog;

    @Inject
    InferenceService inferenceService;

    @Inject
    OnnxService onnxService;

    @Inject
    ChatService chatService;

    /**
     * Dynamically loads a model from disk or Hugging Face into active memory.
     * Accessible only from localhost.
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
            case "sml", "smile" -> loadSml(modelSpec);
            case "onnx" -> loadOnnx(modelSpec);
            case "llm", "chat" -> loadChat(modelSpec, request);
            case "auto" -> autoLoad(modelSpec, request);
            default -> throw new BadRequestException("Unsupported kind '" + kind + "'. Supported: auto, sml/smile, onnx, llm/chat");
        };
    }

    private Map<String, Object> autoLoad(String modelSpec, JsonObject request) {
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        if (modelSpec.endsWith(".sml") || (Files.isDirectory(p) && hasSmlFiles(p) && !hasGenAiOrConfig(p))) {
            return loadSml(modelSpec);
        } else if (modelSpec.endsWith(".onnx") || (Files.isDirectory(p) && hasOnnxFiles(p) && !hasGenAiOrConfig(p))) {
            return loadOnnx(modelSpec);
        } else if (!Files.exists(p) && modelSpec.contains("/")) {
            return loadChat(modelSpec, request);
        } else if (Files.isDirectory(p) && hasGenAiOrConfig(p)) {
            return loadChat(modelSpec, request);
        } else {
            if (Files.exists(p) && Files.isDirectory(p)) {
                if (hasSmlFiles(p)) return loadSml(modelSpec);
                if (hasOnnxFiles(p)) return loadOnnx(modelSpec);
            }
            if (ChatService.looksLikeHuggingFaceRepoId(modelSpec)) {
                return loadChat(modelSpec, request);
            }
            throw new BadRequestException("Unable to determine model kind for '" + modelSpec + "'. Specify 'kind' explicitly (sml, onnx, or llm).");
        }
    }

    private Map<String, Object> loadSml(String modelSpec) {
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        List<InferenceModel> loaded = inferenceService.load(p);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "loaded");
        result.put("kind", "sml");
        result.put("model", modelSpec);
        List<String> ids = loaded.stream().map(InferenceModel::id).toList();
        result.put("ids", ids);
        if (loaded.size() == 1) {
            result.put("id", loaded.getFirst().id());
            result.put("algorithm", loaded.getFirst().metadata().algorithm());
        }
        return result;
    }

    private Map<String, Object> loadOnnx(String modelSpec) {
        java.nio.file.Path p = java.nio.file.Path.of(modelSpec);
        List<OnnxModel> loaded = onnxService.load(p);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "loaded");
        result.put("kind", "onnx");
        result.put("model", modelSpec);
        List<String> ids = loaded.stream().map(OnnxModel::id).toList();
        result.put("ids", ids);
        if (loaded.size() == 1) {
            result.put("id", loaded.getFirst().id());
        }
        return result;
    }

    private Map<String, Object> loadChat(String modelSpec, JsonObject request) {
        return chatService.load(modelSpec, request);
    }

    private static boolean hasSmlFiles(java.nio.file.Path dir) {
        try (var s = Files.list(dir)) {
            return s.anyMatch(f -> Files.isRegularFile(f) && f.toString().endsWith(".sml"));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasOnnxFiles(java.nio.file.Path dir) {
        try (var s = Files.list(dir)) {
            return s.anyMatch(f -> Files.isRegularFile(f) && f.toString().endsWith(".onnx"));
        } catch (Exception e) {
            return false;
        }
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
        return ModelList.of(catalog.list());
    }

    /**
     * Retrieves a single model by id (OpenAI retrieve-model parity).
     *
     * <p>The path accepts ids that contain slashes (e.g. Hugging Face repo ids)
     * via a greedy path segment. Inference is <em>not</em> performed here —
     * use the type-specific endpoints for that.
     *
     * @param id the public model id.
     * @return the model object.
     * @throws NotFoundException if no loaded model has this id.
     */
    @GET
    @Path("/{id:.+}")
    public ModelObject retrieve(@PathParam("id") String id) {
        return catalog.find(id, true)
                .orElseThrow(() -> new NotFoundException("Model not found: " + id));
    }
}
