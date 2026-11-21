/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.arc.profile.UnlessBuildProfile;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.vertx.ext.web.RoutingContext;
import smile.auth.AuthConfig;
import smile.auth.AuthContext;
import smile.auth.UserService;
import smile.chat.blob.ClientIdentity;
import smile.chat.blob.MediaTypeValidator;

/**
 * Serves stored multimedia blobs at {@code /api/v1/media/{content_id}} with
 * conversation ownership verification and defensive response headers.
 *
 * @author Haifeng Li
 */
@UnlessBuildProfile("micro")
@Path("/api/v1/media")
@RunOnVirtualThread
public class MediaResource {

    @Inject
    MediaService mediaService;

    @Inject
    RoutingContext routingContext;

    @Inject
    AuthConfig authConfig;

    @Inject
    AuthContext authContext;

    @Inject
    UserService userService;

    /**
     * Streams a stored blob to its authorized owner.
     *
     * @param contentId opaque content UUID.
     * @param download  when {@code true}, forces {@code Content-Disposition: attachment}.
     * @param headers   HTTP request headers.
     * @return binary response with defensive security headers.
     */
    @GET
    @Path("/{content_id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(@PathParam("content_id") String contentId,
                        @QueryParam("download") Boolean download,
                        @Context HttpHeaders headers) {
        if (userService != null) {
            userService.ensureAuthenticatedUser(authContext);
        }
        String clientIp = ClientIdentity.resolveClientIp(
                routingContext, headers, authConfig.trustForwardedHeaders());
        MediaService.MediaBytes media = mediaService.getBytes(contentId, clientIp);
        ConversationContent meta = media.meta();
        String mime = meta.mimeType;
        if (mime == null || mime.isBlank()) {
            mime = MediaType.APPLICATION_OCTET_STREAM;
        }
        boolean asAttachment = Boolean.TRUE.equals(download)
                || !MediaTypeValidator.isSafeInline(mime);
        String disposition = MediaTypeValidator.contentDisposition(
                asAttachment, meta.filename, contentId);

        return Response.ok(media.bytes())
                .type(mime)
                .header("Content-Disposition", disposition)
                .header("Content-Length", media.bytes().length)
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Referrer-Policy", "no-referrer")
                .header("Cross-Origin-Resource-Policy", "same-origin")
                .header("Cache-Control", "private, no-store")
                .build();
    }
}
