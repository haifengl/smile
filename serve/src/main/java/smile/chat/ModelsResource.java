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

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * OpenAI-compatible models API at {@code /api/v1/models}.
 *
 * <p>Lists the chat LLM(s) currently loaded by {@link ChatService}. Classic
 * SMILE {@code .sml} inference lives under {@code /api/v1/ml/models}.
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

    /**
     * Lists currently available chat models.
     *
     * @return OpenAI-shaped {@code { object: "list", data: [...] }}.
     */
    @GET
    public ModelList list() {
        return ModelList.of(chatService.listModels());
    }
}
