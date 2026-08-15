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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Rewrites {@code Accept} for {@code POST .../chat/completions} from the
 * request body's {@code stream} flag so the correct resource method is chosen.
 *
 * <p>OpenAI SDKs send {@code Accept: application/json} even when streaming.
 * The entity is read on a worker thread (event-loop safe), then restored for
 * JAX-RS deserialization.
 *
 * @author Haifeng Li
 */
public class ChatCompletionsAcceptFilter {

    /**
     * Sets {@code Accept} to SSE or JSON based on {@code stream}.
     *
     * @param requestContext the incoming request.
     * @return a Uni that completes after Accept is rewritten.
     */
    @ServerRequestFilter(preMatching = true)
    public Uni<Void> rewriteAccept(ContainerRequestContext requestContext) {
        if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
            return Uni.createFrom().voidItem();
        }
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !path.contains("chat/completions")) {
            return Uni.createFrom().voidItem();
        }

        InputStream entityStream = requestContext.getEntityStream();
        if (entityStream == null) {
            requestContext.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom().item(() -> {
            try {
                byte[] body = entityStream.readAllBytes();
                requestContext.setEntityStream(new ByteArrayInputStream(body));
                if (ChatCompletionsStreamFlag.streamFlag(body)) {
                    requestContext.getHeaders().putSingle(
                            HttpHeaders.ACCEPT, MediaType.SERVER_SENT_EVENTS);
                } else {
                    requestContext.getHeaders().putSingle(
                            HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON);
                }
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool()).replaceWithVoid();
    }
}
