/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ioa.agent.memory.Memory;
import smile.util.OS;

/**
 * Translates a Claude Code plugin's agent files into {@code ioa} subagents.
 *
 * <p>Claude treats any markdown file under {@code agents/} as an agent and derives
 * its name from the file name, and required fields may be missing. {@code ioa}
 * requires a directory {@code agents/&lt;name&gt;/AGENT.md} whose frontmatter has both
 * {@code name} and {@code description}-missing either makes the spec fail to load.
 * This translator supplies the missing fields and materializes the directory shape.
 *
 * <p>Frontmatter mapping:
 * <ul>
 *   <li>{@code model} (sonnet/opus/haiku/inherit) has no ioa alias → omitted, so the
 *       subagent inherits the parent's model (converted, with a note).</li>
 *   <li>{@code tools} is mapped to ioa tool names; {@code Bash} becomes the host's
 *       shell tool. Scoped forms like {@code Bash(git:*)} are reduced to the tool name.</li>
 *   <li>{@code disallowedTools} has no ioa equivalent (ioa has an allow-list only) →
 *       dropped, with a note.</li>
 * </ul>
 *
 * <p>A translated agent is recorded {@code DEGRADED}: it resolves by name but the
 * Task tool does not advertise user-defined subagents, so the model cannot discover
 * it until ioa change A6 (ADR-009).
 *
 * @author Haifeng Li
 */
final class AgentTranslator {

    /**
     * Translates every agent file in the source plugin.
     * @param context the shared translation state.
     * @return the translated agents.
     * @throws IOException if a file cannot be read or written.
     */
    List<TranslatedPlugin.TranslatedAgent> translate(Translation context) throws IOException {
        List<TranslatedPlugin.TranslatedAgent> agents = new ArrayList<>();
        Path sourceDir = context.source.resolve("agents");
        if (!Files.isDirectory(sourceDir)) {
            return agents;
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .forEach(files::add);
        }

        for (Path file : files) {
            agents.add(translateOne(context, file, sourceDir));
        }
        return agents;
    }

    /** Translates one agent file. */
    private TranslatedPlugin.TranslatedAgent translateOne(Translation context, Path file, Path sourceDir)
            throws IOException {
        Memory memory = Memory.of(file);
        String fileName = stripExtension(file.getFileName().toString());
        String declaredName = memory.getString("name", null);
        String localName = declaredName != null && !declaredName.isBlank() ? declaredName : fileName;
        String materialized = context.id.namespace(localName);

        String description = memory.getString("description", null);
        boolean missingDescription = description == null || description.isBlank();
        if (missingDescription) {
            // ioa's Spec.from rejects an agent with no description; supply a default
            // so the agent still loads, and record the gap.
            description = "Agent from the " + context.id.name() + " plugin.";
        }

        List<String> tools = mapTools(memory.getStringList("tools"));
        List<String> disallowed = memory.getStringList("disallowedTools");
        boolean hadModel = memory.getString("model", null) != null;

        Frontmatter frontmatter = new Frontmatter()
                .put("name", materialized)
                .put("description", description)
                .putList("tools", tools);
        if (hadModel) {
            // Drop the Claude model alias; the subagent inherits the parent's model.
            context.record(ComponentDisposition.converted("agents", localName,
                    "model alias dropped; the subagent inherits the parent's model"));
        }

        // Rewrite plugin path variables in the prompt body.
        String prompt = context.rewritePathVariables(memory.content());
        String rendered = frontmatter.render(prompt);

        Path targetDir = PathGuard.assertInside(context.target, context.target.resolve("agents").resolve(materialized));
        Files.createDirectories(targetDir);
        Path agentFile = targetDir.resolve("AGENT.md");
        Files.writeString(agentFile, rendered);

        StringBuilder note = new StringBuilder();
        if (missingDescription) {
            note.append("description was missing and defaulted; ");
        }
        if (!disallowed.isEmpty()) {
            note.append("disallowedTools ignored (ioa has no deny-list); ");
        }
        note.append("not discoverable until ioa advertises user-defined subagents (A6)");
        context.record(ComponentDisposition.degraded("agents", localName, note.toString()));

        return new TranslatedPlugin.TranslatedAgent(materialized, agentFile, true);
    }

    /**
     * Maps Claude tool names to ioa tool names. {@code Bash} becomes the host's shell
     * tool; a scoped name such as {@code Bash(git:*)} is reduced to its tool.
     *
     * @param tools the Claude tool names.
     * @return the ioa tool names.
     */
    static List<String> mapTools(List<String> tools) {
        List<String> mapped = new ArrayList<>();
        for (String tool : tools) {
            if (tool == null || tool.isBlank()) continue;
            String name = tool;
            int paren = name.indexOf('(');
            if (paren > 0) {
                name = name.substring(0, paren).trim();
            }
            switch (name) {
                case "Bash" -> name = OS.isWindows() ? "PowerShell" : "Bash";
                default -> { /* names match ioa's tool set for the common tools */ }
            }
            if (!mapped.contains(name)) {
                mapped.add(name);
            }
        }
        return mapped;
    }

    /** Removes a trailing {@code .md}. */
    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
