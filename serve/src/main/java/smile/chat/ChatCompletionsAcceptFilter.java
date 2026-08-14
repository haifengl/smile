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

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Rewrites {@code Accept} for {@code POST .../chat/completions} before JAX-RS
 * resource matching.
 *
 * <p>OpenAI SDKs send {@code Accept: application/json} even for streaming
 * requests. Content negotiation runs in {@code ClassRoutingHandler} before
 * ordinary {@code ContainerRequestFilter}s, so this filter must be
 * {@code preMatching = true}.
 *
 * @author Haifeng Li
 */
public class ChatCompletionsAcceptFilter {

    /**
     * Forces {@code Accept: text/event-stream} for chat completion POSTs.
     *
     * @param requestContext the incoming request.
     */
    @ServerRequestFilter(preMatching = true)
    public void rewriteAccept(ContainerRequestContext requestContext) {
        if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !path.contains("chat/completions")) {
            return;
        }
        requestContext.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.SERVER_SENT_EVENTS);
    }
}
