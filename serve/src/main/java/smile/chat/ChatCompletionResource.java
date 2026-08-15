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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Role;

/**
 * REST resource exposing the OpenAI-compatible chat completion API at
 * {@code /api/v1/chat/completions}.
 *
 * <p>When {@code stream} is true (smile default), tokens are streamed as SSE
 * {@code chat.completion.chunk} events ending with {@code [DONE]}.
 * When {@code stream} is false, a single {@code chat.completion} JSON body is
 * returned.
 *
 * <p>The method is produced as {@code application/json} so OpenAI clients that
 * always send {@code Accept: application/json} match. Streaming responses set
 * {@code Content-Type: text/event-stream} on the {@link RestResponse}.
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

    @Inject
    ObjectMapper objectMapper;

    /**
     * Chat completion — JSON when {@code stream: false}, SSE when streaming.
     *
     * @param headers HTTP request headers.
     * @param request completion request.
     * @return JSON completion object or SSE multi of chunk payloads.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    @Blocking
    public RestResponse<?> complete(@Context HttpHeaders headers, CompletionRequest request) {
        validate(request);
        Conversation conversation = newConversation(headers);
        String id = newCompletionId();
        long created = Instant.now().getEpochSecond();
        String modelName = service.modelName();

        if (!request.isStream()) {
            ChatCompletion[] completions = service.complete(request, null);
            if (completions != null) {
                saveConversation(conversation, request, completions);
            }
            return RestResponse.ok(ChatCompletionObject.of(id, created, modelName, completions));
        }

        Multi<String> multi = streamMulti(id, created, modelName, conversation, request);
        return RestResponse.ResponseBuilder.ok(multi)
                .type(MediaType.SERVER_SENT_EVENTS_TYPE)
                .build();
    }

    private Multi<String> streamMulti(String id, long created, String modelName,
                                      Conversation conversation, CompletionRequest request) {
        return Multi.createFrom().emitter(emitter -> {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            CompletableFuture<ChatCompletion[]> resultFuture = new CompletableFuture<>();
            SubmissionPublisher<String> publisher =
                    new SubmissionPublisher<>(Runnable::run, Flow.defaultBufferSize());

            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(String chunk) {
                    if (emitter.isCancelled()) {
                        publisher.close();
                        return;
                    }
                    boolean first = isFirst.compareAndSet(true, false);
                    var delta = first
                            ? new ChatCompletionChunk.Delta("assistant", chunk)
                            : new ChatCompletionChunk.Delta(null, chunk);
                    var choice = new ChatCompletionChunk.Choice(0, delta, null, null);
                    var event = new ChatCompletionChunk(id, "chat.completion.chunk", created, modelName, List.of(choice));
                    emitter.emit(toJson(event));
                }

                @Override
                public void onError(Throwable throwable) {
                    resultFuture.completeExceptionally(throwable);
                    if (!emitter.isCancelled()) {
                        emitter.fail(throwable);
                    }
                }

                @Override
                public void onComplete() {
                    resultFuture.whenComplete((completions, error) -> {
                        if (emitter.isCancelled()) {
                            return;
                        }
                        if (error != null) {
                            emitter.fail(error);
                            return;
                        }
                        FinishReason reason = (completions != null && completions.length > 0)
                                ? completions[0].reason()
                                : FinishReason.stop;
                        var delta = new ChatCompletionChunk.Delta(null, null);
                        var choice = new ChatCompletionChunk.Choice(0, delta, null, reason);
                        var event = new ChatCompletionChunk(id, "chat.completion.chunk", created, modelName, List.of(choice));
                        emitter.emit(toJson(event));
                        emitter.emit("[DONE]");
                        emitter.complete();
                    });
                }
            });

            emitter.onTermination(() -> {
                try {
                    publisher.close();
                } catch (Exception ignored) {
                    // already closed
                }
            });

            executor.supplyAsync(() -> {
                try {
                    var completions = service.complete(request, publisher);
                    resultFuture.complete(completions);
                    if (completions != null) {
                        saveConversation(conversation, request, completions);
                    }
                    return completions;
                } catch (Throwable t) {
                    resultFuture.completeExceptionally(t);
                    if (!publisher.isClosed()) {
                        publisher.close();
                    }
                    return null;
                }
            });
        });
    }

    private void validate(CompletionRequest request) {
        if (!service.isAvailable()) {
            throw new ServiceUnavailableException();
        }
        if (!service.acceptsModel(request.model)) {
            throw new NotFoundException(
                    "The model `" + request.model + "` does not exist or is not loaded (loaded: `"
                            + service.modelName() + "`)");
        }
    }

    private Conversation newConversation(HttpHeaders headers) {
        Conversation conversation = new Conversation();
        conversation.setContext(routingContext, headers);
        return conversation;
    }

    private static String newCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Persists the user message and assistant reply(ies) for this turn.
     *
     * @param conversation the conversation context captured from the request.
     * @param request      the original completion request.
     * @param completions  the generated completions returned by the model.
     */
    @Transactional
    public void saveConversation(Conversation conversation,
                                  CompletionRequest request,
                                  ChatCompletion[] completions) {
        Long conversationId = ConversationIds.parseOptional(request.conversation);
        if (conversationId == null) {
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize SSE chunk", e);
        }
    }
}
