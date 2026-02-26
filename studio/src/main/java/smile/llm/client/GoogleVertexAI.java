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
public interface GoogleVertexAI {
    /**
     * Returns an instance of VertexAI deployment.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     * @param model the model name.
     * @return an instance of VertexAI deployment.
     */
    static GoogleGemini vertex(String apiKey, String baseUrl, String model) {
        var client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().baseUrl(baseUrl).build())
                .vertexAI(true)
                .build();
        return new GoogleGemini(client, model);
    }
}
