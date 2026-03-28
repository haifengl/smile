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

@Path("/conversations")
@RunOnVirtualThread
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationResource {
    @Inject
    RoutingContext routingContext;

    @GET
    public List<Conversation> list(@QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
                                   @QueryParam("pageSize") @DefaultValue("25") int pageSize) {
        return Conversation.findAll(Sort.by("createdAt").descending())
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    @GET
    @Path("/{id}")
    public Conversation get(@PathParam("id") Long id) {
        return Conversation.findById(id);
    }

    @POST
    @Transactional
    public Response create(@Context HttpHeaders headers, Conversation conversation) {
        conversation.setContext(routingContext, headers);
        conversation.persist();
        return Response.status(Response.Status.CREATED).entity(conversation).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        boolean deleted = Conversation.deleteById(id);
        if (deleted) {
            return Response.noContent().build(); // 204 No Content
        } else {
            return Response.status(Response.Status.NOT_FOUND).build(); // 404 Not Found
        }
    }

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
