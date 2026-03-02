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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP (Model Context Protocol) tools.
 *
 * @author Haifeng Li
 */
public class MCP {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MCP.class);
    /** Shared Jackson mapper with camelCase property names. */
    static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();
    /** Type reference for deserializing JSON arguments into a map. */
    private static final TypeReference<HashMap<String, Object>> MAP_TYPE_REF = new TypeReference<>() {
    };

    private static final List<McpClient> clients = new ArrayList<>();
    private static final List<McpToolSpec> specs = new ArrayList<>();
    private static final Map<String, McpClient> tools = new HashMap<>();

    /**
     * Connects to MCP servers declared in the configuration file
     * and initializes clients and tools.
     * @param path the path to the MCP configuration file.
     * @throws IOException if an I/O error occurs when reading the configuration file
     *                     or connecting to servers.
     */
    public static void connect(Path path) throws IOException {
        McpConfig config = McpConfig.from(path);
        var clients = config.connect();
        MCP.clients.addAll(clients);
        specs.addAll(clients.stream().flatMap(client -> client.tools().stream()).toList());
        clients.forEach(client ->
            client.tools().forEach(tool -> tools.put(tool.name(), client))
        );
    }

    /**
     * Calls a tool.
     * @param tool the name of the tool to call.
     * @param arguments the arguments in serialized JSON.
     */
    public static String call(String tool, String arguments) {
        var client = tools.get(tool);
        if (client == null) return "Error: unknown tool " + tool;
        return client.call(tool, MAPPER.readValue(arguments, MAP_TYPE_REF));
    }

    /** Returns the available clients. */
    public static List<McpClient> clients() {
        return clients;
    }

    /** Returns the available tool specs. */
    public static List<McpToolSpec> specs() {
        return specs;
    }

    /** Closes all clients and releases resources. */
    public static void close() {
        for (var client : clients) {
            client.close();
        }
    }
}
