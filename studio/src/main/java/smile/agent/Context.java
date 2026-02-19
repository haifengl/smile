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
import java.util.Map;

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
    /**
     * Global context: system instructions, skills, tools, etc.
     */
    private static final Context global = new Context(System.getProperty("smile.home") + "/agent");
    /**
     * User context: user preferences, history, etc.
     */
    private static final Context user = new Context(System.getProperty("user.home") + "/.smile");

    /**
     * Top-level instructions that apply to all interactions, such as
     * system instructions, global rules, etc. These are the most
     * critical pieces of information for guiding the agent's behavior
     * and should be prioritized in the context window.
     */
    private final Rule instructions;
    /**
     * Rules are mandatory, context-independent instructions that apply
     * to every interaction for consistency.
     */
    private final List<Rule> rules = new ArrayList<>();
    /**
     * Skills are reusable capabilities or tools that the agent can
     * invoke to perform specific tasks.
     */
    private final List<Skill> skills = new ArrayList<>();

    /**
     * Constructor.
     * @param path the folder path of holistic context information
     *             such as system instructions, skills, tools, etc.
     */
    public Context(String path) {
        this(Paths.get(path));
    }

    /**
     * Constructor.
     * @param path the folder path of holistic context information
     *             such as system instructions, skills, tools, etc.
     */
    public Context(Path path) {
        Path smileMd = path.resolve("SMILE.md");
        Rule spec = new Rule("", Map.of(), smileMd);
        try {
            spec = Rule.from(smileMd);
        } catch (IOException ex) {
            logger.error("Error reading SMILE.md", ex);
        }
        instructions = spec;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path.resolve("rules"), ".md")) {
            for (Path file : stream) {
                rules.add(Rule.from(file));
            }
        } catch (IOException | DirectoryIteratorException ex) {
            logger.error("Error reading rules", ex);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path.resolve("skills"))) {
            for (Path entry  : stream) {
                if (Files.isDirectory(entry)) {
                    rules.add(Rule.from(entry.resolve("SKILL.md")));
                }
            }
        } catch (IOException | DirectoryIteratorException ex) {
            logger.error("Error reading skills", ex);
        }
    }

    /**
     * Returns the global context.
     * @return the global context.
     */
    public static Context global() {
        return global;
    }

    /**
     * Returns the user context.
     * @return the user context.
     */
    public static Context user() {
        return user;
    }

    /**
     * Returns the top-level instructions.
     * @return the top-level instructions.
     */
    public Rule instructions() {
        return instructions;
    }

    /**
     * Returns the rules.
     * @return the rules.
     */
    public List<Rule> rules() {
        return rules;
    }

    /**
     * Returns the skills.
     * @return the skills.
     */
    public List<Skill> skills() {
        return skills;
    }
}
