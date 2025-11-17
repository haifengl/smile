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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.azure.AzureUrlPathMode;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * OpenAI service by Azure.
 *
 * @author Haifeng Li
 */
public class AzureOpenAI implements LLM {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AzureOpenAI.class);
    private final OpenAIClient client;
    private final Properties context = new Properties();

    /**
     * Constructor.
     */
    public AzureOpenAI(String baseUrl, String apiKey, AzureOpenAIServiceVersion version) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .addInterceptor(new LoggingInterceptor())
                .build();

        context.setProperty("model", "gpt-4.1-shared");
        client = OpenAIOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .azureServiceVersion(version)
                .azureUrlPathMode(AzureUrlPathMode.AUTO)
                .build();
    }

    @Override
    public Properties context() {
        return context;
    }

    @Override
    public Response request(String input) {
        var params = ChatCompletionCreateParams.builder()
                .addUserMessage(input)
                .model(context.getProperty("model"));

        String systemMessage = context.getProperty("systemMessage");
        if (systemMessage != null) {
            params.addSystemMessage(systemMessage);
        }
        var stop = context.getProperty("stop");
        if (stop != null) {
            params.stop(stop);
        }

        return new ResponseAdaptor(client.async().chat().completions().createStreaming(params.build()));
    }

    private class ResponseAdaptor implements Response, Supplier<String>,
            AsyncStreamResponse.Handler<ChatCompletionChunk> {
        final String EOS = "<eos>";
        final Deque<String> queue = new LinkedBlockingDeque<>();
        final Stream<String> output;

        ResponseAdaptor(AsyncStreamResponse<ChatCompletionChunk> response) {
            response.subscribe(this);
            output = Stream.generate(this).takeWhile(token -> token != EOS);
        }

        @Override
        public Stream<String> output() {
            return output;
        }

        @Override
        public void onNext(ChatCompletionChunk chunk) {
            if (chunk.isValid()) {
                queue.add(chunk.choices().getFirst().delta().content().orElse(""));
            }
        }

        @Override
        public void onComplete(Optional<Throwable> error) {
            error.ifPresent(t -> {
                t.printStackTrace();
                queue.add("Code generation failed: " + t.getMessage());
            });
            queue.push(EOS);
        }

        @Override
        public String get() {
            return queue.remove();
        }
    }

    /** Trace requests. */
    static class LoggingInterceptor implements Interceptor {
        @Override
        public okhttp3.Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            logger.debug("OpenAI API Request URL: {}", request.url());
            return chain.proceed(request);
        }
    }
}
