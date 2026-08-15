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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Multi;
import io.vertx.ext.web.RoutingContext;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Role;

/**
 * REST resource exposing the OpenAI-compatible chat completion API at
 * {@code /api/v1/chat/completions}.
 *
 * <p>The endpoint streams generated tokens back to the client as server-sent
 * events. Each SSE {@code data:} payload is a JSON object following the
 * OpenAI Chat Completions streaming format ({@code object: "chat.completion.chunk"}).
 * The stream is terminated by a {@code data: [DONE]} sentinel event.
 * Conversation history is persisted to the configured database after generation
 * completes.
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
     * Generates a chat completion for the supplied dialog.
     *
     * <p>The response is streamed token by token as SSE events. Each event
     * payload is a JSON {@code ChatCompletionChunk} object. The final data
     * event is the literal string {@code [DONE]}.
     *
     * <p>Generation starts only after the returned {@link Multi} is subscribed
     * (i.e. after the SSE response is opened), so token chunks are not dropped
     * by {@link SubmissionPublisher} before a subscriber exists.
     *
     * @param headers HTTP request headers (used to capture client metadata).
     * @param request the completion request containing the message history
     *                and generation parameters.
     * @return a reactive stream of SSE data payloads.
     * @throws ServiceUnavailableException if the LLM model is not loaded.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> complete(@Context HttpHeaders headers, CompletionRequest request)
            throws ServiceUnavailableException {
        if (!service.isAvailable()) throw new ServiceUnavailableException();

        Conversation conversation = new Conversation();
        // Must capture routing context on the endpoint thread; it is not available
        // inside the worker thread dispatched by executor.supplyAsync.
        conversation.setContext(routingContext, headers);

        String id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
        long created = Instant.now().getEpochSecond();
        String modelName = service.modelName();

        // Emitter runs on subscription — subscribe the publisher before generate().
        return Multi.createFrom().emitter(emitter -> {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            // Completed with ChatCompletion[] after generate returns (publisher may
            // already have closed). Finish/[DONE] wait on this so they never race
            // ahead of asynchronously delivered content onNext calls.
            CompletableFuture<ChatCompletion[]> resultFuture = new CompletableFuture<>();
            // Deliver onNext/onComplete on the submitting thread so the last content
            // chunk is emitted before publisher.close() returns from generate().
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
                    // Guaranteed after all onNext. Wait for generate() to publish the
                    // finish reason, then terminate the SSE stream in OpenAI order:
                    // content deltas → finish_reason chunk → [DONE].
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

    /**
     * Persists the user message and assistant reply(ies) for this turn.
     *
     * <p>If {@link CompletionRequest#conversation} is absent or blank, a new
     * {@link Conversation} record is created first.
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

    /**
     * Serializes an object to a JSON string, wrapping any checked exception.
     *
     * @param value the object to serialize.
     * @return the JSON string.
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize SSE chunk", e);
        }
    }
}
