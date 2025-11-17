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
import com.openai.azure.AzureOpenAIServiceVersion;
import smile.studio.SmileStudio;

/**
 * LLM inference interface.
 *
 * @author Haifeng Li
 */
public interface LLM {
    /**
     * Sends a request to LLM service.
     * @param input the input message.
     * @return a response object.
     */
    Response request(String input);

    /**
     * Returns the associated context object.
     * @return the associated context object.
     */
    Properties context();

    /**
     * Returns an LLM instance specified by app settings.
     * @return an LLM instance specified by app settings.
     */
    static LLM getCoder() {
        var prefs = SmileStudio.preferences();
        var service = prefs.get("aiService", "OpenAI");

        LLM llm = switch (service) {
            case "OpenAI" -> new OpenAI();
            case "Azure OpenAI" -> new AzureOpenAI(
                        prefs.get("azureOpenAIBaseUrl", ""),
                        prefs.get("azureOpenAIApiKey", ""),
                        AzureOpenAIServiceVersion.latestPreviewVersion());

            default -> {
                System.out.println("Unknown AI service: " + service);
                yield new OpenAI();
            }
        };

        llm.context().setProperty("systemMessage", Prompt.smileSystem());
        llm.context().setProperty("model", "gpt-5.1-codex");
        return llm;
    }
}
