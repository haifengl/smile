/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;
import smile.chat.blob.ClientIdentity;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import smile.auth.AuthContext;

/**
 * REST resource for conversations at {@code /api/v1/conversations}.
 *
 * <p>Create, retrieve, update, and delete follow the
 * <a href="https://developers.openai.com/api/reference/resources/conversations">OpenAI
 * Conversations API</a> shapes. {@link #list} is a smile extension (OpenAI has
 * no list endpoint).
 *
 * @author Haifeng Li
 */
@Path("/conversations")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationResource {

    @Inject
    RoutingContext routingContext;

    @Inject
    MediaService mediaService;

    @Inject
    AuthContext authContext;

    @Inject
    ConversationService conversationService;

    private String clientIp(HttpHeaders headers) {
        return ClientIdentity.resolveClientIp(routingContext, headers);
    }

    /**
     * Lists the authenticated user's conversations (smile extension).
     *
     * @param pageIndex zero-based page index (default {@code 0}).
     * @param pageSize  number of records per page (default {@code 25}).
     * @param q         optional search query (title + message content).
     * @param pinned    when {@code true}, only pinned conversations.
     * @return a page of conversation objects.
     */
    @GET
    public List<ConversationObject> list(@QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                         @QueryParam("pageSize") @DefaultValue("25") int pageSize,
                                         @QueryParam("q") String q,
                                         @QueryParam("pinned") Boolean pinned) {
        List<Conversation> rows = conversationService.listForUser(pageIndex, pageSize, q, pinned);
        Map<Long, Long> counts = conversationService.messageCounts(
                rows.stream().map(c -> c.id).toList());
        return rows.stream()
                .map(c -> ConversationObject.from(c, counts.getOrDefault(c.id, 0L)))
                .toList();
    }

    /**
     * Retrieves a conversation ({@code GET /conversations/{conversation_id}}).
     *
     * @param conversationId external conversation id.
     * @param requestContext request context for IP check.
     * @return the conversation object.
     */
    @GET
    @Path("/{conversation_id}")
    public ConversationObject get(@PathParam("conversation_id") String conversationId,
                                  @Context HttpHeaders headers) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        return ConversationObject.from(conversation);
    }

    /**
     * Creates a conversation ({@code POST /conversations}).
     *
     * @param headers HTTP headers (client IP / user-agent capture).
     * @param request optional body with {@code metadata} and {@code items}.
     * @return the created conversation object.
     */
    @POST
    @Transactional
    public ConversationObject create(@Context HttpHeaders headers, CreateConversationRequest request) {
        if (request == null) {
            request = new CreateConversationRequest();
        }
        ConversationIds.validateMetadata(request.metadata);
        if (request.items != null && request.items.size() > 20) {
            throw new BadRequestException("items may contain at most 20 entries");
        }

        Conversation conversation = new Conversation();
        conversation.setContext(routingContext, headers);
        if (authContext.isLoggedIn()) {
            conversation.userId = authContext.userId();
        }
        if (request.metadata != null) {
            conversation.metadata = new HashMap<>(request.metadata);
        }
        conversation.persist();
        ConversationService.ensureDefaultTitle(conversation);

        if (request.items != null) {
            for (ConversationItemInput item : request.items) {
                persistInputItem(conversation.id, item);
            }
            String firstUser = firstUserMessageText(request.items);
            if (firstUser != null) {
                String title = ConversationService.titleFromFirstMessage(firstUser);
                if (title != null) {
                    conversation.title = title;
                }
            }
        }
        return ConversationObject.from(conversation);
    }

    /**
     * Updates conversation metadata ({@code POST /conversations/{conversation_id}}).
     *
     * @param conversationId external conversation id.
     * @param request        body containing replacement {@code metadata}.
     * @param requestContext request context.
     * @return the updated conversation object.
     */
    @POST
    @Path("/{conversation_id}")
    @Transactional
    public ConversationObject update(@PathParam("conversation_id") String conversationId,
                                     UpdateConversationRequest request,
                                     @Context HttpHeaders headers) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        if (request != null && request.metadata != null) {
            ConversationIds.validateMetadata(request.metadata);
            conversation.metadata = new HashMap<>(request.metadata);
        }
        return ConversationObject.from(conversation);
    }

    /**
     * Patches smile sidebar fields (title, pinned).
     *
     * @param conversationId external id.
     * @param request        patch body.
     * @param requestContext request context.
     * @return updated conversation.
     */
    @PATCH
    @Path("/{conversation_id}")
    @Transactional
    public ConversationObject patch(@PathParam("conversation_id") String conversationId,
                                      PatchConversationRequest request,
                                      @Context HttpHeaders headers) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        if (authContext.userId() == null
                || !authContext.userId().equals(conversation.userId)) {
            throw new jakarta.ws.rs.ForbiddenException("Only owned conversations can be patched");
        }
        if (request != null) {
            if (request.title != null) {
                String title = request.title.trim();
                conversation.title = title.isEmpty()
                        ? ConversationService.defaultTitle(conversation.createdAt)
                        : (title.length() > 256 ? title.substring(0, 256) : title);
            }
            if (request.pinned != null) {
                conversation.pinned = request.pinned;
            }
        }
        return ConversationObject.from(conversation);
    }

    /**
     * Deletes a conversation ({@code DELETE /conversations/{conversation_id}}).
     *
     * @param conversationId external conversation id.
     * @param requestContext request context.
     * @return OpenAI-compatible deleted resource acknowledgement.
     */
    @DELETE
    @Path("/{conversation_id}")
    @Transactional
    public ConversationDeleted delete(@PathParam("conversation_id") String conversationId,
                                      @Context HttpHeaders headers) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        String externalId = ConversationIds.toExternalId(conversation.id);
        mediaService.deleteConversationMedia(conversation.id);
        ConversationItem.delete("conversationId", conversation.id);
        conversation.delete();
        return ConversationDeleted.of(externalId);
    }

    /**
     * Uploads a multimedia attachment for a conversation.
     *
     * @param conversationId external conversation id.
     * @param headers        HTTP headers.
     * @param file           multipart file field named {@code file}.
     * @param role           optional role ({@code user} default).
     * @param requestContext request context.
     * @return content metadata including {@code url}.
     */
    @POST
    @Path("/{conversation_id}/content")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public ContentObject uploadContent(@PathParam("conversation_id") String conversationId,
                                       @Context HttpHeaders headers,
                                       @RestForm("file") FileUpload file,
                                       @RestForm("role") String role) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        return mediaService.upload(conversation, file, role, routingContext, headers);
    }

    /**
     * Returns message turns for a conversation.
     *
     * @param conversationId external conversation id.
     * @param pageIndex      zero-based page index (default {@code 0}).
     * @param pageSize       number of items per page (default {@code 100}).
     * @param requestContext request context.
     * @return conversation items in chronological order.
     */
    @GET
    @Path("/{conversation_id}/items")
    public List<ConversationItem> getItems(@PathParam("conversation_id") String conversationId,
                                           @QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                           @QueryParam("pageSize") @DefaultValue("100") int pageSize,
                                           @Context HttpHeaders headers) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        conversationService.ensureAccess(conversation, clientIp(headers));
        return ConversationItem.find("conversationId", io.quarkus.panache.common.Sort.by("createdAt"),
                        conversation.id)
                .page(io.quarkus.panache.common.Page.of(pageIndex, pageSize))
                .list();
    }

    private static String firstUserMessageText(List<ConversationItemInput> items) {
        if (items == null) {
            return null;
        }
        for (ConversationItemInput item : items) {
            if (item == null || item.role == null || !"user".equalsIgnoreCase(item.role)) {
                continue;
            }
            String text = item.toStoredContent();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static void persistInputItem(long conversationId, ConversationItemInput item) {
        if (item == null || item.role == null || item.role.isBlank()) {
            return;
        }
        String text = item.toStoredContent();
        if (text == null) {
            return;
        }
        ConversationItem row = new ConversationItem();
        row.conversationId = conversationId;
        row.role = item.role;
        row.content = text;
        row.persist();
    }
}
