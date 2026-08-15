/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;
import smile.serve.InferenceService;
import smile.serve.OnnxService;

/**
 * OpenAI-compatible models API at {@code /api/v1/models}.
 *
 * <p>Lists every loaded model in one catalog: chat LLMs, ONNX graphs, and
 * SMILE {@code .sml} models. Classic SMILE inference remains under
 * {@code /api/v1/ml/models/{id}}; ONNX inference under {@code /api/v1/onnx/{id}}.
 *
 * @author Haifeng Li
 * @see <a href="https://developers.openai.com/api/reference/resources/models/methods/list">OpenAI List models</a>
 */
@Path("/models")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
public class ModelsResource {

    @Inject
    ChatService chatService;

    @Inject
    InferenceService inferenceService;

    @Inject
    OnnxService onnxService;

    /**
     * Lists all currently available models (chat, ONNX, and SMILE).
     *
     * @return OpenAI-shaped {@code { object: "list", data: [...] }}.
     */
    @GET
    public ModelList list() {
        List<ModelObject> data = new ArrayList<>();
        data.addAll(chatService.listModels());
        data.addAll(inferenceService.listOpenAiModels());
        data.addAll(onnxService.listOpenAiModels());
        return ModelList.of(data);
    }
}
