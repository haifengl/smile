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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import io.smallrye.common.annotation.RunOnVirtualThread;

/**
 * Serves stored multimedia blobs at {@code /api/v1/media/{content_id}}.
 *
 * @author Haifeng Li
 */
@Path("/media")
@RunOnVirtualThread
public class MediaResource {

    @Inject
    MediaService mediaService;

    /**
     * Streams a stored blob.
     *
     * @param contentId opaque content UUID.
     * @param download  when {@code true}, set {@code Content-Disposition: attachment}.
     * @return binary response.
     */
    @GET
    @Path("/{content_id}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response get(@PathParam("content_id") String contentId,
                        @QueryParam("download") Boolean download) {
        MediaService.MediaBytes media = mediaService.getBytes(contentId);
        ConversationContent meta = media.meta();
        String mime = meta.mimeType;
        if (mime == null || mime.isBlank()) {
            mime = MediaType.APPLICATION_OCTET_STREAM;
        }
        Response.ResponseBuilder builder = Response.ok(media.bytes())
                .type(mime);
        String filename = meta.filename;
        if (filename == null || filename.isBlank()) {
            filename = contentId;
        }
        boolean asAttachment = download != null && download;
        String disposition = (asAttachment ? "attachment" : "inline")
                + "; filename=\"" + sanitizeFilename(filename) + "\"";
        builder.header("Content-Disposition", disposition);
        builder.header("Content-Length", media.bytes().length);
        return builder.build();
    }

    private static String sanitizeFilename(String name) {
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
