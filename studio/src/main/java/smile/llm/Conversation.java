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
package smile.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The conversation session.
 *
 * @author Haifeng Li
 */
public class Conversation {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Conversation.class);
    /** The file name for conversation history. */
    private static final String HISTORY_FILE = "history.jsonl";
    /** The file name of conversation summary. */
    private static final String MEMORY_MD = "MEMORY.md";
    /** The JSON object mapper with sneak case and ignoring null fields. */
    private static final ObjectMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .changeDefaultPropertyInclusion(incl -> incl.withContentInclusion(JsonInclude.Include.NON_NULL))
            .build();

    /** The conversation history. */
    private final List<Message> messages = new ArrayList<>();
    /** The directory path for conversation history and summary. */
    private final Path path;
    /** The summary of conversations. */
    private String summary = "";

    /**
     * Constructor. New messages will be saved to history file.
     * @param path the directory path for conversations.
     */
    public Conversation(Path path) {
        var formatter = DateTimeFormatter.ofPattern("yyMMddhhmmssSSS");
        String id = formatter.format(LocalDateTime.now());
        path = path.resolve(id);
        this.path = path;
        if (!Files.exists(path)) {
            // Creates all parent directories
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                logger.error("Failed to create folder of conversation history", ex);
            }
        }
    }

    /**
     * Clears the in-memory conversation session.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Adds a message to the conversation session and saves it to the file.
     * @param message the message to add.
     */
    public void add(Message message) {
        messages.add(message);
        try {
            String jsonLine = mapper.writeValueAsString(message) + System.lineSeparator();
            Files.writeString(path.resolve(HISTORY_FILE), jsonLine, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (Exception ex) {
            logger.error("Failed to save conversation", ex);
        }
    }

    /**
     * Returns the recent conversation messages.
     * @param size the number of recent messages.
     *             If size is <= 0, return the full conversation.
     * @return the recent conversation messages.
     */
    public List<Message> getLast(int size) {
        if (size <= 0) return messages;
        // keeps only the recent conversations within the window size
        int fromIndex = Math.max(messages.size() - size, 0);
        // subList returns a view that becomes inconsistent if the original list is modified.
        return new ArrayList<>(messages.subList(fromIndex, messages.size()));
    }

    /**
     * Returns the summary of conversations.
     * @return the summary of conversations.
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Sets the summary of conversations and saves it to the file.
     * @param text the summary of conversations.
     */
    public void setSummary(String text) {
        try {
            Files.writeString(path.resolve(MEMORY_MD), text);
            summary = text;
        } catch (IOException ex) {
            logger.error("Failed to save conversation summary", ex);
        }
    }
}
