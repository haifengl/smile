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

import java.util.HashMap;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;

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

    /**
     * Lists conversations in reverse chronological order (smile extension).
     *
     * @param pageIndex zero-based page index (default {@code 0}).
     * @param pageSize  number of records per page (default {@code 25}).
     * @return a page of OpenAI-shaped conversation objects.
     */
    @GET
    public List<ConversationObject> list(@QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                         @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        return Conversation.findAll(Sort.by("createdAt").descending())
                .page(Page.of(pageIndex, pageSize))
                .<Conversation>list()
                .stream()
                .map(ConversationObject::from)
                .toList();
    }

    /**
     * Retrieves a conversation ({@code GET /conversations/{conversation_id}}).
     *
     * @param conversationId external conversation id.
     * @return the conversation object.
     */
    @GET
    @Path("/{conversation_id}")
    public ConversationObject get(@PathParam("conversation_id") String conversationId) {
        return ConversationObject.from(ConversationIds.findRequired(conversationId));
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
        if (request.metadata != null) {
            conversation.metadata = new HashMap<>(request.metadata);
        }
        conversation.persist();

        if (request.items != null) {
            for (ConversationItemInput item : request.items) {
                persistInputItem(conversation.id, item);
            }
        }
        return ConversationObject.from(conversation);
    }

    /**
     * Updates conversation metadata ({@code POST /conversations/{conversation_id}}).
     *
     * @param conversationId external conversation id.
     * @param request        body containing replacement {@code metadata}.
     * @return the updated conversation object.
     */
    @POST
    @Path("/{conversation_id}")
    @Transactional
    public ConversationObject update(@PathParam("conversation_id") String conversationId,
                                     UpdateConversationRequest request) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        if (request != null && request.metadata != null) {
            ConversationIds.validateMetadata(request.metadata);
            conversation.metadata = new HashMap<>(request.metadata);
        }
        return ConversationObject.from(conversation);
    }

    /**
     * Deletes a conversation ({@code DELETE /conversations/{conversation_id}}).
     *
     * <p>Matching {@link ConversationItem} rows are removed as well so the
     * local database stays consistent (OpenAI keeps remote items; smile does not).
     *
     * @param conversationId external conversation id.
     * @return OpenAI-compatible deleted resource acknowledgement.
     */
    @DELETE
    @Path("/{conversation_id}")
    @Transactional
    public ConversationDeleted delete(@PathParam("conversation_id") String conversationId) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        String externalId = ConversationIds.toExternalId(conversation.id);
        ConversationItem.delete("conversationId", conversation.id);
        conversation.delete();
        return ConversationDeleted.of(externalId);
    }

    /**
     * Returns message turns for a conversation (smile extension; not the OpenAI
     * items API).
     *
     * @param conversationId external conversation id.
     * @param pageIndex      zero-based page index (default {@code 0}).
     * @param pageSize       number of items per page (default {@code 25}).
     * @return a page of conversation items in chronological order.
     */
    @GET
    @Path("/{conversation_id}/items")
    public List<ConversationItem> getItems(@PathParam("conversation_id") String conversationId,
                                           @QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                           @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        Conversation conversation = ConversationIds.findRequired(conversationId);
        return ConversationItem.find("conversationId", Sort.by("createdAt"), conversation.id)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    /**
     * Persists a create-time input item when it has a role and extractable text.
     */
    private static void persistInputItem(long conversationId, ConversationItemInput item) {
        if (item == null || item.role == null || item.role.isBlank()) {
            return;
        }
        String text = item.contentText();
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
