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

import java.util.concurrent.CompletableFuture;
import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

/**
 * OpenAI service by Azure.
 *
 * @author Haifeng Li
 */
public class AzureOpenAI extends OpenAI {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AzureOpenAI.class);
    private final String model;

    /**
     * Constructor.
     */
    public AzureOpenAI(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        super(OpenAI.singleton.withOptions(builder -> {
            builder.baseUrl(baseUrl)
                   .credential(AzureApiKeyCredential.create(apiKey))
                   .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                   .azureUrlPathMode(AzureUrlPathMode.AUTO);
        }));
        this.model = model;
    }

    @Override
    public CompletableFuture<Response> request(String input) {
        var params = ResponseCreateParams.builder()
                .model(model)
                .instructions(context.getProperty("instructions"))
                .input(input)
                .build();

        return client.responses().create(params);
    }

    @Override
    public String agent() {
        return model;
    }

    @Override
    public String coder() {
        return model;
    }
}
