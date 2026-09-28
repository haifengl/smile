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

/**
 * Scala code execution engine.
 *
 * <p>It drives {@code scala-cli repl} as a separate process and communicates
 * with it through standard input and output, in the same way
 * {@link PythonKernel} drives {@code ipython}. Compared with the JSR-223
 * based {@link ScriptKernel}, running Scala in its own process gives us the
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
 * the SMILE API just like the other kernels. This can be slow on first use,
 * as scala-cli downloads the compiler and dependencies.
 *
 * <p>Requires {@code scala-cli} on the {@code PATH}.
 *
 * @author Haifeng Li
 */
public class ScalaKernel extends Kernel<String> {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ScalaKernel.class);
    /** The localized strings of the kernel. */
    private static final ResourceBundle bundle = ResourceBundle.getBundle(ScalaKernel.class.getName(), Locale.getDefault());
    /** The Scala REPL prompt, which marks the completion of an evaluation. */
    private static final String PROMPT = "scala> ";
    /** ANSI escape sequences, which scala-cli emits even with --color never. */
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    /** The start of a Scala 3 compiler diagnostic, e.g. "-- [E006] Not Found Error:". */
    private static final Pattern COMPILER_DIAGNOSTIC = Pattern.compile("-- \\[E\\d+\\]");
    /** The compiler summary line, e.g. "1 error found". */
    private static final Pattern COMPILER_SUMMARY = Pattern.compile("^ *\\d+ (error|warning)s? found", Pattern.MULTILINE);
    /** An uncaught exception raised by the snippet. */
    private static final Pattern RUNTIME_ERROR =
            Pattern.compile("^(?:[\\w$.]+\\.)?\\w*(?:Exception|Error|Throwable)(?:: .*)?$", Pattern.MULTILINE);
    /** A value or variable declaration echoed by the REPL. */
    private static final Pattern DECLARATION =
            Pattern.compile("^(?:val|var) (\\w+): (.+?) = ", Pattern.MULTILINE);
    /** A class, trait, object, enum or def definition echoed by the REPL. */
    private static final Pattern DEFINITION =
            Pattern.compile("^// defined (?:case )?(\\w+) (\\w+)", Pattern.MULTILINE);

    /** How long to wait for the first prompt, including compiler download. */
    private static final long STARTUP_TIMEOUT_MS = 10 * 60 * 1000L;
    /** How long to wait for a prompt before treating an intermediate line as continuation. */
    private static final long CONTINUATION_TIMEOUT_MS = 1000L;
    /** How long to wait for a prompt before treating the line as incomplete. */
    private static final long EVAL_TIMEOUT_MS = 3 * 60 * 1000L;

    /** Print REPL output to the output area. */
    private final PrintWriter out = new PrintWriter(console, true, StandardCharsets.UTF_8);
    /** The variables declared in the session. */
    private final Map<String, String> variables = new LinkedHashMap<>();

    /** scala-cli process. */
    private volatile Process process;
    /** Send commands to the scala-cli process's input. */
    private BufferedWriter writer;
    /** The thread that pumps the process's output. */
    private Thread pump;
    /** Guards {@link #pending}, {@link #promptSeen} and {@link #eof}. */
    private final Object lock = new Object();
    /** Output received but not yet consumed by {@link #eval}. */
    private final StringBuilder pending = new StringBuilder();
    /** Whether the pump reached the end of the process output. */
    private boolean eof = false;
    /** Holds back a possible partial prompt between reads. */
    private final StringBuilder carry = new StringBuilder();

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
            command.add("scala-cli");
            command.add("repl");
            command.add("--color");
            command.add("never");
            String classpath = classpath();
            if (!classpath.isEmpty()) {
                command.add("--classpath");
                command.add(classpath);
            }
            String home = System.getProperty("smile.home");
            if (home != null && !home.isBlank()) {
                command.add("--java-opt");
                command.add("-Dsmile.home=" + home);
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            // scala-cli draws progress bars and JLine may probe the terminal;
            // both would corrupt the output we parse.
            builder.environment().put("COURSIER_PROGRESS", "false");
            builder.environment().put("TERM", "dumb");
            builder.redirectErrorStream(true);
            process = builder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            synchronized (lock) {
                pending.setLength(0);
                eof = false;
            }
            carry.setLength(0);
            variables.clear();

            pump = new Thread(this::pump, "scala-cli-output");
            pump.setDaemon(true);
            pump.start();

            // Consume the banner and the first prompt.
            if (awaitPrompt(STARTUP_TIMEOUT_MS) == null) {
                logger.error("Timed out waiting for the Scala REPL to start.");
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
        sendLine(":reset");
        awaitQuietly(EVAL_TIMEOUT_MS);
        variables.clear();
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

        StringBuilder captured = new StringBuilder();
        List<String> lines = new ArrayList<>();
        for (String line : code.split("\r?\n")) {
            // Blank lines would be echoed back as an empty prompt by the REPL.
            if (!line.isBlank()) lines.add(line);
        }

        for (int i = 0; i < lines.size(); i++) {
            sendLine(lines.get(i));
            long timeout = (i == lines.size() - 1) ? EVAL_TIMEOUT_MS : CONTINUATION_TIMEOUT_MS;
            String response = awaitQuietly(timeout);
            if (response == null) {
                if (i == lines.size() - 1) {
                    // The last line never produced a prompt: the code is
                    // incomplete. Restart to guarantee a clean state, as the
                    // REPL is still waiting for the rest of the block and
                    // would swallow the following cells.
                    process(List.of("The code is incomplete and swallowed by the REPL."));
                    out.println("ERROR: incomplete code. The kernel has been restarted.");
                    out.flush();
                    restart();
                } else {
                    // The line opened a block (e.g. "class Foo {" or a
                    // method signature) whose continuation is on the next
                    // line, which is the normal multi-line case. If the code
                    // never closes the block, the pending output is reported
                    // as an error.
                    logger.debug("Line {} awaits a continuation.", i + 1);
                }
                continue;
            }
            captured.append(response).append('\n');
        }

        String text = captured.toString();
        String error = detectError(text);
        if (error != null) {
            process(List.of(error));
            out.println("ERROR: " + error);
            out.flush();
            return false;
        }

        collectVariables(text);
        return true;
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
                synchronized (lock) {
                    pending.append(text);
                    lock.notifyAll();
                }
                display(text);
            }
        } catch (IOException ex) {
            logger.warn("scala-cli output stream closed: {}", ex.getMessage());
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
        carry.append(text);
        int start;
        while ((start = carry.indexOf(PROMPT)) >= 0) {
            print(carry.substring(0, start));
            carry.delete(0, start + PROMPT.length());
        }

        // Keep the tail in case it is the beginning of a prompt.
        int safe = carry.length() - (PROMPT.length() - 1);
        if (safe > 0) {
            print(carry.substring(0, safe));
            carry.delete(0, safe);
        }
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
    private String detectError(String text) {
        if (COMPILER_DIAGNOSTIC.matcher(text).find() || COMPILER_SUMMARY.matcher(text).find()) {
            Matcher matcher = COMPILER_DIAGNOSTIC.matcher(text);
            String detail = matcher.find() ? matcher.group() : "compilation failed";
            return "the snippet failed to compile (" + detail + ")";
        }

        Matcher matcher = RUNTIME_ERROR.matcher(text);
        if (matcher.find()) {
            return matcher.group();
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
     * Returns true if the given classpath entry path belongs to smile-kotlin.
     * @param path the classpath entry path.
     * @return true if the entry belongs to smile-kotlin.
     */
    public static boolean isKotlinClasspathEntry(String path) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/');
        if (normalized.contains("!")) {
            normalized = normalized.substring(0, normalized.indexOf('!'));
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("smile-kotlin")) {
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
