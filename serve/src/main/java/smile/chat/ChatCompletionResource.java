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
package smile.chat;

import java.util.concurrent.SubmissionPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * Chat completion API.
 * @author Haifeng Li
 */
@Path("/chat/completions")
public class ChatCompletionResource {

    @Inject
    ChatService service;

    @Inject
    ObjectMapper objectMapper; // Inject the Quarkus-provided Jackson ObjectMapper

    @Inject
    ManagedExecutor executor;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.TEXT_PLAIN) // Important for streaming item by item without buffering
    public Multi<String> csv(CompletionRequest request) throws ServiceUnavailableException {
        if (!service.isAvailable()) throw new ServiceUnavailableException();
        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        executor.supplyAsync(() -> service.complete(request, publisher));
        return Multi.createFrom().publisher(publisher);
    }
}
