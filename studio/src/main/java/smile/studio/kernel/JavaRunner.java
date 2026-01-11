/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import jdk.jshell.*;
import com.formdev.flatlaf.util.SystemInfo;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.resolution.DependencyResolutionException;

/**
 * Java code execution engine.
 *
 * @author Haifeng Li
 */
public class JavaRunner extends Runner {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JavaRunner.class);
    /** JShell instance. */
    private final JShell jshell;
    /** Analysis utilities for source code input. */
    private final SourceCodeAnalysis sourceAnalyzer;

    /**
     * Constructor.
     */
    public JavaRunner() {
        PrintStream shellOut = new PrintStream(console, true, StandardCharsets.UTF_8);
        PrintStream shellErr = new PrintStream(console, true, StandardCharsets.UTF_8);
        var builder = JShell.builder().out(shellOut).err(shellErr)
                .remoteVMOptions("--class-path", System.getProperty("java.class.path"))
                .remoteVMOptions("-XX:MaxMetaspaceSize=1024M")
                .remoteVMOptions("-Xss4M")
                .remoteVMOptions("-XX:MaxRAMPercentage=75")
                .remoteVMOptions("-XX:+UseZGC")
                .remoteVMOptions("--add-opens=java.base/java.nio=ALL-UNNAMED")
                .remoteVMOptions("--enable-native-access=ALL-UNNAMED")
                .remoteVMOptions("-Dsmile.home=" + System.getProperty("smile.home", "."));

        if (SystemInfo.isWindows) {
            // Set to 1.0 for no scaling
            builder = builder.remoteVMOptions("-Dsun.java2d.uiScale=1.0");
        }
        jshell = builder.build();
        sourceAnalyzer = jshell.sourceCodeAnalysis();
    }

    /**
     * Closes this engine and frees resources.
     */
    public void close() {
        jshell.close();
    }

    @Override
    public void stop() {
        jshell.stop();
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     * @return the value of last variable snippet. Or null if no variables.
     */
    public String eval(String code) {
        var wrapper = new Object() { String value = null; };
        eval(code, (events) -> {
            for (var event : events) {
                if (event.status() == Snippet.Status.VALID && event.snippet() instanceof VarSnippet variable) {
                    wrapper.value = event.value();
                }
            }
            return 0;
        });
        return wrapper.value;
    }

    /**
     * Evaluates a code block.
     * @param code a code block.
     * @param callback the callback function for each snippet evaluation results,
     *                 which returns the number of execution errors.
     * @return true if no errors reported by callback.
     */
    public boolean eval(String code, ToIntFunction<List<SnippetEvent>> callback) {
        SourceCodeAnalysis.CompletionInfo info = sourceAnalyzer.analyzeCompletion(code);
        while (info.completeness().isComplete()) {
            String source = info.source();
            String[] lines = source.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("//!")) {
                    evalMagic(line.substring(3));
                }
            }

            List<SnippetEvent> events = jshell.eval(source);
            if (callback != null && callback.applyAsInt(events) > 0) {
                return false;
            }
            info = sourceAnalyzer.analyzeCompletion(info.remaining());
        }

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY) {
            throw new RuntimeException(info.completeness() + ": " + info.remaining().trim());
        }

        return true;
    }

    /**
     * Diagnoses a code snippet.
     * @param snippet a code snippet.
     * @return the diagnostics result stream.
     */
    public Stream<Diag> diagnostics(Snippet snippet) {
        return jshell.diagnostics(snippet);
    }

    /**
     * Returns the active variable snippets.
     * @return the active variable snippets.
     */
    public Stream<VarSnippet> variables() {
        return jshell.variables();
    }

    /**
     * Returns the names of current unresolved dependencies for the snippet.
     * @param snippet the declaration snippet to look up.
     * @return a stream of symbol names that are currently unresolvedDependencies.
     */
    public Stream<String> unresolvedDependencies(DeclarationSnippet snippet) {
        return jshell.unresolvedDependencies(snippet);
    }

    /**
     * Evaluates a magic command.
     * @param magic the magic command line.
     */
    public void evalMagic(String magic) {
        String[] command = magic.trim().split("\\s+");
        if (command[0].equals("mvn")) {
            try {
                for (int i = 1; i < command.length; i++) {
                    addToClasspath(command[i]);
                }
            } catch (DependencyResolutionException | DependencyCollectionException ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }
        } else if (!command[0].isBlank()) {
            logger.warn("Unknown magic: {}", magic);
        }
    }

    /**
     * Adds the specified jar paths to the end of the classpath used in eval().
     * @param jars the jar paths to add to the classpath.
     */
    public void addToClasspath(List<Path> jars) {
        for (Path jar : jars) {
            jshell.addToClasspath(jar.toAbsolutePath().toString());
        }
    }

    /**
     * Adds a maven artifact and its transitive dependencies to the end of the classpath used in eval().
     * @param groupId the group or organization that created the artifact.
     * @param artifactId the specific project within the group.
     * @param version the specific version of the artifact.
     */
    public void addToClasspath(String groupId, String artifactId, String version)
            throws DependencyCollectionException, DependencyResolutionException {
        for (var artifact : Maven.getDependencyJarPaths(groupId, artifactId, version)) {
            if (artifact.getArtifactId().equals(artifactId) && artifact.getExtension().equals("jar")) {
                var path = artifact.getPath();
                if (path != null) {
                    jshell.addToClasspath(path.toAbsolutePath().toString());
                } else {
                    logger.info("{}: null path", artifact);
                }
            }
        }
    }

    /**
     * Adds a maven artifact and its transitive dependencies to the end of the classpath used in eval().
     * @param coordinates the Maven coordinates in GAV (groupId:artifactId:version) format.
     */
    public void addToClasspath(String coordinates)
            throws DependencyCollectionException, DependencyResolutionException {
        String[] gav = coordinates.split(":");
        if (gav.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + coordinates);
        }
        addToClasspath(gav[0], gav[1], gav[2]);
    }
}
