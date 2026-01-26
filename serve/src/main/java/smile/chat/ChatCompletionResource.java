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
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.llm.ChatCompletion;
import smile.llm.Role;

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
    RoutingContext routingContext;

    @Inject
    ManagedExecutor executor;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.TEXT_PLAIN) // Important for streaming item by item without buffering
    public Multi<String> complete(@Context HttpHeaders headers, CompletionRequest request) throws ServiceUnavailableException {
        if (!service.isAvailable()) throw new ServiceUnavailableException();
        Conversation conversation = new Conversation();
        // Must set context in the endpoint instead of supplyAsync.
        // Otherwise, routingContext is undefined in the worker thread.
        conversation.setContext(routingContext, headers);

        SubmissionPublisher<String> publisher = new SubmissionPublisher<>();
        executor.supplyAsync(() -> {
            var completions = service.complete(request, publisher);
            saveConversation(conversation, request, completions);
            return completions;
        });
        return Multi.createFrom()
                .publisher(publisher)
                .map(chunk -> " " + chunk); // in case client eats the space after 'data:'
    }

    @Transactional
    public void saveConversation(Conversation conversation,
                                 CompletionRequest request,
                                 ChatCompletion[] completions) {
        Long conversationId = request.conversation;
        if (conversationId == null || conversationId <= 0) {
            conversation.persist();
            conversationId = conversation.id;
        }

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

        for (var completion : completions) {
            ConversationItem item = new ConversationItem();
            item.conversationId = conversationId;
            item.role = Role.assistant.toString();
            item.content = completion.content();
            item.persist();
        }
    }
}
