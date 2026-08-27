/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tokenizer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.util.Bytes;

/**
 * Loads HuggingFace {@code tokenizer.json} BPE vocab into Tiktoken-style byte ranks.
 *
 * @author Haifeng Li
 */
public final class HuggingFaceBpeVocab {
    private HuggingFaceBpeVocab() {}

    /**
     * Extracts vocab from HuggingFace {@code tokenizer.json}
     * ({@code model.vocab} plus {@code added_tokens}).
     *
     * @param tokenizerJson path to {@code tokenizer.json}.
     * @return token byte sequence → id map (GPT-2 keys converted to raw bytes).
     * @throws IOException if the file is missing or not a valid tokenizer.json.
     */
    public static Map<Bytes, Integer> loadTokenizerJson(Path tokenizerJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(tokenizerJson.toFile());
        JsonNode vocab = root.path("model").path("vocab");
        if (!vocab.isObject()) {
            throw new IOException("tokenizer.json missing model.vocab: " + tokenizerJson);
        }
        Map<Bytes, Integer> ranks = new HashMap<>();
        vocab.properties().forEach(e -> {
            ranks.put(Gpt2ByteMap.vocabTokenToBytes(e.getKey()), e.getValue().asInt());
        });
        mergeAddedTokens(ranks, root);
        return ranks;
    }

    /** Merges {@code added_tokens} entries into {@code ranks} (content → id). */
    public static void mergeAddedTokens(Map<Bytes, Integer> ranks, JsonNode root) {
        JsonNode added = root.get("added_tokens");
        if (added == null || !added.isArray()) {
            return;
        }
        for (JsonNode token : added) {
            if (!token.has("content") || !token.has("id")) {
                continue;
            }
            String content = token.get("content").asString();
            int id = token.get("id").asInt();
            ranks.put(Gpt2ByteMap.vocabTokenToBytes(content), id);
            ranks.put(new Bytes(content), id);
        }
    }
}
