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
import jakarta.ws.rs.BadRequestException;
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
import org.jboss.resteasy.reactive.RestStreamElementType;
import smile.llm.ChatCompletion;
import smile.llm.FinishReason;
import smile.llm.Role;
import smile.auth.AuthContext;
import smile.chat.blob.ClientIdentity;

/**
 * REST resource exposing the OpenAI-compatible chat completion API at
 * {@code /api/v1/chat/completions}.
 *
 * <p>When {@code stream} is true, the method returns a
 * {@link Multi} of SSE payloads directly (required for Quarkus SSE framing).
 * When {@code stream} is false or omitted (OpenAI default), a single
 * {@code chat.completion} JSON body is returned. {@link ChatCompletionsAcceptFilter}
 * sets {@code Accept} from the body {@code stream} flag so the correct method is
 * selected even when clients send {@code Accept: application/json}.
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

    @Inject
    MediaService mediaService;

    @Inject
    AuthContext authContext;

    @Inject
    ConversationService conversationService;

    /**
     * Non-streaming chat completion ({@code stream: false} or omitted).
     *
     * @param headers HTTP request headers.
     * @param request completion request.
     * @return OpenAI {@code chat.completion} JSON object.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public ChatCompletionObject completeJson(@Context HttpHeaders headers, CompletionRequest request) {
        validate(request);
        Conversation conversation = newConversation(headers);
        String id = newCompletionId();
        long created = Instant.now().getEpochSecond();
        String modelName = service.modelName();

        ChatCompletion completion = service.complete(request, null);
        if (completion != null) {
            saveConversation(conversation, headers, request, completion);
        }
        return ChatCompletionObject.of(id, created, modelName, completion);
    }

    /**
     * Streaming chat completion ({@code stream: true}).
     *
     * <p>Must return {@link Multi} directly — wrapping it in {@code RestResponse}
     * causes Quarkus to write the Multi's {@code toString()} instead of SSE.
     *
     * @param headers HTTP request headers.
     * @param request completion request.
     * @return SSE stream of JSON chunk payloads plus a final {@code [DONE]}.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    public Multi<String> completeStream(@Context HttpHeaders headers, CompletionRequest request) {
        validate(request);
        Conversation conversation = newConversation(headers);
        String id = newCompletionId();
        long created = Instant.now().getEpochSecond();
        String modelName = service.modelName();

        return Multi.createFrom().emitter(emitter -> {
            AtomicBoolean isFirst = new AtomicBoolean(true);
            boolean toolsRequested;
            try {
                toolsRequested = request.toChatOptions().hasTools();
            } catch (IllegalArgumentException e) {
                toolsRequested = false;
            }
            final boolean suppressContentDeltas = toolsRequested;
            CompletableFuture<ChatCompletion> resultFuture = new CompletableFuture<>();
            SubmissionPublisher<String> publisher =
                    new SubmissionPublisher<>(Runnable::run, Flow.defaultBufferSize());
            // Set once submitCompletion returns; abort on disconnect.
            java.util.concurrent.atomic.AtomicReference<smile.llm.engine.GenerationHandle> handleRef =
                    new java.util.concurrent.atomic.AtomicReference<>();

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
                    if (suppressContentDeltas) {
                        // v1: buffer-and-replay tool_calls at end; skip raw XML text.
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
                    resultFuture.whenComplete((completion, error) -> {
                        if (emitter.isCancelled()) {
                            return;
                        }
                        if (error != null) {
                            emitter.fail(error);
                            return;
                        }
                        FinishReason reason = completion != null
                                ? completion.reason()
                                : FinishReason.stop;
                        if (completion != null && completion.hasToolCalls()) {
                            boolean firstTool = true;
                            for (var deltas : StreamingToolCallAssembler.replayDeltas(completion)) {
                                String role = firstTool ? "assistant" : null;
                                firstTool = false;
                                var delta = new ChatCompletionChunk.Delta(role, null, deltas);
                                var choice = new ChatCompletionChunk.Choice(0, delta, null, null);
                                var event = new ChatCompletionChunk(
                                        id, "chat.completion.chunk", created, modelName, List.of(choice));
                                emitter.emit(toJson(event));
                            }
                        } else if (suppressContentDeltas && completion != null
                                && completion.content() != null && !completion.content().isEmpty()) {
                            // Final answer after a tools-enabled turn: emit cleaned
                            // content only (think / tool XML / chat specials stripped).
                            var delta = new ChatCompletionChunk.Delta("assistant", completion.content());
                            var choice = new ChatCompletionChunk.Choice(0, delta, null, null);
                            var event = new ChatCompletionChunk(
                                    id, "chat.completion.chunk", created, modelName, List.of(choice));
                            emitter.emit(toJson(event));
                        }
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
                smile.llm.engine.GenerationHandle h = handleRef.get();
                if (h != null) {
                    h.abort();
                }
                try {
                    publisher.close();
                } catch (Exception ignored) {
                    // already closed
                }
            });

            executor.supplyAsync(() -> {
                try {
                    var handle = service.submitCompletion(request, publisher);
                    handleRef.set(handle);
                    if (emitter.isCancelled()) {
                        handle.abort();
                    }
                    var completion = handle.future().join();
                    resultFuture.complete(completion);
                    if (completion != null) {
                        saveConversation(conversation, headers, request, completion);
                    }
                    if (!publisher.isClosed()) {
                        publisher.close();
                    }
                    return completion;
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
        if (request.messages != null) {
            for (var message : request.messages) {
                if (message != null && message.hasAudio()) {
                    throw new BadRequestException(
                            "Audio input is not supported by this model");
                }
            }
        }
        try {
            request.toChatOptions();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
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
        if (authContext.isLoggedIn()) {
            conversation.userId = authContext.userId();
        }
        return conversation;
    }

    private static String newCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Persists the user message and assistant reply for this turn.
     *
     * @param conversation the conversation context captured from the request.
     * @param request      the original completion request.
     * @param completion   the generated completion returned by the model.
     */
    @Transactional
    public void saveConversation(Conversation scratch,
                                  HttpHeaders headers,
                                  CompletionRequest request,
                                  ChatCompletion completion) {
        Long conversationId = ConversationIds.parseOptional(request.conversation);
        Conversation conversation;
        String clientIp = ClientIdentity.resolveClientIp(routingContext, headers);
        if (conversationId != null) {
            conversation = Conversation.findById(conversationId);
            if (conversation == null) {
                throw new NotFoundException("Conversation not found: " + request.conversation);
            }
            conversationService.ensureAccess(conversation, clientIp);
        } else {
            scratch.persist();
            conversation = scratch;
            conversationId = conversation.id;
        }

        String userMessageText = null;
        for (int i = request.messages.length; i-- > 0;) {
            var message = request.messages[i];
            if (message.role() == Role.user) {
                userMessageText = MessageContentCodec.toStoredContent(message);
                ConversationItem item = new ConversationItem();
                item.conversationId = conversationId;
                item.role = message.role().toString();
                item.content = userMessageText;
                item.persist();
                mediaService.linkToMessage(
                        conversationId, item.id, MessageContentCodec.mediaContentIds(message));
                break;
            }
        }

        if (completion != null) {
            ConversationItem item = new ConversationItem();
            item.conversationId = conversationId;
            item.role = Role.assistant.toString();
            if (completion.hasToolCalls()) {
                try {
                    item.content = objectMapper.writeValueAsString(ChatMessageObject.of(completion));
                } catch (JsonProcessingException e) {
                    item.content = completion.content() != null ? completion.content() : "";
                }
            } else {
                item.content = completion.content() != null ? completion.content() : "";
            }
            item.persist();
        }

        if (userMessageText != null) {
            conversationService.touchAfterMessage(conversation, userMessageText);
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
