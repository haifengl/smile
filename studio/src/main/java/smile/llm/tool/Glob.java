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
package smile.llm.tool;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import smile.llm.Conversation;
import smile.llm.client.ResponseHandler;

@JsonClassDescription("""
- Fast file pattern matching tool that works with any codebase size
- Supports glob patterns like "**/*.js" or "src/**/*.ts"
- Returns matching file paths sorted by modification time
- Use this tool when you need to find files by name patterns
- When you are doing an open ended search that may require multiple rounds of globbing and grepping, use the Agent tool instead
- You can call multiple tools in a single response. It is always better to speculatively perform multiple searches in parallel if they are potentially useful.
""")
public class Glob implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The glob pattern to match files against")
    public String pattern;

    @JsonPropertyDescription("The directory to search in. If not specified, the current working directory will be used. IMPORTANT: Omit this field to use the default directory. DO NOT enter \"undefined\" or \"null\" - simply omit it for the default behavior. Must be a valid directory path if provided.")
    public String path;

    @Override
    public String run(Conversation conversation, ResponseHandler handler) {
        if (path == null) {
            path = conversation.cwd().toString();
        }
        handler.onStatus("Searching files " + pattern + " in " + path);
        return globFiles(pattern, path);
    }

    /** Static helper method to edit a file. */
    public static String globFiles(String pattern, String path) {
        List<Path> files = new ArrayList<>();
        var matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try {
            Files.walkFileTree(Path.of(path), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matcher.matches(file)) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            return "Error: " + ex.getMessage();
        }

        if (files.isEmpty()) return "No files found";

        // Sort from newest to oldest (descending order)
        files.sort((f1, f2) -> Long.compare(f2.toFile().lastModified(), f1.toFile().lastModified()));

        int limit = 2000;
        String result = files.stream().limit(limit).map(Path::toString)
                .collect(Collectors.joining("\n"));
        if (files.size() > limit) {
            result += String.format("\n[%d files skipped]", files.size() - limit);
        }
        return result;
    }

    /**
     * The specification for Glob tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(Glob.class,
                    List.of(Glob.class.getMethod("globFiles", String.class, String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(Glob.class, List.of());
    }
}
