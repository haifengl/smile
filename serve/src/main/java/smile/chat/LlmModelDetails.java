/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.llm.LanguageModel;
import smile.llm.model.llama.Llama;
import smile.llm.model.llama.LlamaModelArgs;
import smile.llm.model.qwen.Qwen;
import smile.llm.model.qwen.QwenModelArgs;

/**
 * Chat LLM details returned by {@code GET /models/{id}}.
 *
 * <p>Values come from the loaded checkpoint ({@code config.json} /
 * {@code params.json}), not from Hugging Face model-card APIs.
 *
 * @param family           architecture family label from {@link LanguageModel#family()}.
 * @param source           {@code "huggingface"} or {@code "local"}.
 * @param dim              token embedding dimension.
 * @param numLayers        transformer block count.
 * @param numHeads         attention head count.
 * @param numKvHeads       key/value head count (GQA), or {@code null}.
 * @param vocabSize        vocabulary size.
 * @param intermediateSize explicit FFN hidden size when set, else {@code null}.
 * @param maxBatchSize     configured max batch size.
 * @param maxSeqLen        configured max sequence length.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmModelDetails(
        String family,
        String source,
        Integer dim,
        Integer numLayers,
        Integer numHeads,
        Integer numKvHeads,
        Integer vocabSize,
        Integer intermediateSize,
        Integer maxBatchSize,
        Integer maxSeqLen) {

    /**
     * Builds details from a loaded language model.
     *
     * @param model  the loaded model.
     * @param source {@code "huggingface"} or {@code "local"}.
     * @return llm details for retrieve responses.
     */
    public static LlmModelDetails of(LanguageModel model, String source) {
        if (model instanceof Llama llama) {
            return of(llama, source);
        }
        if (model instanceof Qwen qwen) {
            return of(qwen, source);
        }
        throw new IllegalArgumentException("Unsupported model type: " + model.getClass().getName());
    }

    /**
     * Builds details from a loaded {@link Llama} instance.
     */
    public static LlmModelDetails of(Llama llama, String source) {
        LlamaModelArgs args = llama.params();
        return new LlmModelDetails(
                llama.family(),
                source,
                args.dim(),
                args.numLayers(),
                args.numHeads(),
                args.numKvHeads(),
                args.vocabSize(),
                args.intermediateSize(),
                args.maxBatchSize(),
                args.maxSeqLen());
    }

    /**
     * Builds details from a loaded {@link Qwen} instance.
     */
    public static LlmModelDetails of(Qwen qwen, String source) {
        QwenModelArgs args = qwen.params();
        return new LlmModelDetails(
                qwen.family(),
                source,
                args.dim(),
                args.numLayers(),
                args.numHeads(),
                args.numKvHeads(),
                args.vocabSize(),
                args.intermediateSize(),
                args.maxBatchSize(),
                args.maxSeqLen());
    }
}
