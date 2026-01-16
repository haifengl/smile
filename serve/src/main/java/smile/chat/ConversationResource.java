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

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import io.vertx.ext.web.RoutingContext;

@Path("/conversations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConversationResource {
    @Inject
    RoutingContext routingContext;

    @GET
    public List<Conversation> list() {
        return Conversation.listAll(); // Panache method
    }

    @GET
    @Path("/{id}")
    public Conversation get(@PathParam("id") Long id) {
        return Conversation.findById(id); // Panache method
    }

    @POST
    @Transactional
    public Response create(@Context HttpHeaders headers, Conversation conversation) {
        String clientIP = routingContext.request().remoteAddress().hostAddress();

        // Check for common headers if behind a proxy
        String forwardedFor = routingContext.request().getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            clientIP = forwardedFor.split(",")[0].trim();
        }
        conversation.clientIP = clientIP;
        conversation.userAgent = headers.getHeaderString("User-Agent");
        conversation.persist();
        return Response.status(Response.Status.CREATED).entity(conversation).build();
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response deleteProduct(@PathParam("id") Long id) {
        boolean deleted = Conversation.deleteById(id);
        if (deleted) {
            return Response.noContent().build(); // 204 No Content
        } else {
            return Response.status(Response.Status.NOT_FOUND).build(); // 404 Not Found
        }
    }
}
