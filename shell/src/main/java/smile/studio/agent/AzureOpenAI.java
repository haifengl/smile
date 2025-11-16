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

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;
import java.util.stream.Stream;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

/**
 * OpenAI service by Azure.
 *
 * @author Haifeng Li
 */
public class AzureOpenAI extends OpenAI {
    /**
     * Constructor.
     */
    public AzureOpenAI() {

    }

    @Override
    public Response request(String input) {
        var model = context.getProperty("model", "GPT_5");
        var params = ChatCompletionCreateParams.builder()
                .addUserMessage(input)
                .model(model);

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
        final LinkedBlockingDeque<String> queue = new LinkedBlockingDeque<>();
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
                queue.push(chunk.choices().getFirst().delta().content().orElse(""));
            }
        }

        @Override
        public void onComplete(Optional<Throwable> error) {
            error.ifPresent(t -> queue.push("Code generation streaming failed: " + t.getMessage()));
            queue.push(EOS);
        }

        @Override
        public String get() {
            return queue.pop();
        }
    }
}
