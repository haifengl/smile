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
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Context is a critical but finite resource for AI agents.
 * Context refers to the set of tokens included when sampling from
 * a large-language model (LLM). The key problem is optimizing the
 * utility of those tokens against the inherent constraints of LLMs
 * in order to consistently achieve a desired outcome.
 * <p>
 * Context engineering refers to the set of strategies for curating
 * and maintaining the optimal set of tokens during LLM inference,
 * including all the other information that may land there out of
 * the prompts. That is, considering the holistic state available to
 * the LLM at any given time and what potential behaviors that state
 * might yield.
 * <p>
 * SMILE uses a hierarchical context loading system. It will combine
 * global context (from ${smile.home}/agent/SMILE.md) for managed policy,
 * user context (from ~/.smile/SMILE.md) for cross-project defaults,
 * and project-specific SMILE.md, and rules and skills in subfolders.
 *
 * @author Haifeng Li
 */
public class Context {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Context.class);
    /** The file name of project instructions. */
    public static final String SMILE_MD = "SMILE.md";
    /** The file name of skill instructions. */
    public static final String SKILL_MD = "SKILL.md";
    /** The folder path of context information. */
    private final Path path;
    /**
     * Top-level instructions that apply to all interactions, such as
     * system instructions, global rules, etc. These are the most
     * critical pieces of information for guiding the agent's behavior
     * and should be prioritized in the context window.
     */
    private final List<Memory> instructions = new ArrayList<>();
    /**
     * Rules are mandatory, context-independent instructions that apply
     * to every interaction for consistency.
     */
    private final List<Rule> rules = new ArrayList<>();
    /**
     * Specs (Specifications) are the documents that define the requirements,
     * intent, data structure, metadata, and statistics, serving
     * as the blueprint for the AI to follow.
     */
    private final List<Spec> specs = new ArrayList<>();
    /**
     * Skills are reusable capabilities or tools that the agent can
     * invoke to perform specific tasks.
     */
    private final List<Skill> skills = new ArrayList<>();
    /**
     * Custom slash commands. Commands are essentially pre-defined
     * prompt templates.
     */
    private final List<Command> commands = new ArrayList<>();

    /**
     * Constructor.
     * @param path the folder path of holistic context information
     *             such as system instructions, skills, specs, etc.
     */
    public Context(String path) {
        this(Path.of(path));
    }

    /**
     * Constructor.
     * @param path the folder path of holistic context information
     *             such as system instructions, skills, specs, etc.
     */
    public Context(Path path) {
        this.path = path;
        load();
    }

    /** Loads the context from disk. */
    private void load() {
        Path smileMd = path.resolve(SMILE_MD);
        try {
            if (Files.exists(smileMd)) {
                instructions.add(Memory.from(smileMd));
            }
        } catch (Exception ex) {
            logger.error("Error reading {}", smileMd, ex);
        }

        var ruleDir = path.resolve("rules");
        if (Files.exists(ruleDir)) {
            try (var stream = Files.newDirectoryStream(ruleDir, "*.md")) {
                for (Path file : stream) {
                    rules.add(Rule.from(file));
                }
            } catch (IOException | DirectoryIteratorException ex) {
                logger.error("Error reading rules", ex);
            }
        }

        var specDir = path.resolve("specs");
        if (Files.exists(specDir)) {
            try (var stream = Files.newDirectoryStream(specDir, "*.md")) {
                for (Path file : stream) {
                    specs.add(Spec.from(file));
                }
            } catch (IOException | DirectoryIteratorException ex) {
                logger.error("Error reading specs", ex);
            }
        }

        var commandDir = path.resolve("commands");
        if (Files.exists(commandDir)) {
            try (var stream = Files.newDirectoryStream(commandDir, "*.md")) {
                for (Path file : stream) {
                    commands.add(Command.from(file));
                }
            } catch (IOException | DirectoryIteratorException ex) {
                logger.error("Error reading commands", ex);
            }
        }

        var skillDir = path.resolve("skills");
        if (Files.exists(skillDir)) {
            try (var stream = Files.newDirectoryStream(skillDir)) {
                for (Path folder : stream) {
                    if (Files.isDirectory(folder)) {
                        skills.add(Skill.from(folder));
                    }
                }
            } catch (IOException | DirectoryIteratorException ex) {
                logger.error("Error reading skills", ex);
            }
        }
    }

    /** Reloads the context from disk. */
    public void refresh() {
        instructions.clear();
        rules.clear();
        specs.clear();
        skills.clear();
        commands.clear();
        load();
    }

    /**
     * Returns the folder path of context information.
     * @return the folder path of context information.
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the top-level instructions.
     * @return the top-level instructions.
     */
    public String instructions() {
        return instructions.stream()
                .map(Memory::content)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    /**
     * Adds the project instructions.
     * @param instructions the project instructions.
     */
    public void addInstructions(String instructions) throws IOException {
        if (this.instructions.isEmpty()) {
            Path smileMd = path.resolve(SMILE_MD);
            var metadata = Memory.mapper.createObjectNode();
            metadata.put("name", "project-instructions");
            metadata.put("description", "Project instruction manual.");
            Memory memory = new Memory(instructions, metadata, smileMd);
            this.instructions.add(memory);
            memory.save();
        } else {
            Memory memory = this.instructions.getFirst();
            var content = memory.content() + "\n\n" + instructions;
            memory = new Memory(content, memory.metadata(), memory.path());
            this.instructions.set(0, memory);
            memory.save();
        }
    }

    /**
     * Returns the rules.
     * @return the rules.
     */
    public List<Rule> rules() {
        return rules;
    }

    /**
     * Returns the specs.
     * @return the specs.
     */
    public List<Spec> specs() {
        return specs;
    }

    /**
     * Returns the skills.
     * @return the skills.
     */
    public List<Skill> skills() {
        return skills;
    }

    /**
     * Returns the commands.
     * @return the commands.
     */
    public List<Command> commands() {
        return commands;
    }
}
