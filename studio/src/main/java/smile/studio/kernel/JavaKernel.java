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
package smile.studio.kernel;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
public class JavaKernel extends Kernel<SnippetEvent> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JavaKernel.class);
    /** JShell instance. */
    private JShell jshell;
    /** Analysis utilities for source code input. */
    private SourceCodeAnalysis sourceAnalyzer;

    /**
     * Constructor.
     */
    public JavaKernel() {
        restart();
    }

    @Override
    public synchronized void restart() {
        close();

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

        // Note that JShell runs in another JVM so that
        // we need to set up FlatLaf again.
        eval("""
            javax.swing.SwingUtilities.invokeLater(() -> {
                com.formdev.flatlaf.FlatLightLaf.setup();
            });""");
    }

    @Override
    public synchronized void close() {
        if (jshell != null) {
            jshell.stop();
            jshell.close();
            jshell = null;
            sourceAnalyzer = null;
        }
    }

    @Override
    public void reset() {
        // Drop all snippets, restoring the JShell session to its initial state.
        // jshell.eval("/reset") does not work — "/reset" is a jshell tool command,
        // not a Java expression. Drop every snippet individually instead.
        jshell.snippets()
              .filter(s -> jshell.status(s) != Snippet.Status.DROPPED)
              .forEach(jshell::drop);
    }

    @Override
    public void stop() {
        jshell.stop();
    }

    @Override
    public boolean eval(String code, List<Object> values) {
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
            process(events);
            for (var event : events) {
                if (event.exception() != null) {
                    logger.error("Evaluation error: ", event.exception());
                    return false;
                }

                // A previous snippet may be overwritten by a later snippet,
                // e.g., when a variable is re-assigned. In that case, we should
                // not treat it as an error.
                if (event.status() != Snippet.Status.VALID && event.status() != Snippet.Status.OVERWRITTEN) {
                    logger.error("Evaluation status: {}", event.status());
                    return false;
                }

                if (event.snippet() instanceof VarSnippet variable) {
                    values.add(event.value());
                }
            }

            info = sourceAnalyzer.analyzeCompletion(info.remaining());
        }

        if (info.completeness() != SourceCodeAnalysis.Completeness.EMPTY) {
            throw new RuntimeException(info.completeness() + ": " + info.remaining().trim());
        }

        return true;
    }

    /**
     * Processes the snippet events and prints the results to the console.
     * @param events the snippet events to process.
     */
    @Override
    public void process(List<SnippetEvent> events) {
        var output = console.getOutputArea();
        // Capture values, diagnostics, and exceptions in order
        for (SnippetEvent event : events) {
            if (event.status() == Snippet.Status.VALID && event.snippet() instanceof VarSnippet variable) {
                if (!variable.name().matches("\\$\\d+")) {
                    String typeName = variable.typeName();
                    if (output != null) output.print("⇒ " + typeName + " " + variable.name() + " = ");

                    String value = event.value();
                    if (value == null) {
                        if (output != null) output.println("null");
                    } else {
                        if (typeName.endsWith("DataFrame")) {
                            if (output != null) output.println();
                        } else if (typeName.contains("[]")) {
                            // The type may be generic with array, e.g., SVM<double[]>
                            int index = value.indexOf('{');
                            if (index > 0) {
                                value = value.substring(0, index);
                            }
                        }
                        if (output != null) output.println(value);
                    }
                }
            } else if (event.status() == Snippet.Status.REJECTED) {
                if (output != null) output.println("✖ Rejected snippet: " + event.snippet().source());
            } else if (event.status() == Snippet.Status.RECOVERABLE_DEFINED ||
                       event.status() == Snippet.Status.RECOVERABLE_NOT_DEFINED) {
                if (output != null) output.println("⚠ Recoverable issue: " + event.snippet().source());
                if (event.snippet() instanceof DeclarationSnippet snippet) {
                    if (output != null) output.println("⚠ Unresolved dependencies:");
                    unresolvedDependencies(snippet).forEach(name -> {
                        if (output != null) output.println("  └ " + name);
                    });
                }
            }

            diagnostics(event.snippet()).forEach(diag -> {
                String kind = diag.isError() ? "ERROR" : "WARN";
                if (output != null) output.println(String.format("%s: %s",
                        kind, diag.getMessage(Locale.getDefault())));
            });

            if (event.exception() instanceof EvalException ex) {
                if (output != null) output.println(ex.getExceptionClassName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ""));
                // JShell exception stack trace is often concise
                for (StackTraceElement ste : ex.getStackTrace()) {
                    if (output != null) output.println("  at " + ste.toString());
                }
            }
        }
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
    public List<Variable> variables() {
        return jshell.variables()
                .map(v -> new Variable(v.name(), v.typeName()))
                .toList();
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
