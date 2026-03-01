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
import java.util.Properties;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import smile.llm.tool.Tool;

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

    /** The parameters for LLM. */
    private final Properties params = new Properties();
    /** The conversation history. */
    private final List<Message> messages = new ArrayList<>();
    /** The tools available for LLM. */
    private final List<Class<? extends Tool>> tools;
    /** The directory path for conversation history and summary. */
    private final Path path;

    /**
     * Constructor. New messages will be saved to history file.
     * @param tools the tools available for the LLM inference.
     * @param path the directory path for conversations.
     */
    public Conversation(List<Class<? extends Tool>> tools, Path path) {
        this.tools = tools;
        var formatter = DateTimeFormatter.ofPattern("yyMMddHHmmssSSS");
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
     * Returns the parameters for LLM.
     * @return the parameters for LLM.
     */
    public Properties params() {
        return params;
    }

    /**
     * Returns the tools available for LLM.
     * @return the tools available for LLM.
     */
    public List<Class<? extends Tool>> tools() {
        return tools;
    }

    /**
     * Returns the conversation messages.
     * @return the conversation messages.
     */
    public List<Message> messages() {
        return messages;
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
     * Clears the in-memory conversation session.
     */
    public void clear() {
        messages.clear();
    }

    /**
     * Compacts the conversation with a summary to set context space.
     * @param summary the summary of conversation.
     */
    public void compact(String summary) {
        try {
            clear();
            add(Message.assistant(summary));
            Files.writeString(path.resolve(MEMORY_MD), summary);
        } catch (IOException ex) {
            logger.error("Failed to save conversation summary", ex);
        }
    }
}
