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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import smile.util.Bytes;
import smile.util.IntArrayList;
import smile.util.IntPair;

/**
 * tiktoken is a fast BPE tokenizer by OpenAI.
 *
 * @author Haifeng Li
 */
public class Tiktoken implements Tokenizer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tiktoken.class);
    private static final int MAX = Integer.MAX_VALUE;

    /** The regex pattern to split the input text into tokens. */
    private final Pattern pattern;
    /** The regex pattern to detect special tokens. */
    private final Pattern specialTokenPattern;
    /** Token -> Rank */
    protected final Map<Bytes, Integer> ranks;
    /** Special Token -> Rank */
    protected final Map<String, Integer> specialTokens;
    /** Special-token ids for {@link #tryDecode(int[], boolean)} skip filtering. */
    private final Set<Integer> specialTokenIds;
    /**
     * Specials that remain in streamed text even when {@code skipSpecial} is
     * true (e.g. model thinking markers the UI needs to style).
     */
    private final Set<Integer> visibleSpecialTokenIds;
    /** ID -> Token */
    private final Bytes[] decoder;
    /** BOS (beginning of sequence) token id. */
    private final int bos;
    /** EOS (end of sequence) token id. */
    private final int eos;
    /**
     * If false, special tokens will be encoded as natural text.
     * Otherwise, they will be encoded as special tokens.
     */
    private boolean allowSpecialTokens = false;
    /**
     * The default Charset.decode() method doesn't throw exceptions.
     * Constructs a new decoder for tryDecode method.
     */
    private final CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();

    /**
     * Constructor.
     * @param pattern The regex pattern to split the input text into tokens.
     * @param ranks The token to rank map.
     * @param bos The beginning of sequence token.
     * @param eos The end of sequence token.
     * @param specialTokens Optional special tokens.
     */
    public Tiktoken(Pattern pattern, Map<Bytes, Integer> ranks, String bos, String eos, String... specialTokens) {
        this.pattern = pattern;
        this.ranks = ranks;

        int maxId = -1;
        for (int id : ranks.values()) {
            if (id > maxId) {
                maxId = id;
            }
        }

        // Resolve specials against the vocab first. HF tokenizers (e.g. Qwen)
        // already place <|im_start|> etc. inside model.vocab / added_tokens;
        // remapping them to maxId+1 produces embedding gather OOB.
        // Llama / OpenAI tiktoken style leave specials out of the BPE ranks and
        // assign them contiguously after maxRankId (e.g. 128000+) — that is
        // expected, not a warning.
        int nextId = maxId + 1;
        int[] specialIds = new int[specialTokens.length];
        Bytes[] specialBytes = new Bytes[specialTokens.length];
        int appended = 0;
        for (int i = 0; i < specialTokens.length; i++) {
            specialBytes[i] = new Bytes(specialTokens[i]);
            Integer existing = findRankId(ranks, specialTokens[i]);
            if (existing != null) {
                specialIds[i] = existing;
            } else {
                specialIds[i] = nextId++;
                appended++;
                logger.debug("Special token '{}' not in BPE ranks; assigning id {} (after maxRankId={})",
                        specialTokens[i], specialIds[i], maxId);
            }
        }
        if (appended > 0) {
            logger.info("Registered {} special token(s) after BPE ranks (ids {}..{}); "
                            + "{} special(s) reused vocab ids",
                    appended, maxId + 1, nextId - 1, specialTokens.length - appended);
        }

        this.decoder = new Bytes[Math.max(maxId + 1, nextId)];
        for (var entry : ranks.entrySet()) {
            this.decoder[entry.getValue()] = entry.getKey();
        }

        this.specialTokenPattern = specialTokenRegex(specialTokens);
        this.specialTokens = new HashMap<>();
        for (int i = 0; i < specialTokens.length; i++) {
            this.specialTokens.put(specialTokens[i], specialIds[i]);
            this.decoder[specialIds[i]] = specialBytes[i];
        }
        this.specialTokenIds = Set.copyOf(this.specialTokens.values());
        this.visibleSpecialTokenIds = visibleSpecialTokenIds(this.specialTokens);

        this.bos = Optional.ofNullable(this.specialTokens.get(bos))
                .orElseThrow(() -> new IllegalArgumentException("BOS token not found in specialTokens: " + bos));
        this.eos = Optional.ofNullable(this.specialTokens.get(eos))
                .orElseThrow(() -> new IllegalArgumentException("EOS token not found in specialTokens: " + eos));
        logger.info("#words: {} | BOS ID: {} | EOS ID: {}", decoder.length, this.bos, this.eos);
    }

    /**
     * Specials the UI relies on (thinking span markers). Built without
     * embedding the literal tag spelling in one piece so tooling that
     * strips HTML-like tokens cannot corrupt the constants.
     */
    private static Set<Integer> visibleSpecialTokenIds(Map<String, Integer> specials) {
        String open = "<" + "think>";
        String close = "</" + "think>";
        Set<Integer> visible = new HashSet<>();
        Integer openId = specials.get(open);
        Integer closeId = specials.get(close);
        if (openId != null) {
            visible.add(openId);
        }
        if (closeId != null) {
            visible.add(closeId);
        }
        return Set.copyOf(visible);
    }

    /**
     * Looks up a special-token string in {@code ranks}, trying UTF-8 bytes first
     * and then a string equality scan (HF vocab keys are sometimes stored with
     * a different {@link Bytes} representation than {@code new Bytes(token)}).
     */
    private static Integer findRankId(Map<Bytes, Integer> ranks, String token) {
        Integer id = ranks.get(new Bytes(token));
        if (id != null) {
            return id;
        }
        for (var entry : ranks.entrySet()) {
            if (token.equals(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Returns the vocabulary size.
     * @return the vocabulary size.
     */
    public int size() {
        return decoder.length;
    }

    /**
     * Returns the regex for special tokens.
     * @param tokens special tokens.
     * @return the pattern regex.
     */
    private Pattern specialTokenRegex(String... tokens) {
        var inner = Arrays.stream(tokens).map(Pattern::quote)
                .collect(Collectors.joining("|", "(", ")"));
        // Employ lookahead and lookbehind to keep delimiter when calling
        // split(). Lookahead and lookbehind equal to select an empty
        // character before or after delimiter.
        var regex = String.format("((?<=%s)|(?=%s))", inner, inner);
        return Pattern.compile(regex);
    }

    /**
     * Sets how special tokens will be encoded.
     * @param allowSpecialTokens If false, special tokens will be encoded as
     *                          natural text. Otherwise, they will be encoded
     *                          as special tokens.
     */
    public void allowSpecialTokens(boolean allowSpecialTokens) {
        this.allowSpecialTokens = allowSpecialTokens;
    }

    /**
     * Returns how special tokens will be encoded.
     * @return false if special tokens will be encoded as natural text;
     *         true if they will be encoded as special tokens.
     */
    public boolean isSpecialTokenAllowed() {
        return allowSpecialTokens;
    }

    /**
     * Returns the special token id.
     * @param token a special token.
     * @return the special token id, or {@code null} if the token is not registered.
     */
    public Integer specialToken(String token) {
        return specialTokens.get(token);
    }

    @Override
    public int[] encode(String text) {
        return encode(text, false, false);
    }

    @Override
    public int[] encode(String text, boolean bos, boolean eos) {
        String[] tokens = tokenize(text);
        IntArrayList output = new IntArrayList(2 * tokens.length);
        ArrayList<IntPair> parts = new ArrayList<>(text.length());

        if (bos) {
            output.add(this.bos);
        }

        for (var token : tokens) {
            var rank = specialTokens.get(token);
            if (rank != null && allowSpecialTokens) {
                output.add(rank);
            } else {
                var piece = new Bytes(token);
                rank = ranks.get(piece);
                if (rank != null) {
                    output.add(rank);
                } else {
                    bytePairEncode(piece, parts, output);
                }
            }
        }

        if (eos) {
            output.add(this.eos);
        }

        return output.toArray();
    }

    /**
     * Byte pair encoding.
     * @param piece the piece of text.
     * @param parts the vector of (start, rank).
     * @param output the output buffer.
     */
    private void bytePairEncode(Bytes piece, ArrayList<IntPair> parts, IntArrayList output) {
        int length = piece.length();
        if (length == 0) {
            return;
        }
        if (length == 1) {
            int token = getRank(piece, 0, 1);
            if (token == MAX) {
                throw new IllegalArgumentException(
                        "Byte not in vocabulary (id lookup failed for a single-byte piece). "
                                + "HF vocabs must be loaded via GPT-2 byte mapping.");
            }
            output.add(token);
            return;
        }

        bytePairMerge(piece, parts);
        for (int i = 0; i < parts.size() - 1; i++) {
            int token = getRank(piece, parts.get(i)._1(), parts.get(i + 1)._1());
            if (token == MAX) {
                throw new IllegalArgumentException(
                        "BPE produced an unknown token (rank=MAX). Check that the tokenizer "
                                + "vocab was converted from HuggingFace GPT-2 unicode keys to raw bytes.");
            }
            output.add(token);
        }
    }

    /** Byte pair merge. */
    private void bytePairMerge(Bytes piece, ArrayList<IntPair> parts) {
        int length = piece.length();
        if (length <= 1) {
            throw new IllegalArgumentException("bytePairMerge requires length > 1");
        }
        parts.clear();
        parts.ensureCapacity(length + 1);

        int minRank = MAX;
        int minRankIndex = MAX;
        for (int i = 0; i < length - 1; i++) {
            int rank = getRank(piece, i, i + 2);
            if (rank < minRank) {
                minRank = rank;
                minRankIndex = i;
            }
            parts.add(new IntPair(i, rank));
        }
        parts.add(new IntPair(length - 1, MAX));
        parts.add(new IntPair(length, MAX));

        while (minRank != MAX) {
            int i = minRankIndex;
            if (i > 0) {
                setRank(piece, parts, i - 1);
            }
            setRank(piece, parts, i);
            parts.remove(i + 1);

            minRank = MAX;
            minRankIndex = MAX;
            for (i = 0; i < parts.size() - 1; i++) {
                int rank = parts.get(i)._2();
                if (rank < minRank) {
                    minRank = parts.get(i)._2();
                    minRankIndex = i;
                }
            }
        }
    }

    /** Returns the rank of piece[parts[i].i, parts[i+3].i]. */
    private int getRank(Bytes piece, ArrayList<IntPair> parts, int i) {
        if (i + 3 < parts.size()) {
            return getRank(piece, parts.get(i)._1(), parts.get(i+3)._1());
        }
        return MAX;
    }

    /** Sets parts[i].rank. */
    private void setRank(Bytes piece, ArrayList<IntPair> parts, int i) {
        var part = parts.get(i);
        parts.set(i, new IntPair(part._1(), getRank(piece, parts, i)));
    }

    /**
     * Returns the rank of a part of piece.
     * @param piece the piece of text.
     * @param start the initial index of the range to be included, inclusive
     * @param end the final index of the range to be included, exclusive.
     * @return the token id if the part of piece is in the vocabulary or MAX_RANK.
     */
    private int getRank(Bytes piece, int start, int end) {
        return ranks.getOrDefault(piece.slice(start, end), MAX);
    }

    @Override
    public String decode(int[] tokens) {
        int totalBytes = 0;
        for (var token : tokens) totalBytes += decoder[token].length();
        byte[] buffer = new byte[totalBytes];
        int offset = 0;
        for (var token : tokens) {
            var array = decoder[token].array();
            System.arraycopy(array, 0, buffer, offset, array.length);
            offset += array.length;
        }
        return new String(buffer, 0, offset, StandardCharsets.UTF_8);
    }

    @Override
    public String tryDecode(int[] tokens) throws CharacterCodingException {
        return tryDecode(tokens, false);
    }

    @Override
    public String tryDecode(int[] tokens, boolean skipSpecial) throws CharacterCodingException {
        int totalBytes = 0;
        for (var token : tokens) {
            if (skipSpecial && shouldSkipSpecial(token)) {
                continue;
            }
            totalBytes += decoder[token].length();
        }
        byte[] buffer = new byte[totalBytes];
        int offset = 0;
        for (var token : tokens) {
            if (skipSpecial && shouldSkipSpecial(token)) {
                continue;
            }
            var array = decoder[token].array();
            System.arraycopy(array, 0, buffer, offset, array.length);
            offset += array.length;
        }
        return charsetDecoder.decode(ByteBuffer.wrap(buffer, 0, offset)).toString();
    }

    /** @return {@code true} when {@code tokenId} is a registered special token. */
    private boolean isSpecialTokenId(int tokenId) {
        return specialTokenIds.contains(tokenId);
    }

    /**
     * Whether a special should be omitted from streamed decode. Control
     * specials ({@code <|im_end|>}, pads, …) are skipped; thinking markers
     * stay so clients can style reasoning spans.
     */
    private boolean shouldSkipSpecial(int tokenId) {
        return isSpecialTokenId(tokenId) && !visibleSpecialTokenIds.contains(tokenId);
    }

    @Override
    public String[] tokenize(String text) {
        ArrayList<String> tokens = new ArrayList<>();
        if (allowSpecialTokens) {
            for (var segment : specialTokenPattern.split(text)) {
                if (specialTokens.containsKey(segment)) {
                    tokens.add(segment);
                } else {
                    for (var matcher = pattern.matcher(segment); matcher.find(); ) {
                        tokens.add(matcher.group());
                    }
                }
            }
        } else {
            for (var matcher = pattern.matcher(text); matcher.find(); ) {
                tokens.add(matcher.group());
            }
        }

        return tokens.toArray(new String[0]);
    }

    /**
     * Loads a tiktoken model file.
     * @param path The tiktoken model file path.
     * @return the token -> rank map.
     * @throws IOException if fail to load the model.
     */
    public static Map<Bytes, Integer> load(String path) throws IOException {
        logger.info("Loading tiktoken model from {}", path);
        var decoder = Base64.getDecoder();
        Map<Bytes, Integer> encoder = new HashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line = reader.readLine();

            while (line != null) {
                String[] tokens = line.split("\\s+");
                encoder.put(new Bytes(decoder.decode(tokens[0])), Integer.parseInt(tokens[1]));
                line = reader.readLine();
            }
        }
        return encoder;
    }
}
