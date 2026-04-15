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

import java.util.concurrent.SubmissionPublisher;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Multi;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.llm.ChatCompletion;
import smile.llm.Role;

/**
 * REST resource exposing the OpenAI-compatible chat completion API at
 * {@code /api/v1/chat/completions}.
 *
 * <p>The endpoint streams generated tokens back to the client as plain-text
 * chunks via server-sent events. Conversation history is persisted to the
 * configured database after generation completes.
 *
 * @author Haifeng Li
 */
@Path("/chat/completions")
public class ChatCompletionResource {

    @Inject
    ChatService service;

    @Inject
    RoutingContext routingContext;

    @Inject
    ManagedExecutor executor;

    /**
     * Generates a chat completion for the supplied dialog.
     *
     * <p>The response is streamed token by token. Each emitted item is one
     * text chunk preceded by a space so that SSE clients do not swallow the
     * first character after the {@code data:} prefix.
     *
     * @param headers HTTP request headers (used to capture client metadata).
     * @param request the completion request containing the message history
     *                and generation parameters.
     * @return a reactive stream of generated text chunks.
     * @throws ServiceUnavailableException if the LLM model is not loaded.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> complete(@Context HttpHeaders headers, CompletionRequest request)
            throws ServiceUnavailableException {
        if (!service.isAvailable()) throw new ServiceUnavailableException();

        Conversation conversation = new Conversation();
        // Must capture routing context on the endpoint thread; it is not available
        // inside the worker thread dispatched by executor.supplyAsync.
        conversation.setContext(routingContext, headers);

        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        executor.supplyAsync(() -> {
            var completions = service.complete(request, publisher);
            if (completions != null) {
                saveConversation(conversation, request, completions);
            }
            return completions;
        });
        return Multi.createFrom()
                .publisher(publisher)
                .map(chunk -> " " + chunk); // leading space prevents SSE client from eating first char
    }

    /**
     * Persists the user message and assistant reply(ies) for this turn.
     *
     * <p>If the {@link CompletionRequest#conversation} ID is absent or
     * non-positive, a new {@link Conversation} record is created first.
     *
     * @param conversation the conversation context captured from the request.
     * @param request      the original completion request.
     * @param completions  the generated completions returned by the model.
     */
    @Transactional
    public void saveConversation(Conversation conversation,
                                  CompletionRequest request,
                                  ChatCompletion[] completions) {
        Long conversationId = request.conversation;
        if (conversationId == null || conversationId <= 0) {
            conversation.persist();
            conversationId = conversation.id;
        }

        // Persist the last user message in this turn.
        for (int i = request.messages.length; i-- > 0;) {
            var message = request.messages[i];
            if (message.role() == Role.user) {
                ConversationItem item = new ConversationItem();
                item.conversationId = conversationId;
                item.role = message.role().toString();
                item.content = message.content();
                item.persist();
                break;
            }
        }

        // Persist each assistant completion.
        for (var completion : completions) {
            ConversationItem item = new ConversationItem();
            item.conversationId = conversationId;
            item.role = Role.assistant.toString();
            item.content = completion.content();
            item.persist();
        }
    }
}
