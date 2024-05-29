/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LLaMA model specification.
 *
 * @author Haifeng Li
 */
public class Llama {
    /** The transformer model. */
    final Transformer model;
    /** The tokenizer. */
    final Tokenizer tokenizer;

    /**
     * Constructor.
     * @param model the transformer model.
     */
    public Llama(Transformer model, Tokenizer tokenizer) {
        this.model = model;
        this.tokenizer = tokenizer;
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     * @param checkpoint the directory path of checkpoint files.
     * @param tokenizer the path of tokenizer file.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param maxBatchSize the maximum batch size for inference.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpoint, String tokenizer, int maxBatchSize, int maxSeqLen) throws IOException {
        return build(checkpoint, tokenizer, maxBatchSize, maxSeqLen, 1);
    }

    /**
     * Builds a Llama instance by initializing and loading a model checkpoint.
     * @param checkpointDir the directory path of checkpoint files.
     * @param tokenizerPath the path of tokenizer file.
     * @param maxSeqLen the maximum sequence length for input text.
     * @param maxBatchSize the maximum batch size for inference.
     * @param modelParallelSize the number of model parallel processes.
     * @return an instance of Llama model.
     */
    public static Llama build(String checkpointDir, String tokenizerPath, int maxBatchSize, int maxSeqLen, int modelParallelSize) throws IOException {
        File dir = new File(checkpointDir);
        List<String> checkpoints = new ArrayList<>();
        for (var file : dir.listFiles()) {
            var path = file.getPath();
            if (path.endsWith(".pth")) {
                checkpoints.add(path);
            }
        }

        if (checkpoints.isEmpty()) {
            throw new IllegalArgumentException("No checkpoint files found in " + checkpointDir);
        }

        if (checkpoints.size() != modelParallelSize) {
            throw new IllegalStateException(String.format("Loading a checkpoint for MP=%d but world size is %d", checkpoints.size(), modelParallelSize));
        }

        var modelArgs = ModelArgs.from(checkpointDir + "/params.json", maxBatchSize, maxSeqLen);

        var tokenizer = Tokenizer.of(tokenizerPath);
        if (tokenizer.size() != modelArgs.vocabSize()) {
            throw new IllegalStateException("Tokenizer and ModelArgs have different vocabulary size.");
        }

        var model = new Transformer(modelArgs);

        Collections.sort(checkpoints);
        String rank = System.getenv("RANK");
        var checkpoint = checkpoints.get(Integer.valueOf(rank == null ? "0" : rank));
        model.load(checkpoint);
        return new Llama(model, tokenizer);
    }
}
