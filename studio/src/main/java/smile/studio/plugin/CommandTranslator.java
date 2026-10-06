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
 * Translates Claude Code plugin commands into {@code ioa} skills.
 *
 * <p>In {@code ioa} a slash command <em>is</em> a skill: {@code AgentCLI.runSlashCommand}
 * falls through to {@code runSkill}. A Claude command is a single markdown file
 * {@code commands/&lt;name&gt;.md}; a skill is a directory {@code skills/&lt;name&gt;/SKILL.md}.
 * This translator reshapes the file into the directory form, preserving the command's
 * frontmatter and rewriting path variables.
 *
 * <p>A nested command path such as {@code commands/db/migrate.md} would become
 * {@code /plugin:db:migrate} in Claude. {@code ioa} command names are flat, so the
 * segments are joined with {@code --}: the skill name is {@code db--migrate}.
 *
 * @author Haifeng Li
 */
final class CommandTranslator {

    /**
     * Translates every command file in the source plugin.
     * @param context the shared translation state.
     * @return the translated skills (each originating from a command).
     * @throws IOException if a file cannot be read or written.
     */
    List<TranslatedPlugin.TranslatedSkill> translate(Translation context) throws IOException {
        List<TranslatedPlugin.TranslatedSkill> skills = new ArrayList<>();
        Path sourceDir = context.source.resolve("commands");
        if (!Files.isDirectory(sourceDir)) {
            return skills;
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .sorted()
                    .forEach(files::add);
        }

        for (Path file : files) {
            String localName = flattenName(sourceDir, file);
            Memory memory = Memory.of(file);
            String declared = memory.getString("name", null);
            if (declared != null && !declared.isBlank()) {
                localName = declared;
            }
            skills.add(SkillTranslator.writeSkill(context, localName, memory, null, true));
        }
        return skills;
    }

    /**
     * Joins a nested command path into a flat name: {@code db/migrate.md} → {@code db--migrate}.
     * @param root the commands directory.
     * @param file the command file.
     * @return the flattened local name.
     */
    private static String flattenName(Path root, Path file) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.toLowerCase(Locale.ROOT).endsWith(".md")) {
            relative = relative.substring(0, relative.length() - 3);
        }
        // Join segments with the same `--` separator used for namespacing, so a
        // nested command cannot collide with a flat one.
        return relative.replace('/', '-').replace("--", "-").replace("-", "--").replace(' ', '-');
    }
}
