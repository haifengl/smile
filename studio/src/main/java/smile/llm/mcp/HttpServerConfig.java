/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.mcp;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpTransport;

/**
 * Configuration for an MCP server that uses the {@code sse} or {@code http}
 * transport. The MCP client connects to a remote HTTP endpoint.
 * <ul>
 *   <li>{@code sse} – uses Server-Sent Events for server-to-client streaming
 *       (legacy transport, MCP spec prior to 2025-03-26).</li>
 *   <li>{@code http} – streamable HTTP transport introduced in the
 *       2025-03-26 MCP specification revision.</li>
 * </ul>
 *
 * <p>Example JSON fragment:
 * <pre>{@code
 * "github": {
 *   "type": "http",
 *   "url": "https://api.githubcopilot.com/mcp/",
 *   "headers": {
 *     "Authorization": "Bearer ${input:github-token}"
 *   }
 * }
 * }</pre>
 *
 * @param type     The transport type; {@link ServerType#SSE} or
 *                 {@link ServerType#HTTP} for this record.
 * @param url      The URL of the remote MCP server endpoint.
 * @param headers  Optional HTTP headers sent with every request.
 *                 Values may reference input variables as {@code ${input:id}}.
 * @param inputs   Optional list of input variable definitions used to resolve
 *                 {@code ${input:id}} placeholders in {@code headers} values.
 * @param disabled If {@code true}, this server is disabled and will not be
 *                 connected.
 *
 * @author Haifeng Li
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HttpServerConfig(
        ServerType type,
        String url,
        Map<String, String> headers,
        List<McpInput> inputs,
        boolean disabled) implements ServerConfig, Consumer<HttpRequest.Builder> {

    @Override
    public McpTransport transport() {
        return HttpClientStreamableHttpTransport
                .builder(url)
                .endpoint("") // override the default endpoint /mcp
                .customizeRequest(this)
                .build();
    }

    @Override
    public void accept(HttpRequest.Builder builder) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
    }
}
