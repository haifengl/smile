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

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

/**
 * Rewrites {@code Accept} for {@code POST /chat/completions} so OpenAI SDKs
 * (which send {@code Accept: application/json} even when streaming) negotiate
 * successfully against the SSE endpoint.
 *
 * @author Haifeng Li
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ChatCompletionsAcceptFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!"POST".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        String path = requestContext.getUriInfo().getPath();
        if (path == null || !path.endsWith("chat/completions")) {
            return;
        }
        // Force SSE acceptance; the resource always streams token chunks.
        requestContext.getHeaders().putSingle(HttpHeaders.ACCEPT, MediaType.SERVER_SENT_EVENTS);
    }
}
