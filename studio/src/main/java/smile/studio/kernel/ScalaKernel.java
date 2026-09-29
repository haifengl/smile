/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.kernel;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import smile.util.OS;

/**
 * Scala code execution engine.
 *
 * <p>It drives {@code dotty.tools.repl.Main} in a child JVM process and communicates
 * with it through standard input and output, in the same way
 * {@link PythonKernel} drives {@code ipython}. Running Scala in its own process gives us the
 * real Scala 3 REPL: proper multi-line input, a compiler that matches the
 * language version, and no global {@code System.out} redirection.
 *
 * <p>The REPL is driven one line at a time, because it evaluates only the
 * first line of a multi-line write. For every line the kernel waits for the
 * {@code scala> } prompt, which marks the end of the evaluation. Output is
 * streamed to the cell as it arrives, so a long-running snippet stays
 * responsive.
 *
 * <p>The application classpath is passed to the REPL, so Scala scripts can use
 * the SMILE API just like the other kernels.
 *
 * @author Haifeng Li
 */
public class ScalaKernel extends Kernel<String> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScalaKernel.class);
    /** The localized strings of the kernel. */
    private static final ResourceBundle bundle = ResourceBundle.getBundle(ScalaKernel.class.getName(), Locale.getDefault());
    /** The Scala REPL prompt, which marks the completion of an evaluation. */
    private static final String PROMPT = "scala> ";
    /** ANSI escape sequences, which the REPL may emit. */
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    /** The start of a Scala REPL compiler error, e.g. "-- Error: --" or "-- [E006] Not Found Error:". */
    private static final Pattern COMPILER_ERROR =
            Pattern.compile("^-- (?:Error: --|Error:|\\[E\\d+\\](?!.*Warning:))", Pattern.MULTILINE);
    /** The start of a Scala 3 compiler diagnostic code, e.g. "-- [E006]". */
    private static final Pattern COMPILER_DIAGNOSTIC = Pattern.compile("-- \\[E\\d+\\]");
    /** Error line starting specifically with "-- Error: --". */
    private static final Pattern COMPILER_ERROR_LINE = Pattern.compile("^-- Error: --", Pattern.MULTILINE);
    /** The compiler summary line, e.g. "1 error found". */
    private static final Pattern COMPILER_SUMMARY = Pattern.compile("^ *\\d+ errors? found", Pattern.MULTILINE);
    /** A stack trace or elision marker emitted by the REPL for an uncaught exception. */
    private static final Pattern STACK_TRACE =
            Pattern.compile("\\R\\s*(?:\\.\\.\\. \\d+ elided|at\\s+)");
    /** An uncaught exception raised by the snippet. */
    private static final Pattern RUNTIME_ERROR =
            Pattern.compile("^(?:[\\w$.]+\\.)?[A-Z]\\w*(?:Exception|Error|Throwable)(?:: .*)?$", Pattern.MULTILINE);
    /** Fallback pattern for uncaught qualified exceptions when stack trace is absent. */
    private static final Pattern RUNTIME_ERROR_QUALIFIED =
            Pattern.compile("^(?:java|scala|javax|jakarta)\\.[\\w$.]+\\.[A-Z]\\w*(?:Exception|Error|Throwable)(?:: .*)?$", Pattern.MULTILINE);
    /** A value or variable declaration echoed by the REPL. */
    private static final Pattern DECLARATION =
            Pattern.compile("^(?:val|var) (\\w+): (.+?) = ", Pattern.MULTILINE);
    /** A class, trait, object, enum or def definition echoed by the REPL. */
    private static final Pattern DEFINITION =
            Pattern.compile("^// defined (?:case )?(\\w+) (\\w+)", Pattern.MULTILINE);

    /** How long to wait for the first prompt. */
    private static final long STARTUP_TIMEOUT_MS = 60 * 1000L;
    /** How long to wait for a prompt before treating the line as incomplete. */
    private static final long EVAL_TIMEOUT_MS = 3 * 60 * 1000L;

    /** Parser context for checking statement completeness. */
    private final dotty.tools.dotc.core.Contexts.Context parseCtx =
            new dotty.tools.dotc.core.Contexts.ContextBase().initialCtx();

    /** Print REPL output to the output area. */
    private final PrintWriter out = new PrintWriter(console, true, StandardCharsets.UTF_8);
    /** The variables declared in the session. */
    private final Map<String, String> variables = new LinkedHashMap<>();

    /** Child JVM process running dotty.tools.repl.Main. */
    private volatile Process process;
    /** Send commands to the child process's input. */
    private BufferedWriter writer;
    /** The thread that pumps the process's output. */
    private Thread pump;
    /** Guards {@link #pending} and {@link #eof}. */
    private final Object lock = new Object();
    /** Output received but not yet consumed by {@link #eval}. */
    private final StringBuilder pending = new StringBuilder();
    /** Whether the pump reached the end of the process output. */
    private boolean eof = false;
    /** Holds back a possible partial prompt between reads. */
    private final StringBuilder carry = new StringBuilder();
    /** Whether the initial startup prompt has been consumed. */
    private volatile boolean started = false;
    /** Whether output display is suppressed (e.g. during reset). */
    private volatile boolean suppressingOutput = false;

    /**
     * Constructor.
     */
    public ScalaKernel() {
        restart();
    }

    @Override
    public synchronized void restart() {
        close();
        try {
            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-XX:MaxMetaspaceSize=1024M");
            command.add("-Xss4M");
            command.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/sun.swing=ALL-UNNAMED");
            if (OS.isMacOS()) {
                command.add("--add-opens=java.desktop/sun.lwawt=ALL-UNNAMED");
                command.add("--add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED");
            }
            command.add("--enable-native-access=ALL-UNNAMED");
            if (OS.isWindows()) {
                // Icons may become blurry due to desktop scaling with standard JDK.
                // Set to 1.0 for no scaling if running with standard JDK.
                // However, JBR optimizes HiDPI scaling.
                //command.add("-Dsun.java2d.uiScale=1.0");
            }
            String home = System.getProperty("smile.home");
            if (home != null && !home.isBlank()) {
                command.add("-Dsmile.home=" + home);
            }
            command.add("-Dscala.usejavacp=true");

            String classpath = classpath();
            if (!classpath.isEmpty()) {
                command.add("-cp");
                command.add(classpath);
            }

            command.add("dotty.tools.repl.Main");
            command.add("-color");
            command.add("never");
            command.add("-usejavacp");

            String predef = loadPredef();
            if (!predef.isEmpty()) {
                command.add("-repl-init-script");
                command.add(predef);
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().put("TERM", "dumb");
            builder.redirectErrorStream(true);
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            started = false;
            suppressingOutput = false;
            synchronized (lock) {
                pending.setLength(0);
                eof = false;
            }
            synchronized (carry) {
                carry.setLength(0);
            }
            variables.clear();

            pump = new Thread(this::pump, "scala-repl-output");
            pump.setDaemon(true);
            pump.start();

            // Consume the banner and the first prompt.
            if (awaitPrompt(STARTUP_TIMEOUT_MS) == null) {
                logger.error("Timed out waiting for the Scala REPL to start.");
            } else {
                started = true;
                synchronized (carry) {
                    carry.setLength(0);
                }
                suppressingOutput = true;
                try {
                    sendLine("javax.swing.SwingUtilities.invokeLater(() => com.formdev.flatlaf.FlatLightLaf.setup())");
                    awaitQuietly(EVAL_TIMEOUT_MS);
                } finally {
                    synchronized (carry) {
                        carry.setLength(0);
                    }
                    suppressingOutput = false;
                }
            }
        } catch (IOException ex) {
            logger.error("Failed to start the Scala REPL: {}", ex.getMessage());
            JOptionPane.showMessageDialog(
                    null,
                    bundle.getString("ScalaCliInstallMessage"),
                    bundle.getString("ScalaCliInstallTitle"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        started = false;
        suppressingOutput = false;
        if (pump != null) {
            pump.interrupt();
            pump = null;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
        writer = null;
        synchronized (lock) {
            eof = true;
            pending.setLength(0);
            lock.notifyAll();
        }
    }

    @Override
    public void reset() {
        // The REPL implements :reset, which forgets all session entries
        // without restarting the process.
        suppressingOutput = true;
        try {
            sendLine(":reset");
            awaitQuietly(EVAL_TIMEOUT_MS);
            variables.clear();
        } finally {
            synchronized (carry) {
                carry.setLength(0);
            }
            suppressingOutput = false;
        }
    }

    @Override
    public void stop() {
        // Ctrl-C interrupts the running snippet, as JLine reads it as a
        // user interrupt.
        if (writer != null) {
            try {
                writer.write(3);
                writer.flush();
            } catch (IOException ex) {
                logger.warn("Failed to interrupt the Scala REPL: {}", ex.getMessage());
            }
        }
    }

    @Override
    public boolean eval(String code, List<Object> values) {
        if (process == null || writer == null) {
            out.println("ERROR: the Scala kernel is not running.");
            out.flush();
            return false;
        }

        List<String> statements = splitStatements(code);
        if (statements.isEmpty()) {
            return true;
        }

        StringBuilder captured = new StringBuilder();
        for (int i = 0; i < statements.size(); i++) {
            String stmt = statements.get(i);
            if (dotty.tools.repl.ParseResult$.MODULE$.isIncomplete(stmt, parseCtx)) {
                process(List.of("The code is incomplete and swallowed by the REPL."));
                out.println("ERROR: incomplete code. The kernel has been restarted.");
                out.flush();
                restart();
                return false;
            }

            sendLine(stmt);
            String response = awaitQuietly(EVAL_TIMEOUT_MS);
            if (response == null) {
                process(List.of("The code is incomplete and swallowed by the REPL."));
                out.println("ERROR: incomplete code. The kernel has been restarted.");
                out.flush();
                restart();
                return false;
            }
            captured.append(response).append('\n');

            String error = detectError(response);
            if (error != null) {
                collectVariables(captured.toString());
                process(List.of(error));
                out.println("ERROR: " + error);
                out.flush();
                return false;
            }
        }

        collectVariables(captured.toString());
        return true;
    }

    /**
     * Splits code into complete executable statements using Scala's parser.
     *
     * @param code the source code.
     * @return the list of complete statements.
     */
    List<String> splitStatements(String code) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : code.split("\r?\n")) {
            if (current.isEmpty()) {
                if (line.isBlank() || line.trim().startsWith("//")) {
                    continue;
                }
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
            if (!dotty.tools.repl.ParseResult$.MODULE$.isIncomplete(current.toString(), parseCtx)) {
                statements.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    /**
     * Prints the results of the code evaluation to the console.
     * @param errors the errors detected during the code evaluation.
     */
    @Override
    public void process(List<String> errors) {
        for (String error : errors) {
            logger.error(error);
        }
    }

    @Override
    public List<Variable> variables() {
        return variables.entrySet().stream()
                .map(e -> new Variable(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * Writes a line to the scala-cli REPL.
     * @param line the line to evaluate.
     */
    private void sendLine(String line) {
        try {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (IOException ex) {
            logger.error("Failed to send code to the Scala REPL: {}", ex.getMessage());
        }
    }

    /**
     * Waits for the REPL prompt.
     * @param timeoutMs the maximum time to wait.
     * @return the output produced before the prompt, or null on timeout.
     */
    private String awaitPrompt(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        synchronized (lock) {
            while (true) {
                int index = pending.indexOf(PROMPT);
                if (index >= 0) {
                    String text = pending.substring(0, index);
                    pending.delete(0, index + PROMPT.length());
                    return text;
                }
                if (eof) {
                    return pending.toString();
                }
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return null;
                }
                lock.wait(Math.min(remaining, 200));
            }
        }
    }

    /**
     * Waits for the REPL prompt, absorbing an interruption.
     * @param timeoutMs the maximum time to wait.
     * @return the output produced before the prompt, or null on timeout.
     */
    private String awaitQuietly(long timeoutMs) {
        try {
            return awaitPrompt(timeoutMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Reads the process output, streams it to the cell, and records it for
     * consumption by {@link #eval}.
     */
    private void pump() {
        char[] buffer = new char[512];
        try (var reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)) {
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                String text = new String(buffer, 0, count);
                display(text);
                synchronized (lock) {
                    pending.append(text);
                    lock.notifyAll();
                }
            }
        } catch (IOException ex) {
            logger.warn("Scala REPL output stream closed: {}", ex.getMessage());
        } finally {
            synchronized (lock) {
                eof = true;
                lock.notifyAll();
            }
        }
    }

    /**
     * Streams output to the cell, dropping the REPL prompt and ANSI colors.
     * @param text the output received from the REPL.
     */
    private void display(String text) {
        if (!started || suppressingOutput) {
            return;
        }
        synchronized (carry) {
            carry.append(text);
            int start;
            while ((start = carry.indexOf(PROMPT)) >= 0) {
                int end = start;
                if (end > 0 && carry.charAt(end - 1) == '\n') {
                    end--;
                    if (end > 0 && carry.charAt(end - 1) == '\r') {
                        end--;
                    }
                }
                print(carry.substring(0, end));
                carry.delete(0, start + PROMPT.length());
            }

            // Keep the tail in case it is the beginning of a prompt (including preceding newline).
            int safe = carry.length() - (PROMPT.length() + 1);
            if (safe > 0) {
                print(carry.substring(0, safe));
                carry.delete(0, safe);
            }
        }
    }

    /**
     * Resolves the java executable for launching the child JVM.
     *
     * @return the java executable path.
     */
    private static String javaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            Path javaPath = Path.of(javaHome, "bin", OS.isWindows() ? "java.exe" : "java");
            if (Files.exists(javaPath)) {
                return javaPath.toString();
            }
        }
        return ProcessHandle.current().info().command().orElse("java");
    }

    /**
     * Loads the predef script used to initialize the REPL session.
     *
     * @return the predef script content, or empty string if not found.
     */
    private static String loadPredef() {
        String home = System.getProperty("smile.home", ".");
        List<Path> candidates = List.of(
                Path.of(home, "bin", "predef.sc"),
                Path.of(home, "studio", "src", "universal", "bin", "predef.sc"),
                Path.of("bin", "predef.sc"),
                Path.of("studio", "src", "universal", "bin", "predef.sc")
        );
        for (Path path : candidates) {
            if (Files.exists(path)) {
                try {
                    return Files.readString(path, StandardCharsets.UTF_8);
                } catch (IOException ex) {
                    logger.warn("Failed to read predef script from {}: {}", path, ex.getMessage());
                }
            }
        }

        try (InputStream in = ScalaKernel.class.getResourceAsStream("/bin/predef.sc")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            // ignore
        }

        return "";
    }

    /**
     * Prints text to the cell.
     * @param text the text to print.
     */
    private void print(String text) {
        if (!text.isEmpty()) {
            out.print(ANSI.matcher(text).replaceAll(""));
            out.flush();
        }
    }

    /**
     * Detects a compilation or runtime error in the REPL output.
     * @param text the output produced by the REPL.
     * @return a one-line description of the error, or null if there is none.
     */
    String detectError(String text) {
        Matcher errorMatcher = COMPILER_ERROR.matcher(text);
        if (errorMatcher.find() || COMPILER_SUMMARY.matcher(text).find()) {
            Matcher diagMatcher = COMPILER_DIAGNOSTIC.matcher(text);
            String detail;
            if (diagMatcher.find()) {
                detail = diagMatcher.group();
            } else if (COMPILER_ERROR_LINE.matcher(text).find()) {
                detail = "-- Error: --";
            } else {
                detail = "compilation failed";
            }
            return "the snippet failed to compile (" + detail + ")";
        }

        if (STACK_TRACE.matcher(text).find()) {
            Matcher matcher = RUNTIME_ERROR.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        } else {
            Matcher matcher = RUNTIME_ERROR_QUALIFIED.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        }

        return null;
    }

    /**
     * Records the declarations echoed by the REPL.
     * @param text the output produced by the REPL.
     */
    private void collectVariables(String text) {
        Matcher declarations = DECLARATION.matcher(text);
        while (declarations.find()) {
            String name = declarations.group(1);
            // res<N> holds the value of the last expression, not a variable.
            if (name.matches("res\\d+")) continue;
            variables.put(name, declarations.group(2).trim());
        }

        Matcher definitions = DEFINITION.matcher(text);
        while (definitions.find()) {
            variables.put(definitions.group(2), definitions.group(1));
        }
    }

    /**
     * Returns the classpath to expose to the REPL.
     *
     * <p>{@code java.class.path} is enough for the staged launcher, whose
     * launcher script puts the jars in {@code lib/*} on the classpath. When
     * running under a build tool or a test runner the classes are loaded by a
     * {@link URLClassLoader} instead, and {@code java.class.path} only holds
     * the runner itself, so the loader is consulted as well.
     *
     * @return the resolved classpath, or an empty string.
     */
    private String classpath() {
        Set<String> entries = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            addClasspathEntry(entries, entry);
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        while (loader != null) {
            if (loader instanceof URLClassLoader urlClassLoader) {
                for (URL url : urlClassLoader.getURLs()) {
                    addClasspathEntry(entries, urlToPath(url));
                }
            }
            loader = loader.getParent();
        }

        return entries.stream()
                .filter(entry -> !isKotlinClasspathEntry(entry))
                .collect(Collectors.joining(File.pathSeparator));
    }

    /**
     * Adds a classpath entry, expanding a trailing wildcard into the jars of
     * the directory.
     * @param entries the collected classpath entries.
     * @param entry a classpath entry, which may be empty or a wildcard.
     */
    private void addClasspathEntry(Set<String> entries, String entry) {
        if (entry == null || entry.isBlank()) return;
        if (!entry.endsWith("*")) {
            if (!isKotlinClasspathEntry(entry)) {
                entries.add(entry);
            }
            return;
        }

        // The staged launcher uses lib/*, which the child process would not
        // expand. Enumerate the jars ourselves.
        Path directory = Path.of(entry.substring(0, entry.length() - 1));
        if (!Files.isDirectory(directory)) return;
        try (var jars = Files.list(directory)) {
            jars.filter(p -> p.toString().endsWith(".jar"))
                .map(Path::toString)
                .filter(path -> !isKotlinClasspathEntry(path))
                .sorted()
                .forEach(entries::add);
        } catch (IOException ex) {
            logger.warn("Failed to expand classpath wildcard {}: {}", entry, ex.getMessage());
        }
    }

    /**
     * Returns true if the given classpath entry path belongs to smile-kotlin
     * or Kotlin compiler/scripting tooling jars.
     * @param path the classpath entry path.
     * @return true if the entry belongs to Kotlin.
     */
    public static boolean isKotlinClasspathEntry(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/');
        if (normalized.contains("!")) {
            normalized = normalized.substring(0, normalized.indexOf('!'));
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("smile-kotlin") ||
            lower.contains("kotlin-compiler-embeddable") ||
            lower.contains("kotlin-daemon-embeddable") ||
            lower.contains("kotlin-scripting-compiler-embeddable") ||
            lower.contains("kotlin-scripting-compiler-impl-embeddable") ||
            lower.contains("kotlin-scripting-jvm-host") ||
            lower.contains("kotlin-build-tools-api")) {
            return true;
        }
        if (normalized.matches("(?i).*/kotlin/(build/classes|bin|target)(/.*)?")) {
            return true;
        }
        return false;
    }

    /**
     * Converts a classloader URL into a classpath entry.
     * @param url the URL of a classpath entry.
     * @return the file path, or null if the URL is not a file.
     */
    private String urlToPath(URL url) {
        if (!"file".equals(url.getProtocol())) return null;
        try {
            return Path.of(url.toURI()).toString();
        } catch (URISyntaxException ex) {
            return url.getPath();
        }
    }
}
