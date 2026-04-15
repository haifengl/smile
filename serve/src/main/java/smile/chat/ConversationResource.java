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
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;

/**
 * REST resource exposing CRUD operations on persisted {@link Conversation}
 * records at {@code /api/v1/conversations}.
 *
 * <p>All methods run on virtual threads to avoid blocking the event loop.
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
     * Lists conversations in reverse chronological order.
     *
     * @param pageIndex zero-based page index (default {@code 0}).
     * @param pageSize  number of records per page (default {@code 25}).
     * @return a page of conversations.
     */
    @GET
    public List<Conversation> list(@QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                   @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        return Conversation.findAll(Sort.by("createdAt").descending())
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    /**
     * Returns the conversation with the given ID.
     *
     * @param id the conversation ID.
     * @return the conversation, or HTTP 404 if not found.
     */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") Long id) {
        Conversation c = Conversation.findById(id);
        if (c == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(c).build();
    }

    /**
     * Creates a new conversation record.
     *
     * @param headers      HTTP request headers (for client metadata capture).
     * @param conversation the conversation to persist.
     * @return HTTP 201 with the created entity.
     */
    @POST
    @Transactional
    public Response create(@Context HttpHeaders headers, Conversation conversation) {
        conversation.setContext(routingContext, headers);
        conversation.persist();
        return Response.status(Response.Status.CREATED).entity(conversation).build();
    }

    /**
     * Deletes the conversation with the given ID.
     *
     * @param id the conversation ID.
     * @return HTTP 204 on success, HTTP 404 if not found.
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Conversation.deleteById(id);
        return deleted
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * Returns the message turns belonging to a conversation.
     *
     * @param id        the conversation ID.
     * @param pageIndex zero-based page index (default {@code 0}).
     * @param pageSize  number of items per page (default {@code 25}).
     * @return a page of conversation items in chronological order.
     */
    @GET
    @Path("/{id}/items")
    public List<ConversationItem> getItems(@PathParam("id") Long id,
                                           @QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                           @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        return ConversationItem.find("conversationId", Sort.by("createdAt"), id)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }
}
