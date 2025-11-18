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

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.azure.credential.AzureApiKeyCredential;

/**
 * OpenAI service by Azure.
 *
 * @author Haifeng Li
 */
public class AzureOpenAI extends OpenAI {
    /** The deployment name serves as the model name in requests. */
    private final String model;

    /**
     * Constructor.
     * @param apiKey API key for authentication and authorization.
     * @param model the model name, aka the deployment name in Azure.
     */
    public AzureOpenAI(String apiKey, String baseUrl, String model) {
        // The new client will reuse connection and thread pool
        super(OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl + "/openai")
                    .credential(AzureApiKeyCredential.create(apiKey))
                    .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                    .azureUrlPathMode(AzureUrlPathMode.UNIFIED)),
              OpenAI.singleton.withOptions(builder -> builder.baseUrl(baseUrl)
                    .credential(AzureApiKeyCredential.create(apiKey))
                    .azureServiceVersion(AzureOpenAIServiceVersion.fromString("2025-04-01-preview"))
                    .azureUrlPathMode(AzureUrlPathMode.LEGACY)));
        this.model = model;
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
