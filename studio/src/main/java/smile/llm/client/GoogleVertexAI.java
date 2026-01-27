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
package smile.llm.client;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;

/**
 * Google Vertex AI.
 *
 * @author Haifeng Li
 */
public class GoogleVertexAI extends GoogleGemini {
    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     */
    public GoogleVertexAI(String apiKey, String baseUrl) {
        super(Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().baseUrl(baseUrl).build())
                .vertexAI(true)
                .build());
    }
}
