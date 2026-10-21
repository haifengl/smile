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
    /** Behaviours of the checkpoint's chat template, or {@code null} to build the prompt the legacy way. */
    private volatile smile.llm.template.Qwen3ChatTemplate.Variant chatVariant;
    /** Added tokens that stay one token inside text (longest first), as in the reference tokenizer. */
    private volatile String[] atomicTokens = new String[0];

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
        registerAtomicTokens(List.of(defaultSpecialTokens()));
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
     * Declares the chat template of the checkpoint, so {@link #encodeDialog} renders prompts like it.
     *
     * @param variant the template's behaviours ({@link smile.llm.template.Qwen3ChatTemplate.Variant#detect}), or
     *                {@code null} for the legacy hand-built prompt.
     */
    public void setChatTemplate(smile.llm.template.Qwen3ChatTemplate.Variant variant) {
        this.chatVariant = variant;
    }

    /**
     * Returns the declared chat template behaviours.
     *
     * @return the variant, or {@code null} when none was declared.
     */
    public smile.llm.template.Qwen3ChatTemplate.Variant chatTemplate() {
        return chatVariant;
    }

    /**
     * Registers added tokens (control tokens plus {@code <tool_call>}-style markers) that must stay a single token when
     * they occur in text, as the reference tokenizer does. Tokens missing from the vocabulary are ignored.
     *
     * @param contents the token strings.
     */
    public void registerAtomicTokens(java.util.Collection<String> contents) {
        java.util.TreeSet<String> present = new java.util.TreeSet<>(
                java.util.Comparator.comparingInt(String::length).reversed().thenComparing(java.util.Comparator.naturalOrder()));
        for (String c : contents) {
            if (tokenId(c) >= 0) {
                present.add(c);
            }
        }
        this.atomicTokens = present.toArray(String[]::new);
    }

    private int tokenId(String content) {
        Integer special = specialTokens.get(content);
        if (special != null) {
            return special;
        }
        Integer rank = ranks.get(new Bytes(content));
        return rank == null ? -1 : rank;
    }

    /**
     * Encodes rendered chat text in which control tokens ({@code <|im_start|>}, {@code <think>}, {@code <tool_call>},
     * the vision placeholders, ...) stand for single tokens; everything between them is ordinary text.
     *
     * @param text a rendered prompt.
     * @return token ids.
     */
    public int[] encodeWithSpecials(String text) {
        String[] atomic = atomicTokens;
        IntArrayList out = new IntArrayList();
        int pos = 0;
        while (pos < text.length()) {
            int at = -1;
            String hit = null;
            for (String token : atomic) {
                int i = text.indexOf(token, pos);
                if (i >= 0 && (at < 0 || i < at)) {
                    at = i;
                    hit = token;
                }
            }
            if (hit == null) {
                out.add(encode(text.substring(pos), false, false));
                break;
            }
            if (at > pos) {
                out.add(encode(text.substring(pos, at), false, false));
            }
            int id = tokenId(hit);
            out.add(id);
            pos = at + hit.length();
        }
        return out.toArray();
    }

    /**
     * Renders a dialog with the checkpoint's chat template.
     *
     * @param dialog  the messages.
     * @param options chat options (tools, thinking mode, reasoning effort); may be {@code null}.
     * @return the prompt text with the assistant turn open, or {@code null} when no template was declared.
     */
    public String renderDialog(Message[] dialog, smile.llm.ChatOptions options) {
        var variant = chatVariant;
        if (variant == null) {
            return null;
        }
        return new smile.llm.template.Qwen3ChatTemplate(variant, true)
                .encode(new ArrayList<>(List.of(dialog)), options);
    }

    /**
     * Encodes a chat dialog in the Qwen chat-template format and leaves the
     * assistant header open for completion.
     *
     * @param dialog conversation turns.
     * @return token ids.
     */
    public int[] encodeDialog(Message... dialog) {
        return encodeDialog(dialog, null);
    }

    /**
     * Encodes a chat dialog with optional tool-calling options.
     *
     * @param dialog  conversation turns.
     * @param options chat options; when tools are present (or the dialog
     *                already contains tool turns), uses {@link smile.llm.template.Qwen3ChatTemplate}.
     * @return token ids.
     */
    public int[] encodeDialog(Message[] dialog, smile.llm.ChatOptions options) {
        String rendered = dialog == null || dialog.length == 0 ? null : renderDialog(dialog, options);
        if (rendered != null) {
            return encodeWithSpecials(rendered);
        }
        if (needsToolTemplate(dialog, options)) {
            String prompt = smile.llm.template.Qwen3ChatTemplate.encodeDialog(dialog, options);
            return encode(prompt, false, false);
        }
        IntArrayList tokens = new IntArrayList();
        if (dialog != null) {
            for (Message message : dialog) {
                encodeMessage(message, tokens);
            }
        }
        // Open assistant turn for the model to complete.
        tokens.add(specialToken("<|im_start|>"));
        tokens.add(encode("assistant\n", false, false));
        return tokens.toArray();
    }

    private static boolean needsToolTemplate(Message[] dialog, smile.llm.ChatOptions options) {
        if (options != null && options.hasTools()) {
            return true;
        }
        if (dialog == null) {
            return false;
        }
        for (Message m : dialog) {
            if (m == null) {
                continue;
            }
            if (m.role() == smile.llm.Role.tool || m.hasToolCalls()) {
                return true;
            }
        }
        return false;
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
            return configure(new Tokenizer(loadTokenizerJson(tokenizerJson)), dir);
        }

        Path vocabJson = dir.resolve("vocab.json");
        Path mergesTxt = dir.resolve("merges.txt");
        if (Files.exists(vocabJson) && Files.exists(mergesTxt)) {
            logger.info("Loading Qwen tokenizer from vocab.json (+ added_tokens if present)");
            return configure(new Tokenizer(loadVocabAndMerges(vocabJson, mergesTxt)), dir);
        }

        Path tiktoken = firstExisting(dir,
                "tokenizer.model", "qwen.tiktoken", "vocab.tiktoken");
        if (tiktoken != null) {
            logger.info("Loading Qwen tokenizer from {}", tiktoken.getFileName());
            return new Tokenizer(Tiktoken.load(tiktoken.toString()));
        }

        throw new IOException("No Qwen tokenizer files found under " + checkpointDir);
    }

    /**
     * Reads the checkpoint's chat template ({@code chat_template.jinja}, or {@code chat_template} in
     * {@code tokenizer_config.json}) to learn its behaviours, and its added tokens to keep them atomic in text.
     */
    private static Tokenizer configure(Tokenizer tokenizer, Path dir) {
        try {
            String jinja = null;
            Path file = dir.resolve("chat_template.jinja");
            if (Files.exists(file)) {
                jinja = Files.readString(file);
            } else if (Files.exists(dir.resolve("tokenizer_config.json"))) {
                JsonNode template = new ObjectMapper().readTree(dir.resolve("tokenizer_config.json").toFile())
                        .get("chat_template");
                if (template != null && template.isString()) {
                    jinja = template.asString();
                }
            }
            if (jinja != null) {
                tokenizer.setChatTemplate(smile.llm.template.Qwen3ChatTemplate.Variant.detect(jinja));
                logger.info("Qwen chat template: {}", tokenizer.chatTemplate());
            }
            Path json = dir.resolve("tokenizer.json");
            if (Files.exists(json)) {
                List<String> added = new ArrayList<>(List.of(defaultSpecialTokens()));
                for (JsonNode t : new ObjectMapper().readTree(json.toFile()).path("added_tokens")) {
                    added.add(t.path("content").asString());
                }
                tokenizer.registerAtomicTokens(added);
            }
        } catch (IOException e) {
            logger.warn("Could not read the chat template or added tokens in {}: {}", dir, e.toString());
        }
        return tokenizer;
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
        return smile.llm.tokenizer.HuggingFaceBpeVocab.loadTokenizerJson(tokenizerJson);
    }

    /** Merges {@code added_tokens} entries into {@code ranks} (content → id). */
    static void mergeAddedTokens(Map<Bytes, Integer> ranks, JsonNode root) {
        smile.llm.tokenizer.HuggingFaceBpeVocab.mergeAddedTokens(ranks, root);
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
