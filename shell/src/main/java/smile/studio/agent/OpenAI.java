/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.agent;

import java.util.Properties;
import java.util.stream.Stream;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputText;

/**
 * OpenAI service.
 *
 * @author Haifeng Li
 */
public class OpenAI implements LLM {
    // Configures using the `openai.apiKey`, `openai.orgId`, `openai.projectId`,
    // `openai.webhookSecret` and `openai.baseUrl` system properties.
    // Or configures using the `OPENAI_API_KEY`, `OPENAI_ORG_ID`, `OPENAI_PROJECT_ID`,
    // `OPENAI_WEBHOOK_SECRET` and `OPENAI_BASE_URL` environment variables.
    static final OpenAIClient client = OpenAIOkHttpClient.builder().fromEnv().build();
    final Properties context = new Properties();

    /**
     * Constructor.
     */
    public OpenAI() {

    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public Response request(String input) {
        ResponseCreateParams params = ResponseCreateParams.builder()
                .input(input)
                .model(context.getProperty("model", "GPT_5"))
                .build();

        return new ResponseAdaptor(client.responses().create(params));
    }

    private record ResponseAdaptor(com.openai.models.responses.Response response) implements Response {
        @Override
        public Stream<String> output() {
            return response.output().stream()
                    .flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream())
                    .flatMap(content -> content.outputText().stream())
                    .map(ResponseOutputText::text);
        }
    }
}
