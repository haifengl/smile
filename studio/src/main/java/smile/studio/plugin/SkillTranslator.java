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

/**
 * Translates Claude Code plugin skills into {@code ioa} skills.
 *
 * <p>A Claude skill is already {@code skills/&lt;dir&gt;/SKILL.md} with YAML frontmatter, so
 * the shape matches {@code ioa} directly. Two things change: the skill's command name
 * is namespaced with the plugin prefix (Claude namespaces at the harness level with
 * {@code plugin:skill}; ioa is flat), and {@code ioa} requires a {@code name} and
 * {@code description} that Claude lets a skill omit. Path variables in the body,
 * scripts, and references are rewritten to absolute local paths.
 *
 * <p>Claude frontmatter fields {@code allowed-tools}, {@code argument-hint},
 * {@code user-invocable}, {@code disable-model-invocation}, {@code license}, and
 * {@code compatibility} are preserved — {@code ioa} reads all of them.
 *
 * @author Haifeng Li
 */
final class SkillTranslator {

    /**
     * Translates every skill directory in the source plugin.
     * @param context the shared translation state.
     * @return the translated skills.
     * @throws IOException if a file cannot be read or written.
     */
    List<TranslatedPlugin.TranslatedSkill> translate(Translation context) throws IOException {
        List<TranslatedPlugin.TranslatedSkill> skills = new ArrayList<>();
        Path sourceDir = context.source.resolve("skills");
        if (!Files.isDirectory(sourceDir)) {
            return skills;
        }

        List<Path> skillFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(skillFiles::add);
        }

        for (Path skillFile : skillFiles) {
            Path skillSourceDir = skillFile.getParent();
            String localName = skillSourceDir.getFileName().toString();
            Memory memory = Memory.of(skillFile);
            String declared = memory.getString("name", null);
            if (declared != null && !declared.isBlank()) {
                localName = declared;
            }
            skills.add(writeSkill(context, localName, memory, skillSourceDir, false));
        }
        return skills;
    }

    /**
     * Writes one ioa skill, copying any supporting {@code scripts/}, {@code references/},
     * and {@code assets/} directories and rewriting path variables.
     *
     * @param context the shared translation state.
     * @param localName the skill's name within the plugin.
     * @param memory the parsed source frontmatter and body.
     * @param supportingSource the directory holding the source skill's supporting folders,
     *                         or null when there are none.
     * @param fromCommand whether the skill came from a command file.
     * @return the translated skill.
     * @throws IOException if a file cannot be read or written.
     */
    static TranslatedPlugin.TranslatedSkill writeSkill(Translation context, String localName, Memory memory,
                                                       Path supportingSource, boolean fromCommand)
            throws IOException {
        String materialized = context.id.namespace(localName);
        String description = memory.getString("description", null);
        boolean missingDescription = description == null || description.isBlank();
        if (missingDescription) {
            description = "Skill from the " + context.id.name() + " plugin.";
        }

        Frontmatter frontmatter = new Frontmatter()
                .put("name", materialized)
                .put("description", description)
                .put("argument-hint", memory.getString("argument-hint", null))
                .put("license", memory.getString("license", null))
                .put("compatibility", memory.getString("compatibility", null))
                .putList("allowed-tools", memory.getStringList("allowed-tools"));
        // Preserve the two invocation flags only when present (ioa defaults differ).
        if (memory.metadata().has("user-invocable")) {
            frontmatter.putRaw("user-invocable: " + memory.getBoolean("user-invocable", true));
        }
        if (memory.metadata().has("disable-model-invocation")) {
            frontmatter.putRaw("disable-model-invocation: "
                    + memory.getBoolean("disable-model-invocation", false));
        }

        String body = context.rewritePathVariables(memory.content());
        String rendered = frontmatter.render(body);

        Path targetDir = PathGuard.assertInside(context.target,
                context.target.resolve("skills").resolve(materialized));
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("SKILL.md"), rendered);
        copySupporting(context, supportingSource, targetDir);

        StringBuilder note = new StringBuilder(fromCommand
                ? "converted from a command file into a skill"
                : "kept as an ioa skill");
        if (missingDescription) {
            note.append("; description was missing and defaulted");
        }
        context.record(ComponentDisposition.converted(fromCommand ? "commands" : "skills",
                localName, note.toString()));

        return new TranslatedPlugin.TranslatedSkill(materialized, targetDir, fromCommand);
    }

    /**
     * Copies the {@code scripts/}, {@code references/}, and {@code assets/} folders and
     * rewrites path variables inside text files.
     */
    private static void copySupporting(Translation context, Path source, Path targetDir) throws IOException {
        if (source == null || !Files.isDirectory(source)) {
            return;
        }
        for (String folder : List.of("scripts", "references", "assets")) {
            Path from = source.resolve(folder);
            if (!Files.isDirectory(from)) continue;
            Path to = targetDir.resolve(folder);
            Files.createDirectories(to);
            try (var stream = Files.walk(from)) {
                for (Path path : stream.filter(Files::isRegularFile).toList()) {
                    Path rel = from.relativize(path);
                    Path dest = PathGuard.assertInside(targetDir, to.resolve(rel));
                    Files.createDirectories(dest.getParent());
                    rewriteOrCopy(context, path, dest);
                }
            }
        }
    }

    /** Rewrites a text file's path variables, or copies binary files verbatim. */
    private static void rewriteOrCopy(Translation context, Path source, Path target) throws IOException {
        String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean textual = name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".json")
                || name.endsWith(".sh") || name.endsWith(".py") || name.endsWith(".js")
                || name.endsWith(".yml") || name.endsWith(".yaml");
        if (textual) {
            String content = Files.readString(source);
            Files.writeString(target, context.rewritePathVariables(content));
        } else {
            Files.copy(source, target);
        }
    }
}
