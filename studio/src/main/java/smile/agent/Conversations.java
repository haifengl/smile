/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import smile.llm.client.Message;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.node.ObjectNode;

/**
 * The conversation history.
 *
 * @author Haifeng Li
 */
public class Conversations {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Conversations.class);
    /** The file name for conversation history. */
    private static final String HISTORY_FILE = "history.jsonl";
    /** The file name of conversation summary. */
    private static final String MEMORY_MD = "MEMORY.md";
    /** The conversation history. */
    private final List<Message> conversations = new ArrayList<>();
    /** The directory path for conversation history and summary. */
    private final Path path;
    /** The JSON object mapper. */
    private final ObjectMapper mapper;
    /** The summary of conversations. */
    private Memory summary;

    /**
     * Constructor. Loads the conversation history if it exists.
     * New messages will be saved after each conversation.
     * @param path the directory path for conversation history.
     */
    public Conversations(Path path) {
        this.path = path;
        // Ignore null fields
        this.mapper = JsonMapper.builder()
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
                .build();

        if (!Files.exists(path)) {
            // Creates all parent directories
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                logger.error("Failed to create folder of conversation history", ex);
            }
            return;
        }

        Path historyFile = path.resolve(HISTORY_FILE);
        if (Files.exists(historyFile)) {
            try (Stream<String> lines = Files.lines(historyFile)) {
                lines.forEach(line -> {
                    if (!line.trim().isEmpty()) {
                        Message message = mapper.readValue(line, Message.class);
                        conversations.add(message);
                    }
                });

                if (conversations.size() > 100) {
                    conversations.subList(0, conversations.size() - 100).clear();
                }
            } catch (IOException ex) {
                logger.error("Failed to load conversation history", ex);
            }
        }

        Path memoryFile = path.resolve(MEMORY_MD);
        if (Files.exists(memoryFile)) {
            try {
                summary = Memory.from(memoryFile);
            } catch (IOException ex) {
                logger.error("Failed to load conversation summary", ex);
            }
        }
    }

    /**
     * Clears the in-memory conversation history.
     */
    public void clear() {
        conversations.clear();
    }

    /**
     * Adds a message to the conversation history and saves it to the file.
     * @param message the message to add.
     */
    public void add(Message message) {
        conversations.add(message);
        try {
            String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
            Files.writeString(path.resolve(HISTORY_FILE), jsonLine, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (Exception ex) {
            logger.error("Failed to save conversation", ex);
        }
    }

    /**
     * Returns the recent conversation history.
     * @param size the number of recent conversations.
     *             If size is <= 0, return the full conversations.
     * @return the recent conversation history.
     */
    public List<Message> getLast(int size) {
        if (size <= 0) return conversations;
        // keeps only the recent conversations within the window size
        int fromIndex = Math.max(conversations.size() - size, 0);
        // subList returns a view that becomes inconsistent if the original list is modified.
        return new ArrayList<>(conversations.subList(fromIndex, conversations.size()));
    }

    /**
     * Returns the summary of conversations.
     * @return the summary of conversations.
     */
    public String getSummary() {
        return summary != null ? summary.content() : "";
    }

    /**
     * Sets the summary of conversations and saves it to the file.
     * @param text the summary of conversations.
     */
    public void setSummary(String text) {
        try {
            Path memoryFile = path.resolve(MEMORY_MD);
            ObjectNode metadata = Memory.mapper.createObjectNode();
            metadata.put("name", "conversation-summary");
            metadata.put("description", "Summary of previous conversations.");
            summary = new Memory(text, metadata, memoryFile);
            summary.save();
        } catch (IOException ex) {
            logger.error("Failed to save conversation summary", ex);
        }
    }
}
