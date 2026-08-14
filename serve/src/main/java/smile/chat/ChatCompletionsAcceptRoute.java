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

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.core.http.HttpMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

/**
 * HTTP-level rewrite of {@code Accept} for chat completions.
 *
 * <p>OpenAI clients send {@code Accept: application/json} for streaming
 * requests. JAX-RS content negotiation happens before ordinary request
 * filters, so this Vert.x filter runs on the Quarkus HTTP pipeline and
 * forces {@code text/event-stream} before RESTEasy matching.
 *
 * @author Haifeng Li
 * @see ChatCompletionsAcceptFilter
 */
@ApplicationScoped
public class ChatCompletionsAcceptRoute {

    /**
     * Registers an early Vert.x filter that rewrites Accept for chat POSTs.
     *
     * @param filters Quarkus HTTP filter registration (higher priority runs first).
     */
    void register(@Observes Filters filters) {
        filters.register(rc -> {
            var req = rc.request();
            if (req.method() == HttpMethod.POST) {
                String path = req.path();
                if (path != null && path.contains("/chat/completions")) {
                    req.headers().set(HttpHeaders.ACCEPT, MediaType.SERVER_SENT_EVENTS);
                }
            }
            rc.next();
        }, 1000);
    }
}
