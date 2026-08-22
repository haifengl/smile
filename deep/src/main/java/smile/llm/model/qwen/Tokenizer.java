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
package smile.llm.model.qwen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.Message;
import smile.llm.tokenizer.Gpt2ByteMap;
import smile.llm.tokenizer.Tiktoken;
import smile.util.Bytes;
import smile.util.IntArrayList;

/**
 * Qwen3.5 chat tokenizer (byte-level BPE via {@link Tiktoken}).
 *
 * <p>Loads from a checkpoint directory. Prefers HuggingFace
 * {@code tokenizer.json} (includes {@code added_tokens} with the real
 * {@code <|im_start|>} / {@code <|im_end|>} ids) over {@code vocab.json} alone.
 *
 * @author Haifeng Li
 */
public class Tokenizer extends Tiktoken {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tokenizer.class);

    /** Token splitting regex (Qwen / GPT-4o style). */
    private static final Pattern REGEX = Pattern.compile(
            "(?i:'s|'t|'re|'ve|'m|'ll|'d)|[^\\r\\n\\p{L}\\p{N}]?\\p{L}+|\\p{N}| ?[^\\s\\p{L}\\p{N}]+[\\r\\n]*|\\s*[\\r\\n]+|\\s+(?!\\S)|\\s+");

    private final int[] stopTokens;

    /**
     * Constructor.
     * @param ranks token byte sequence → id map.
     */
    public Tokenizer(Map<Bytes, Integer> ranks) {
        this(ranks, "<|endoftext|>", "<|endoftext|>", defaultSpecialTokens());
    }

    /**
     * Constructor.
     * @param ranks         token → id map.
     * @param bos           beginning-of-sequence token.
     * @param eos           end-of-sequence token.
     * @param specialTokens additional special tokens.
     */
    public Tokenizer(Map<Bytes, Integer> ranks, String bos, String eos, String... specialTokens) {
        super(REGEX, ranks, bos, eos, specialTokens);
        List<Integer> stops = new ArrayList<>();
        addStop(stops, "<|endoftext|>");
        addStop(stops, "<|im_end|>");
        addStop(stops, "<|im_start|>");
        this.stopTokens = stops.stream().mapToInt(Integer::intValue).toArray();
    }

    private void addStop(List<Integer> stops, String token) {
        try {
            int id = specialToken(token);
            if (id >= 0) stops.add(id);
        } catch (Exception ignored) {
            // optional special not present in this vocab
        }
    }

    /**
     * Padding token id ({@code <|endoftext|>}).
     * @return pad token id.
     */
    public int pad() {
        return specialToken("<|endoftext|>");
    }

    /**
     * Stop token ids used during generation.
     * @return stop token id array.
     */
    public int[] stopTokens() {
        return stopTokens;
    }

    /**
     * Encodes a chat dialog in the Qwen chat-template format and leaves the
     * assistant header open for completion.
     *
     * @param dialog conversation turns.
     * @return token ids.
     */
    public int[] encodeDialog(Message... dialog) {
        IntArrayList tokens = new IntArrayList();
        for (Message message : dialog) {
            encodeMessage(message, tokens);
        }
        // Open assistant turn for the model to complete.
        tokens.add(specialToken("<|im_start|>"));
        tokens.add(encode("assistant\n", false, false));
        return tokens.toArray();
    }

    private void encodeMessage(Message message, IntArrayList tokens) {
        tokens.add(specialToken("<|im_start|>"));
        String role = switch (message.role()) {
            case system -> "system";
            case user -> "user";
            case assistant -> "assistant";
            default -> message.role().name();
        };
        tokens.add(encode(role + "\n", false, false));
        tokens.add(encode(message.content(), false, false));
        tokens.add(specialToken("<|im_end|>"));
        tokens.add(encode("\n", false, false));
    }

    /**
     * Loads a tokenizer from a HuggingFace checkpoint directory.
     *
     * <p>Order matters: {@code tokenizer.json} is preferred because it carries
     * {@code added_tokens} with the true chat special ids. Loading
     * {@code vocab.json} alone and then appending specials at {@code maxId+1}
     * remaps {@code <|im_start|>} past {@code vocab_size} and crashes embedding
     * gather on GPU.
     *
     * @param checkpointDir model directory.
     * @return tokenizer instance.
     * @throws IOException if no supported tokenizer files are found.
     */
    public static Tokenizer of(String checkpointDir) throws IOException {
        Path dir = Path.of(checkpointDir);

        Path tokenizerJson = dir.resolve("tokenizer.json");
        if (Files.exists(tokenizerJson)) {
            logger.info("Loading Qwen tokenizer from {}", tokenizerJson.getFileName());
            return new Tokenizer(loadTokenizerJson(tokenizerJson));
        }

        Path vocabJson = dir.resolve("vocab.json");
        Path mergesTxt = dir.resolve("merges.txt");
        if (Files.exists(vocabJson) && Files.exists(mergesTxt)) {
            logger.info("Loading Qwen tokenizer from vocab.json (+ added_tokens if present)");
            return new Tokenizer(loadVocabAndMerges(vocabJson, mergesTxt));
        }

        Path tiktoken = firstExisting(dir,
                "tokenizer.model", "qwen.tiktoken", "vocab.tiktoken");
        if (tiktoken != null) {
            logger.info("Loading Qwen tokenizer from {}", tiktoken.getFileName());
            return new Tokenizer(Tiktoken.load(tiktoken.toString()));
        }

        throw new IOException("No Qwen tokenizer files found under " + checkpointDir);
    }

    private static Path firstExisting(Path dir, String... names) {
        for (String name : names) {
            Path p = dir.resolve(name);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    /**
     * Loads GPT-2 style {@code vocab.json} + {@code merges.txt} into byte ranks.
     * Also merges {@code added_tokens} from a sibling {@code tokenizer.json}
     * when present so chat specials keep their HF ids.
     */
    static Map<Bytes, Integer> loadVocabAndMerges(Path vocabJson, Path mergesTxt) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode vocab = mapper.readTree(vocabJson.toFile());
        Map<Bytes, Integer> ranks = new HashMap<>();
        vocab.properties().forEach(e -> {
            ranks.put(Gpt2ByteMap.vocabTokenToBytes(e.getKey()), e.getValue().asInt());
        });
        if (!Files.exists(mergesTxt)) {
            throw new IOException("merges.txt missing next to vocab.json");
        }
        Path tokenizerJson = vocabJson.getParent().resolve("tokenizer.json");
        if (Files.exists(tokenizerJson)) {
            mergeAddedTokens(ranks, mapper.readTree(tokenizerJson.toFile()));
        }
        return ranks;
    }

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
            // HF vocab keys are GPT-2 unicode-mapped; Tiktoken BPE needs raw bytes.
            ranks.put(Gpt2ByteMap.vocabTokenToBytes(e.getKey()), e.getValue().asInt());
        });
        mergeAddedTokens(ranks, root);
        return ranks;
    }

    /** Merges {@code added_tokens} entries into {@code ranks} (content → id). */
    static void mergeAddedTokens(Map<Bytes, Integer> ranks, JsonNode root) {
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
            // Chat specials are literal strings; ASCII maps 1:1 through GPT-2.
            ranks.put(Gpt2ByteMap.vocabTokenToBytes(content), id);
            ranks.put(new Bytes(content), id);
        }
    }

    /**
     * Ensures chat control tokens lie in {@code [0, vocabSize)}.
     * Call after construction against the model config vocab size.
     *
     * @param vocabSize embedding / lm_head vocabulary size from model config.
     */
    public void requireChatSpecialsInVocab(int vocabSize) {
        for (String name : List.of("<|endoftext|>", "<|im_start|>", "<|im_end|>")) {
            Integer id = specialToken(name);
            if (id == null || id < 0 || id >= vocabSize) {
                throw new IllegalStateException(
                        "Chat special '" + name + "' id=" + id
                                + " is outside model vocab_size=" + vocabSize
                                + ". Load tokenizer.json (with added_tokens), not a bare vocab "
                                + "that remaps specials past the embedding table.");
            }
        }
        logger.info("Chat specials OK: pad/eos={}, im_start={}, im_end={}, vocab_size={}",
                specialToken("<|endoftext|>"),
                specialToken("<|im_start|>"),
                specialToken("<|im_end|>"),
                vocabSize);
    }

    private static String[] defaultSpecialTokens() {
        return new String[] {
                "<|endoftext|>",
                "<|im_start|>",
                "<|im_end|>",
                "<|object_ref_start|>",
                "<|object_ref_end|>",
                "<|box_start|>",
                "<|box_end|>",
                "<|quad_start|>",
                "<|quad_end|>",
                "<|vision_start|>",
                "<|vision_end|>",
                "<|vision_pad|>",
                "<|image_pad|>",
                "<|video_pad|>",
                "<think>",
                "</think>"
        };
    }
}
