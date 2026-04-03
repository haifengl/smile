/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.util.lsp;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.util.OS;

/**
 * An LSP (Language Server Protocol) service that manages the full lifecycle
 * of a language server process and exposes high-level navigation and search
 * operations.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct a {@code LanguageService} with the server launch command
 *       and the workspace root directory.</li>
 *   <li>Call {@link #start()} to launch the server process, connect the
 *       LSP4J launcher over the process's stdin/stdout, and perform the
 *       LSP {@code initialize} / {@code initialized} handshake.</li>
 *   <li>Use the navigation methods.</li>
 *   <li>Call {@link #close()} (or use try-with-resources) to send the
 *       LSP {@code shutdown} + {@code exit} messages and terminate the
 *       server process.</li>
 * </ol>
 *
 * <h2>All position parameters are 1-based</h2>
 * <p>The {@code line} and {@code character} parameters accepted by every
 * operation use the same 1-based numbering displayed in editors.
 * The client converts them to the 0-based coordinates required by the LSP
 * specification transparently.
 *
 * <h2>Thread safety</h2>
 * <p>Each operation method may be called from any thread after {@link #start()}
 * returns.  Internally, all calls are forwarded over the single LSP4J channel
 * whose threading is managed by the LSP4J message reader/writer threads.
 *
 * @author Haifeng Li
 */
public class LanguageService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LanguageService.class);

    /** Default timeout for individual LSP request futures (seconds). */
    private static final int REQUEST_TIMEOUT_SECONDS = 30;
    /** Available language services, keyed by language ID. */
    private static final Map<String, LanguageService> services = new HashMap<>();

    /** The command used to launch the language server. */
    private final List<String> command;
    /** The workspace root directory. */
    private final Path workspaceRoot;
    /** Optional extra timeout override for slow servers. */
    private final int timeoutSeconds;

    /** The running language server process. */
    private Process serverProcess;
    /** The LSP4J server stub — all RPC calls go through this. */
    private LanguageServer server;
    /**
     * The LSP4J launcher that owns the JSON-RPC message pump threads.
     * Kept as a field to prevent premature garbage collection of the
     * background reader/writer threads.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private Launcher<LanguageServer> launcher;
    /** True once {@link #start()} has completed the initialize handshake. */
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    /** Guards {@link #start()} and {@link #close()} against concurrent invocations. */
    private final Object lifecycleLock = new Object();

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code LanguageService} using a pre-parsed command list.
     *
     * @param command       the command and arguments used to start the
     *                      language server process (e.g.
     *                      {@code ["java", "-jar", "jdt-ls.jar", ...]}).
     * @param workspaceRoot the workspace root directory sent to the server
     *                      in the {@code initialize} request.
     */
    public LanguageService(List<String> command, Path workspaceRoot) {
        this(command, workspaceRoot, REQUEST_TIMEOUT_SECONDS);
    }

    /**
     * Creates a {@code LanguageService} with a custom per-request timeout.
     *
     * @param command          the command used to start the language server.
     * @param workspaceRoot    the workspace root directory.
     * @param timeoutSeconds   how many seconds to wait for each LSP response
     *                         before throwing a {@link TimeoutException}.
     */
    public LanguageService(List<String> command, Path workspaceRoot, int timeoutSeconds) {
        this.command        = List.copyOf(Objects.requireNonNull(command, "command"));
        this.workspaceRoot  = Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Returns the service for the given language.
     * @param lang the language ID (e.g. "java", "python").
     * @return the corresponding {@code LanguageService}, or {@code null} if not found.
     */
    public static LanguageService get(String lang) {
        return services.get(lang);
    }

    /**
     * Registers a {@code LanguageService} for the given language ID.
     * @param lang the language ID (e.g. "java", "python").
     * @param service the service to register.
     */
    public static void put(String lang, LanguageService service) {
        services.put(lang, service);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    /**
     * Launches the language server process, connects the LSP4J launcher over
     * the process's stdin/stdout, and performs the LSP
     * {@code initialize} / {@code initialized} handshake.
     * The server may send notifications (e.g. publishDiagnostics) back
     * to the client. This overload uses a no-op LSP4J client implementation
     * that log all server notifications at DEBUG or INFO level.
     *
     * <p>This method blocks until the server has acknowledged the
     * {@code initialize} request.
     *
     * @throws IOException          if the server process cannot be started.
     * @throws InterruptedException if the thread is interrupted while waiting
     *                              for the initialize response.
     * @throws ExecutionException   if the initialize request fails.
     * @throws TimeoutException     if the server does not respond within the
     *                              configured timeout.
     */
    public void start()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        start(new NoOpLanguageClientStub());
    }

    /**
     * Launches the language server process, connects the LSP4J launcher over
     * the process's stdin/stdout, and performs the LSP
     * {@code initialize} / {@code initialized} handshake.
     *
     * <p>This method blocks until the server has acknowledged the
     * {@code initialize} request.
     *
     * @param client an implementation of LSP4J's {@code LanguageClient} interface
     *               to receive server notifications.
     * @throws IOException          if the server process cannot be started.
     * @throws InterruptedException if the thread is interrupted while waiting
     *                              for the initialize response.
     * @throws ExecutionException   if the initialize request fails.
     * @throws TimeoutException     if the server does not respond within the
     *                              configured timeout.
     */
    public void start(LanguageClient client)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        synchronized (lifecycleLock) {
            if (initialized.get()) {
                logger.warn("LanguageService is already started; ignoring duplicate start().");
                return;
            }
            logger.info("Starting language server: {}", command);

            // Launch the server process.  stdin/stdout are inherited as
            // PIPE so the LSP4J launcher can wire them up directly.
            serverProcess = new ProcessBuilder(command)
                    .redirectErrorStream(false)   // keep stderr separate
                    .start();

            // Drain stderr in the background so the server is never blocked on it.
            Thread.ofPlatform().name("lsp-stderr-drain").start(() -> {
                try (var reader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[LSP-ERR] {}", line);
                    }
                } catch (IOException ex) {
                    logger.warn("LSP stderr drain error", ex);
                }
            });

            // Wire the LSP4J launcher to the process streams.
            // The launcher starts two internal threads: one to read JSON-RPC
            // messages from stdout and one to dispatch them.
            launcher = LSPLauncher.createClientLauncher(
                    client,
                    serverProcess.getInputStream(),
                    serverProcess.getOutputStream());
            launcher.startListening();
            server = launcher.getRemoteProxy();

            // Send the LSP initialize request.
            sendInitialize();

            initialized.set(true);
            logger.info("Language server ready. Workspace: {}", workspaceRoot);
        }
    }

    /**
     * Sends the LSP {@code initialize} + {@code initialized} handshake.
     */
    private void sendInitialize()
            throws InterruptedException, ExecutionException, TimeoutException {
        var params = new InitializeParams();
        params.setRootUri(workspaceRoot.toUri().toString());
        params.setRootPath(workspaceRoot.toString());
        params.setProcessId((int) ProcessHandle.current().pid());
        params.setClientInfo(new ClientInfo("smile-lsp-client", "1.0"));

        // Declare the client capabilities we rely on.
        var textDocCaps   = new TextDocumentClientCapabilities();
        var defCap        = new DefinitionCapabilities();
        defCap.setLinkSupport(false);
        textDocCaps.setDefinition(defCap);

        var implCap = new ImplementationCapabilities();
        implCap.setLinkSupport(false);
        textDocCaps.setImplementation(implCap);

        var refCap = new ReferencesCapabilities();
        textDocCaps.setReferences(refCap);

        var hoverCap = new HoverCapabilities();
        hoverCap.setContentFormat(List.of(MarkupKind.PLAINTEXT, MarkupKind.MARKDOWN));
        textDocCaps.setHover(hoverCap);

        var docSymCap = new DocumentSymbolCapabilities();
        docSymCap.setHierarchicalDocumentSymbolSupport(false);
        textDocCaps.setDocumentSymbol(docSymCap);

        var callHierCap = new CallHierarchyCapabilities();
        callHierCap.setDynamicRegistration(false);
        textDocCaps.setCallHierarchy(callHierCap);

        var wsCaps    = new WorkspaceClientCapabilities();
        var symCap    = new SymbolCapabilities();
        wsCaps.setSymbol(symCap);

        var caps = new ClientCapabilities();
        caps.setTextDocument(textDocCaps);
        caps.setWorkspace(wsCaps);
        params.setCapabilities(caps);

        // initialize is the very first request — block until it completes.
        InitializeResult result = server.initialize(params)
                .get(timeoutSeconds, TimeUnit.SECONDS);
        logger.debug("Server capabilities: {}", result.getCapabilities());

        // Notify the server that the client is ready.
        server.initialized(new InitializedParams());
    }

    /**
     * Sends the LSP {@code shutdown} request followed by the {@code exit}
     * notification, then forcibly terminates the server process if it is
     * still running.
     */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (!initialized.get()) return;
            initialized.set(false);
            try {
                server.shutdown().get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (Exception ex) {
                logger.warn("LSP shutdown request failed", ex);
            }
            server.exit();
            if (serverProcess != null && serverProcess.isAlive()) {
                serverProcess.destroy();
                try {
                    if (!serverProcess.waitFor(5, TimeUnit.SECONDS)) {
                        serverProcess.destroyForcibly();
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            logger.info("Language server stopped.");
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Ensures the client has been started; throws if not.
     */
    private void checkStarted() {
        if (!initialized.get()) {
            throw new IllegalStateException(
                    "LanguageService has not been started. Call start() first.");
        }
    }

    /**
     * Converts a file path to a {@code file://} URI string.
     *
     * @param filePath the file path (absolute or resolvable against the workspace).
     * @return the URI string.
     */
    private static String toUri(String filePath) {
        Path path = Path.of(filePath);
        if (!path.isAbsolute()) {
            path = path.toAbsolutePath();
        }
        return path.toUri().toString();
    }

    /**
     * Converts a 1-based (line, character) pair to an LSP4J {@link Position}
     * (0-based).
     *
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the LSP4J {@code Position}.
     */
    private static Position toPosition(int line, int character) {
        return new Position(line - 1, character - 1);
    }

    /**
     * Blocks on a {@link CompletableFuture} with the configured timeout,
     * translating checked exceptions to unchecked ones for ergonomic use
     * in operation methods.
     *
     * @param <T>    the result type.
     * @param future the future to await.
     * @return the result.
     * @throws LspException wrapping the underlying cause on failure.
     */
    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LspException("LSP request interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            String msg = cause != null ? cause.getMessage() : ex.getMessage();
            throw new LspException("LSP request failed: " + msg, cause != null ? cause : ex);
        } catch (TimeoutException ex) {
            throw new LspException(
                    "LSP request timed out after " + timeoutSeconds + " seconds", ex);
        }
    }

    /**
     * Builds a {@link TextDocumentIdentifier} for the given file path.
     *
     * @param filePath the file path.
     * @return the text document identifier.
     */
    private static TextDocumentIdentifier textDoc(String filePath) {
        return new TextDocumentIdentifier(toUri(filePath));
    }


    // -----------------------------------------------------------------------
    // Navigation operations
    // -----------------------------------------------------------------------

    /**
     * Finds the definition location(s) of the symbol at the given position.
     *
     * <p>Corresponds to the LSP {@code textDocument/definition} request.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the list of definition locations; empty if none found.
     * @throws LspException if the request fails or times out.
     */
    public List<LspLocation> goToDefinition(String filePath, int line, int character) {
        checkStarted();
        logger.debug("goToDefinition {}:{}:{}", filePath, line, character);

        var params = new DefinitionParams(textDoc(filePath), toPosition(line, character));
        var result = await(server.getTextDocumentService().definition(params));
        if (result == null) return List.of();

        return result.isLeft()
                ? result.getLeft().stream().map(LspLocation::fromProtocol).toList()
                : result.getRight().stream()
                        .filter(ll -> ll.getTargetUri() != null || ll.getTargetRange() != null)
                        .map(ll -> {
                            String uri = ll.getTargetUri() != null
                                    ? ll.getTargetUri() : toUri(filePath);
                            org.eclipse.lsp4j.Range range = ll.getTargetRange() != null
                                    ? ll.getTargetRange() : ll.getTargetSelectionRange();
                            return LspLocation.fromProtocol(new Location(uri, range));
                        })
                        .toList();
    }

    /**
     * Finds all references to the symbol at the given position.
     *
     * <p>Corresponds to the LSP {@code textDocument/references} request.
     * The definition location is included in the results when
     * {@code includeDeclaration} is {@code true}.
     *
     * @param filePath           the absolute file path to query.
     * @param line               the 1-based line number.
     * @param character          the 1-based character offset.
     * @param includeDeclaration whether to include the declaration site.
     * @return the list of reference locations; empty if none found.
     * @throws LspException if the request fails or times out.
     */
    public List<LspLocation> findReferences(
            String filePath, int line, int character, boolean includeDeclaration) {
        checkStarted();
        logger.debug("findReferences {}:{}:{} includeDeclaration={}",
                filePath, line, character, includeDeclaration);

        var refCtx    = new ReferenceContext(includeDeclaration);
        var params    = new ReferenceParams(textDoc(filePath),
                toPosition(line, character), refCtx);
        var locations = await(server.getTextDocumentService().references(params));
        if (locations == null) return List.of();
        return locations.stream().map(LspLocation::fromProtocol).toList();
    }

    /**
     * Finds all references to the symbol at the given position, including the
     * declaration site.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the list of reference locations; empty if none found.
     * @throws LspException if the request fails or times out.
     */
    public List<LspLocation> findReferences(String filePath, int line, int character) {
        return findReferences(filePath, line, character, true);
    }

    /**
     * Returns hover information (documentation, type info) for the symbol at
     * the given position.
     *
     * <p>Corresponds to the LSP {@code textDocument/hover} request.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the hover text, or an empty string if no hover info is available.
     * @throws LspException if the request fails or times out.
     */
    public String hover(String filePath, int line, int character) {
        checkStarted();
        logger.debug("hover {}:{}:{}", filePath, line, character);

        var params = new HoverParams(textDoc(filePath), toPosition(line, character));
        var result = await(server.getTextDocumentService().hover(params));
        if (result == null || result.getContents() == null) return "";

        var contents = result.getContents();
        if (contents.isLeft()) {
            // List<Either<String, MarkedString>>
            var parts = contents.getLeft();
            if (parts == null || parts.isEmpty()) return "";
            var sb = new StringBuilder();
            for (var part : parts) {
                if (part.isLeft()) {
                    sb.append(part.getLeft());
                } else {
                    sb.append(part.getRight().getValue());
                }
                sb.append('\n');
            }
            return sb.toString().stripTrailing();
        } else {
            // MarkupContent
            return contents.getRight().getValue();
        }
    }

    /**
     * Returns all symbols defined in the given document.
     *
     * <p>Corresponds to the LSP {@code textDocument/documentSymbol} request.
     *
     * @param filePath the absolute file path to query.
     * @return the list of symbols; empty if the server returns nothing.
     * @throws LspException if the request fails or times out.
     */
    public List<LspSymbol> documentSymbol(String filePath) {
        checkStarted();
        logger.debug("documentSymbol {}", filePath);

        var params = new DocumentSymbolParams(textDoc(filePath));
        var result = await(server.getTextDocumentService().documentSymbol(params));
        if (result == null) return List.of();

        String uri = toUri(filePath);
        List<LspSymbol> symbols = new ArrayList<>();
        for (var either : result) {
            if (either.isLeft()) {
                symbols.add(LspSymbol.fromSymbolInformation(either.getLeft()));
            } else {
                flattenDocumentSymbol(either.getRight(), uri, null, symbols);
            }
        }
        return Collections.unmodifiableList(symbols);
    }

    /**
     * Recursively flattens a hierarchical {@link org.eclipse.lsp4j.DocumentSymbol} tree
     * into a flat list of {@link LspSymbol} instances.
     *
     * @param ds          the document symbol node to process.
     * @param uri         the document URI.
     * @param container   the name of the containing symbol, or {@code null}.
     * @param accumulator the list to collect results into.
     */
    private static void flattenDocumentSymbol(
            org.eclipse.lsp4j.DocumentSymbol ds, String uri,
            String container, List<LspSymbol> accumulator) {
        var range = ds.getRange();
        accumulator.add(new LspSymbol(
                ds.getName(),
                ds.getKind().getValue(),
                container,
                uri,
                range.getStart().getLine() + 1,
                range.getStart().getCharacter() + 1,
                range.getEnd().getLine() + 1,
                range.getEnd().getCharacter() + 1));
        var children = ds.getChildren();
        if (children != null) {
            for (var child : children) {
                flattenDocumentSymbol(child, uri, ds.getName(), accumulator);
            }
        }
    }

    /**
     * Searches for symbols whose names match the given query across the entire
     * workspace.
     *
     * <p>Corresponds to the LSP {@code workspace/symbol} request.
     *
     * @param query the search query string (may be empty to list all symbols).
     * @return the list of matching symbols; empty if the server returns nothing.
     * @throws LspException if the request fails or times out.
     */
    public List<LspSymbol> workspaceSymbol(String query) {
        checkStarted();
        logger.debug("workspaceSymbol query='{}'", query);

        var params = new WorkspaceSymbolParams(query);
        var result = await(server.getWorkspaceService().symbol(params));
        if (result == null) return List.of();

        return result.isLeft()
                ? result.getLeft().stream()
                        .map(LspSymbol::fromSymbolInformation)
                        .toList()
                : result.getRight().stream()
                        .map(ws -> {
                            var loc = ws.getLocation();
                            // WorkspaceSymbol may carry just a URI without a Range
                            LspLocation lspLoc = loc.isLeft()
                                    ? LspLocation.fromProtocol(loc.getLeft())
                                    : new LspLocation(loc.getRight().getUri(), 1, 1, 1, 1);
                            return new LspSymbol(
                                    ws.getName(),
                                    ws.getKind().getValue(),
                                    ws.getContainerName(),
                                    lspLoc.uri(),
                                    lspLoc.startLine(),
                                    lspLoc.startCharacter(),
                                    lspLoc.endLine(),
                                    lspLoc.endCharacter());
                        })
                        .toList();
    }

    /**
     * Finds all implementations of the interface or abstract method at the
     * given position.
     *
     * <p>Corresponds to the LSP {@code textDocument/implementation} request.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the list of implementation locations; empty if none found.
     * @throws LspException if the request fails or times out.
     */
    public List<LspLocation> goToImplementation(String filePath, int line, int character) {
        checkStarted();
        logger.debug("goToImplementation {}:{}:{}", filePath, line, character);

        var params = new ImplementationParams(textDoc(filePath), toPosition(line, character));
        var result = await(server.getTextDocumentService().implementation(params));
        if (result == null) return List.of();

        return result.isLeft()
                ? result.getLeft().stream().map(LspLocation::fromProtocol).toList()
                : result.getRight().stream()
                        .filter(ll -> ll.getTargetUri() != null || ll.getTargetRange() != null)
                        .map(ll -> {
                            String uri = ll.getTargetUri() != null
                                    ? ll.getTargetUri() : toUri(filePath);
                            org.eclipse.lsp4j.Range range = ll.getTargetRange() != null
                                    ? ll.getTargetRange() : ll.getTargetSelectionRange();
                            return LspLocation.fromProtocol(new Location(uri, range));
                        })
                        .toList();
    }

    /**
     * Resolves the call-hierarchy item at the given position.
     *
     * <p>This is the first step of a call-hierarchy query.  Pass the returned
     * items to {@link #incomingCalls(CallHierarchyItem)} or
     * {@link #outgoingCalls(CallHierarchyItem)} to traverse the hierarchy.
     *
     * <p>Corresponds to the LSP {@code textDocument/prepareCallHierarchy}
     * request.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the list of call-hierarchy items at the position; empty if none.
     * @throws LspException if the request fails or times out.
     */
    public List<CallHierarchyItem> prepareCallHierarchy(
            String filePath, int line, int character) {
        checkStarted();
        logger.debug("prepareCallHierarchy {}:{}:{}", filePath, line, character);

        var params = new CallHierarchyPrepareParams();
        params.setTextDocument(textDoc(filePath));
        params.setPosition(toPosition(line, character));

        var result = await(server.getTextDocumentService().prepareCallHierarchy(params));
        if (result == null) return List.of();
        return result.stream().map(CallHierarchyItem::fromProtocol).toList();
    }

    /**
     * Returns all callers of the given call-hierarchy item.
     *
     * <p>Corresponds to the LSP {@code callHierarchy/incomingCalls} request.
     *
     * @param item the call-hierarchy item to query (typically obtained from
     *             {@link #prepareCallHierarchy}).
     * @return the list of incoming calls; empty if none.
     * @throws LspException if the request fails or times out.
     */
    public List<CallHierarchyCall> incomingCalls(CallHierarchyItem item) {
        checkStarted();
        logger.debug("incomingCalls for {}", item.name());

        var params = new CallHierarchyIncomingCallsParams(item.toProtocol());
        var result = await(server.getTextDocumentService().callHierarchyIncomingCalls(params));
        if (result == null) return List.of();
        return result.stream().map(CallHierarchyCall::fromIncoming).toList();
    }

    /**
     * Returns all functions or methods called by the given call-hierarchy item.
     *
     * <p>Corresponds to the LSP {@code callHierarchy/outgoingCalls} request.
     *
     * @param item the call-hierarchy item to query (typically obtained from
     *             {@link #prepareCallHierarchy}).
     * @return the list of outgoing calls; empty if none.
     * @throws LspException if the request fails or times out.
     */
    public List<CallHierarchyCall> outgoingCalls(CallHierarchyItem item) {
        checkStarted();
        logger.debug("outgoingCalls for {}", item.name());

        var params = new CallHierarchyOutgoingCallsParams(item.toProtocol());
        var result = await(server.getTextDocumentService().callHierarchyOutgoingCalls(params));
        if (result == null) return List.of();
        return result.stream().map(CallHierarchyCall::fromOutgoing).toList();
    }

    // -----------------------------------------------------------------------
    // Convenience composite operations
    // -----------------------------------------------------------------------

    /**
     * Convenience method that combines {@link #prepareCallHierarchy} and
     * {@link #incomingCalls} into a single call.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the flat list of all incoming calls; empty if none.
     * @throws LspException if any request fails or times out.
     */
    public List<CallHierarchyCall> incomingCalls(String filePath, int line, int character) {
        return prepareCallHierarchy(filePath, line, character).stream()
                .flatMap(item -> incomingCalls(item).stream())
                .toList();
    }

    /**
     * Convenience method that combines {@link #prepareCallHierarchy} and
     * {@link #outgoingCalls} into a single call.
     *
     * @param filePath  the absolute file path to query.
     * @param line      the 1-based line number.
     * @param character the 1-based character offset.
     * @return the flat list of all outgoing calls; empty if none.
     * @throws LspException if any request fails or times out.
     */
    public List<CallHierarchyCall> outgoingCalls(String filePath, int line, int character) {
        return prepareCallHierarchy(filePath, line, character).stream()
                .flatMap(item -> outgoingCalls(item).stream())
                .toList();
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /**
     * Returns the workspace root directory used by this client.
     *
     * @return the workspace root.
     */
    public Path workspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Returns {@code true} if the client has been successfully started and
     * the initialize handshake has completed.
     *
     * @return {@code true} if ready.
     */
    public boolean isStarted() {
        return initialized.get();
    }

    // -----------------------------------------------------------------------
    // Factory methods for common language servers
    // -----------------------------------------------------------------------

    /**
     * Creates a {@code LanguageService} pre-configured to launch the Eclipse
     * JDT Language Server (JDT-LS).
     *
     * <p>The {@code jdtLsJar} path must point to the JDT-LS launcher JAR
     * ({@code plugins/org.eclipse.equinox.launcher_*.jar} inside the JDT-LS
     * installation directory).
     *
     * <p>The data directory is where JDT-LS stores its workspace index.
     * It must be writable and is typically a subdirectory of the user's
     * home directory.
     *
     * @param workspaceRoot the project root to analyze.
     * @param jdtLsJar      path to the JDT-LS equinox launcher JAR.
     * @param jdtLsHome     the JDT-LS installation directory (contains
     *                      {@code plugins/} and {@code config_linux/} etc.).
     * @param dataDir       writable directory for JDT-LS workspace data.
     * @return a configured (but not yet started) {@code LanguageService}.
     */
    public static LanguageService jdtLs(
            Path workspaceRoot, Path jdtLsJar, Path jdtLsHome, Path dataDir) {

        String osConfig = switch (System.getProperty("os.name", "").toLowerCase()) {
            case String s when s.contains("mac")  -> "config_mac";
            case String s when s.contains("win")  -> "config_win";
            default                               -> "config_linux";
        };

        List<String> cmd = List.of(
                "java",
                "-Declipse.application=org.eclipse.jdt.ls.core.id1",
                "-Dosgi.bundles.defaultStartLevel=4",
                "-Declipse.product=org.eclipse.jdt.ls.core.product",
                "-Dlog.level=ALL",
                "-noverify",
                "-Xmx1G",
                "--add-modules=ALL-SYSTEM",
                "--add-opens", "java.base/java.util=ALL-UNNAMED",
                "--add-opens", "java.base/java.lang=ALL-UNNAMED",
                "-jar", jdtLsJar.toString(),
                "-configuration", jdtLsHome.resolve(osConfig).toString(),
                "-data", dataDir.toString()
        );

        return new LanguageService(cmd, workspaceRoot);
    }

    /**
     * Creates a {@code LanguageService} for any language server that accepts
     * a simple {@code stdio} launch command (e.g. Pyright, TypeScript LS,
     * rust-analyzer, clangd).
     *
     * @param workspaceRoot the project root to analyze.
     * @param serverCommand the full launch command as a single string
     *                      (e.g. {@code "pyright-langserver --stdio"}).
     * @return a configured (but not yet started) {@code LanguageService}.
     */
    public static LanguageService of(Path workspaceRoot, String serverCommand) {
        return new LanguageService(OS.parse(serverCommand), workspaceRoot);
    }
}
