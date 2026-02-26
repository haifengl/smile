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

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;

/**
 * OpenAI service by Azure.
 *
 * @author Haifeng Li
 */
public interface AzureOpenAI {
    /**
     * Returns an instance of Azure OpenAI deployment.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     * @param model the model name, aka the deployment name in Azure.
     * @return an instance of Azure OpenAI deployment.
     */
    static OpenAI unified(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        var client = OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl + "/openai")
                .credential(AzureApiKeyCredential.create(apiKey))
                .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                .azureUrlPathMode(AzureUrlPathMode.UNIFIED));
        return new OpenAI(client, model);
    }

    /**
     * Returns an instance of Azure OpenAI deployment with legacy URL path.
     * @param apiKey API key for authentication and authorization.
     * @param baseUrl the base URL for the service.
     * @param model the model name, aka the deployment name in Azure.
     * @return an instance of Azure OpenAI deployment with legacy URL path.
     */
    static OpenAI legacy(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        var client = OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl)
                .credential(AzureApiKeyCredential.create(apiKey))
                .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                .azureUrlPathMode(AzureUrlPathMode.LEGACY);
        return new OpenAI(client, model);
    }
}
