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
package smile.llm.tokenizer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import smile.util.Bytes;
import smile.util.IntArrayList;

/**
 * tiktoken is a fast BPE tokenizer by OpenAI.
 *
 * @author Haifeng Li
 */
public class Tiktoken implements Tokenizer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Tokenizer.class);
    private static final int MAX_RANK = Integer.MAX_VALUE - 1;
    private static final int DUMMY_RANK = Integer.MAX_VALUE;

    /** The regex pattern to split the input text into tokens. */
    private final Pattern pattern;
    /** The regex pattern to detect special tokens. */
    private final Pattern specialTokenPattern;
    /** Token -> ID */
    protected final Map<Bytes, Integer> encoder;
    /** Special Token -> ID */
    protected final Map<String, Integer> specialTokenEncoder;
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
     * Constructor.
     * @param pattern The regex pattern to split the input text into tokens.
     * @param encoder The token to id map.
     * @param bos The beginning of sequence token.
     * @param eos The end of sequence token.
     * @param specialTokens Optional special tokens.
     */
    public Tiktoken(Pattern pattern, Map<Bytes, Integer> encoder, String bos, String eos, String... specialTokens) {
        this.pattern = pattern;
        this.encoder = encoder;

        int size = encoder.size();
        this.decoder = new Bytes[size + specialTokens.length];
        for (var entry : encoder.entrySet()) {
            this.decoder[entry.getValue()] = entry.getKey();
        }

        this.specialTokenPattern = specialTokenRegex(specialTokens);
        this.specialTokenEncoder = new HashMap<>();
        for (int i = 0; i < specialTokens.length; i++) {
            int id = size + i;
            this.specialTokenEncoder.put(specialTokens[i], id);
            this.decoder[id] = new Bytes(specialTokens[i]);
        }

        this.bos = this.specialTokenEncoder.get(bos);
        this.eos = this.specialTokenEncoder.get(eos);
        logger.info("#words: {} | BOS ID: {} | EOS ID: {}", decoder.length, this.bos, this.eos);
    }

    /**
     * Returns the regex for special tokens.
     * @param tokens special tokens.
     * @return the pattern regex.
     */
    private Pattern specialTokenRegex(String... tokens) {
        var inner = Arrays.stream(tokens).map(s -> Pattern.quote(s))
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

    @Override
    public int[] encode(String text) {
        return encode(text, false, false);
    }

    @Override
    public int[] encode(String text, boolean bos, boolean eos) {
        String[] tokens = tokenize(text);
        IntArrayList output = new IntArrayList(2 * tokens.length);
        IntArrayList ranks = new IntArrayList(text.length());

        if (bos) {
            output.add(this.bos);
        }

        for (var token : tokens) {
            var id = specialTokenEncoder.get(token);
            if (id != null && allowSpecialTokens) {
                output.add(id);
            } else {
                var piece = new Bytes(token);
                id = encoder.get(piece);
                if (id != null) {
                    output.add(id);
                } else {
                    bytePairEncode(piece, output, ranks);
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
     * @param ranks a workspace.
     */
    private void bytePairEncode(Bytes piece, IntArrayList output, IntArrayList ranks) {
        int length = piece.length();
        assert length > 1;
        ranks.clear();
        ranks.ensureCapacity(length + 1);

        int minRankIndex = -1;
        for (int i = 0, minRank = MAX_RANK; i <= length; i++) {
            int encoded = encode(piece, i, i + 2);
            if (encoded != MAX_RANK) {
                if (encoded < minRank) {
                    minRankIndex = i;
                    minRank = encoded;
                }
            }
            ranks.add(encoded);
        }

        bytePairMerge(piece, ranks, minRankIndex);

        for (int start = 0, end = 1; end < ranks.size(); end++) {
            if (ranks.get(end) != DUMMY_RANK) {
                int token = encode(piece, start, end);
                assert token != MAX_RANK : "Token should not be MAX_RANK";
                output.add(token);
                start = end;
            }
        }
    }

    private void bytePairMerge(Bytes piece, IntArrayList ranks, int minRankIndex) {
        int length = piece.length();
        while (minRankIndex >= 0) {
            int previousIndex = getPreviousIndex(ranks, minRankIndex - 1);
            int nextIndex = getNextIndex(ranks, minRankIndex + 1);
            int nextNextIndex = getNextIndex(ranks, nextIndex + 1);
            int nextNextNextIndex = getNextIndex(ranks, nextNextIndex + 1);

            if (previousIndex >= 0) {
                assert ranks.get(previousIndex) != DUMMY_RANK;
                int newRank = encode(piece, previousIndex, nextNextIndex);
                ranks.set(previousIndex, newRank);
            }
            assert ranks.get(minRankIndex) != DUMMY_RANK;
            int newRank = encode(piece, minRankIndex, nextNextNextIndex);
            ranks.set(minRankIndex, newRank);

            ranks.set(nextIndex, DUMMY_RANK);

            length--;
            if (length < 3) {
                break; // single tokens were already filtered out, let's skip a minimum calculation
            } else {
                minRankIndex = getMinRankIndex(ranks);
            }
        }
        assert getMinRankIndex(ranks) < 0;
    }

    private static int getMinRankIndex(IntArrayList ranks) {
        int minRankIndex = -1;
        int minRank = MAX_RANK;

        int i = 0;
        int length = ranks.size() - 3;
        for (; i < length - 2; i++) {
            int r = ranks.get(i);
            if (r < minRank) {
                minRankIndex = i;
                minRank = r;
            }
        }

        for (; i <= length; i++) {
            int r = ranks.get(i);
            if (r < minRank) {
                minRankIndex = i;
                minRank = r;
            }
        }

        return minRankIndex;
    }

    private static int getNextIndex(IntArrayList ranks, int nextIndex) {
        while (nextIndex < ranks.size() && ranks.get(nextIndex) == DUMMY_RANK) {
            nextIndex++;
        }
        return nextIndex;
    }

    private static int getPreviousIndex(IntArrayList ranks, int previousIndex) {
        while (previousIndex >= 0 && ranks.get(previousIndex) == DUMMY_RANK) {
            previousIndex--;
        }
        return previousIndex;
    }

    /**
     * Encodes a part of piece.
     * @param piece the piece of text.
     * @param start the initial index of the range to be included, inclusive
     * @param end the final index of the range to be included, exclusive.
     * @return the token id if the part of piece is in the vocabulary or MAX_RANK.
     */
    private int encode(Bytes piece, int start, int end) {
        if (end > piece.length() || end - start == piece.length()) {
            return MAX_RANK;
        } else {
            var rank = encoder.get(piece.slice(start, end));
            return rank != null ? rank : MAX_RANK;
        }
    }

    @Override
    public String decode(int[] tokens) {
        StringBuilder sb = new StringBuilder();
        for (var token : tokens) {
            sb.append(decoder[token].toString());
        }
        return sb.toString();
    }

    @Override
    public String[] tokenize(String text) {
        ArrayList<String> tokens = new ArrayList<>();
        if (allowSpecialTokens) {
            for (var segment : specialTokenPattern.split(text)) {
                if (specialTokenEncoder.containsKey(segment)) {
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

        return tokens.toArray(new String[tokens.size()]);
    }

    /**
     * Loads a tiktoken model file.
     * @param path The tiktoken model file path.
     * @return the token-id map.
     * @throws IOException if fail to load the model.
     */
    public static Map<Bytes, Integer> load(String path) throws IOException {
        logger.info("Loading tiktoken model from {}", path);
        var decoder = Base64.getDecoder();
        Map<Bytes, Integer> encoder = new HashMap<>();
        try (var reader = new BufferedReader(new FileReader(path))) {
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
