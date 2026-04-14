# SMILE Studio User Guide

> **SMILE Studio** is an integrated development environment (IDE) for machine learning and data science using the [SMILE](https://haifengl.github.io/) library. It combines an interactive notebook, an AI-powered agent panel, a file explorer, and a kernel explorer in a single, modern desktop application.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Starting SMILE Studio](#2-starting-smile-studio)
3. [Application Layout](#3-application-layout)
4. [Menus and Toolbar](#4-menus-and-toolbar)
5. [The Notebook](#5-the-notebook)
   - 5.1 [Opening and Creating Notebooks](#51-opening-and-creating-notebooks)
   - 5.2 [Supported File Formats](#52-supported-file-formats)
   - 5.3 [Cells](#53-cells)
   - 5.4 [Cell Types](#54-cell-types)
   - 5.5 [Running Code](#55-running-code)
   - 5.6 [AI Code Completion and Generation](#56-ai-code-completion-and-generation)
   - 5.7 [Saving Notebooks](#57-saving-notebooks)
   - 5.8 [Auto-Save](#58-auto-save)
   - 5.9 [External File Changes](#59-external-file-changes)
6. [Execution Kernels](#6-execution-kernels)
   - 6.1 [Java Kernel (JShell)](#61-java-kernel-jshell)
   - 6.2 [Scala Kernel](#62-scala-kernel)
   - 6.3 [Python Kernel (iPython)](#63-python-kernel-ipython)
   - 6.4 [Restarting and Stopping a Kernel](#64-restarting-and-stopping-a-kernel)
   - 6.5 [Magic Commands (Java Kernel)](#65-magic-commands-java-kernel)
7. [Project Explorer](#7-project-explorer)
8. [Kernel Explorer](#8-kernel-explorer)
   - 8.1 [Inspecting Variables](#81-inspecting-variables)
   - 8.2 [Saving Models](#82-saving-models)
   - 8.3 [Starting an Inference Service](#83-starting-an-inference-service)
9. [AI Agent Panel](#9-ai-agent-panel)
   - 9.1 [Clair the Analyst](#91-clair-the-analyst)
   - 9.2 [James the Java Guru](#92-james-the-java-guru)
   - 9.3 [Guido the Pythonista](#93-guido-the-pythonista)
   - 9.4 [Intent Types](#94-intent-types)
   - 9.5 [Slash Commands](#95-slash-commands)
   - 9.6 [Shell Commands](#96-shell-commands)
   - 9.7 [Long-Term Memory (SMILE.md)](#97-long-term-memory-smilemd)
   - 9.8 [Reasoning Effort](#98-reasoning-effort)
   - 9.9 [Auto-Compact](#99-auto-compact)
10. [Notepad](#10-notepad)
11. [Settings — AI Service Configuration](#11-settings--ai-service-configuration)
12. [Status Bar](#12-status-bar)
13. [Font Size](#13-font-size)
14. [MCP (Model Context Protocol) Integration](#14-mcp-model-context-protocol-integration)
15. [LSP (Language Server Protocol) Integration](#15-lsp-language-server-protocol-integration)
16. [Themes / Look-and-Feel](#16-themes--look-and-feel)
17. [Keyboard Shortcuts Reference](#17-keyboard-shortcuts-reference)
18. [Configuration Files](#18-configuration-files)
19. [Troubleshooting](#19-troubleshooting)

---

## 1. Overview

SMILE Studio brings together four major components in one window:

| Component | Description |
|-----------|-------------|
| **Notebook** | Interactive multi-cell editor supporting Java, Scala, and Python |
| **AI Agent Panel** | Conversation interface to AI-powered coding and data science agents |
| **Project (File) Explorer** | File tree of the current working directory |
| **Kernel Explorer** | Live view of runtime variables, models, and inference services |

All four panels are arranged in a resizable split-pane layout, and the application remembers which notebooks were open across sessions.

---

## 2. Starting SMILE Studio

Run SMILE Studio from the project's root directory so that relative paths in notebooks resolve correctly:

```bash
# Unix / macOS
cd /your/project
smile

# Windows
cd C:\your\project
smile
```

> **Note:** SMILE Studio must be started in a graphical (non-headless) environment. If the JVM is running in headless mode the application will print an error and exit.

### System Requirements

- Java 25+ (with `--enable-native-access=ALL-UNNAMED` for the Java kernel's JShell remote VM)
- Python 3.x with `ipython` installed for Python notebooks (`pip install ipython`)
- Scala engine (included via the JSR-223 script engine)
- At least 4 GB RAM; 8 GB recommended for large datasets

---

## 3. Application Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│  Menu Bar:  File  |  Cell  |  Help                                  │
│  Toolbar:  [New] [Open] [Save] [SaveAs] | [AddCell] [Run] [Clear]   │
│             [Restart] [Stop]                                        │
├──────────────────────────────┬──────────────────────────────────────┤
│  Explorer Tabs               │  Agent Tabs                          │
│  ┌──────────┬──────────┐     │  📊 Clair | ☕ James | 🐍 Guido     │
│  │ Project  │  Kernel  │     │                                      │
│  └──────────┴──────────┘     │  [Intent input / conversation area]  │
│                              │                                      │
│  File tree / Variable tree   ├──────────────────────────────────────┤
│                              │  Notebook Tabs                       │
│                              │  ┌─────────────────────────────────┐ │
│                              │  │  Cell 1: [▶][⏭][▾][↑][↓][🧹][⌦]│ │
│                              │  │  [code editor]                  │ │
│                              │  │  [output area]                  │ │
│                              │  ├─────────────────────────────────┤ │
│                              │  │  Cell 2 …                       │ │
│                              │  └─────────────────────────────────┘ │
├──────────────────────────────┴──────────────────────────────────────┤
│  Status Bar:  [status message]         [Heap: 512 MB  CPU: 12%]     │
└─────────────────────────────────────────────────────────────────────┘
```

- **Left split** – Project explorer (top) / Kernel explorer (bottom), switchable by tab.
- **Center split** – Notebook tabs.
- **Right split** – AI Agent tabs.
- **Bottom** – Status bar.

The divider positions are persisted and restored between sessions.

---

## 4. Menus and Toolbar

### File Menu

| Action | Description |
|--------|-------------|
| **New** | Create a new `Untitled.java` notebook |
| **Open…** | Open an existing notebook or script file |
| **Save** | Save the currently active notebook |
| **Save As…** | Save the active notebook to a new path |
| **Auto Save** | Toggle periodic auto-save (every 60 seconds) |
| **Settings…** | Open the AI service configuration dialog |
| **Exit** | Close all open notebooks (with save prompts) and quit |

### Cell Menu

| Action | Description |
|--------|-------------|
| **Add Cell** | Insert a new code cell after the currently focused one |
| **Run All** | Execute every cell in the active notebook sequentially |
| **Clear All** | Clear all cell outputs |
| **Restart Kernel** | Restart the execution engine (confirmation required) |
| **Stop** | Interrupt the currently running cell |

### Help Menu

| Action | Description |
|--------|-------------|
| **Tutorials** | Open the SMILE quickstart guide in the default browser |
| **JavaDocs** | Open the SMILE Java API documentation in the browser |
| **About** | Display version and licensing information |

---

## 5. The Notebook

### 5.1 Opening and Creating Notebooks

- **New** (toolbar or `File > New`) — opens a fresh `Untitled.java` notebook pre-populated with the standard SMILE import block.
- **Open…** (toolbar or `File > Open…`) — file chooser filtered to supported extensions.
- **Double-click** a supported file in the Project Explorer — opens it directly in the notebook.

Previously opened notebooks are restored automatically at startup from `.smile/studio.properties` in the working directory.

### 5.2 Supported File Formats

| Extension | Language | Notes |
|-----------|----------|-------|
| `.java` | Java | Multi-cell via `//--- CELL ---` separator |
| `.jsh` | Java (JShell snippet) | Same cell separator |
| `.scala` | Scala | Multi-cell via `//--- CELL ---` separator |
| `.sc` | Scala (Ammonite script) | Same as `.scala` |
| `.py` | Python | Multi-cell via `#--- CELL ---` separator |
| `.ipynb` | Jupyter Notebook | Cells read from / written back to the JSON format |

> **Jupyter compatibility:** When saving a `.ipynb` file, Studio writes back code cells, markdown cells, and raw cells. Cell outputs are not yet persisted to the `.ipynb` file.

### 5.3 Cells

Each cell is an independent editing unit consisting of:

- **Header toolbar** with action buttons (see below)
- **Code editor** – syntax-highlighted, resizable, with code folding
- **Output area** – appears below the editor after execution; supports Markdown rendering and clickable hyperlinks

#### Cell Header Buttons

| Button | Action |
|--------|--------|
| `▶` Run | Execute this cell; stay in the current cell |
| `⏭` Run Below | Execute this cell and all cells below sequentially |
| `▾` Collapse | Toggle hiding the output area |
| `↑` Move Up | Swap this cell with the one above |
| `↓` Move Down | Swap this cell with the one below |
| `🧹` Clear | Erase this cell's output |
| `⌦` Delete | Remove this cell (if only one cell remains, clears content instead) |
| **Type** combobox | Switch between Code / Markdown / Raw cell type |
| **Prompt** field | Describe what you want and press Enter to generate code via AI |

### 5.4 Cell Types

| Type | Behavior |
|------|----------|
| **Code** | Executed by the kernel; output displayed below |
| **Markdown** | Rendered as styled HTML when cell is executed |
| **Raw** | Plain text, not executed or rendered |

### 5.5 Running Code

| Shortcut | Behavior |
|----------|----------|
| `Ctrl + Enter` | Run cell, keep focus in current cell |
| `Shift + Enter` | Run cell, move focus to next cell (or create a new one) |
| `Alt + Enter` | Run cell, insert a new cell immediately below |
| Toolbar **Run All** | Execute all cells top-to-bottom |

Execution is asynchronous; the UI remains responsive while code runs. A second run request while code is executing shows a warning dialog rather than allowing concurrent execution.

Variable values are printed automatically after each successful snippet evaluation:

```
⇒ DataFrame df = [200 rows × 5 columns]
```

Errors are highlighted in the output area with pink highlighting.

### 5.6 AI Code Completion and Generation

Both features require an AI service to be configured in **Settings**.

#### TAB — Line Completion

Press `Tab` at any non-first line to trigger single-line AI completion. The AI uses the surrounding code context (previous cell + current cell) to suggest a completion. If a completion is in progress, `Tab` inserts a literal tab character instead.

#### Prompt Field — Code Generation

1. Type a natural language description in the **Prompt** text field at the top of a cell (e.g., *"load iris.csv into a DataFrame"*).
2. Press **Enter**.
3. The AI streams generated code directly into the editor, with the task description included as a comment.

The generation context includes the text of the previous cell and the existing content of the current cell.

### 5.7 Saving Notebooks

- `Ctrl + S` equivalent → **File > Save**
- **File > Save As…** prompts for a new path. If no extension is given, `.java` is appended.
- The tab title reflects the filename. Unsaved changes are tracked internally; a save-before-close prompt appears when closing a tab.

### 5.8 Auto-Save

Enable **File > Auto Save** to automatically save all open notebooks that have unsaved changes every **60 seconds**. Only notebooks that have been previously saved to a file (i.e., have an associated path) are auto-saved; new `Untitled` notebooks are not.

### 5.9 External File Changes

If a file that is open in the notebook is modified by an external process (e.g., git checkout, another editor), Studio detects the change via a background file-watcher and prompts:

> *"'filename' has been changed externally. Reload?"*

Choosing **Yes** reloads the file from disk, preserving the tab's position. Choosing **No** keeps the current in-memory version.

---

## 6. Execution Kernels

### 6.1 Java Kernel (JShell)

The Java kernel uses the JDK's built-in `JShell` API running in a **separate JVM process**. This isolation means:

- The remote VM inherits the full application classpath (`java.class.path`).
- JVM flags set for the remote process: `-XX:MaxMetaspaceSize=1024M`, `-Xss4M`, `-XX:MaxRAMPercentage=75`, `-XX:+UseZGC`.
- `FlatLightLaf` is automatically initialized in the remote JVM so that `smile.plot.swing` charts display correctly.

#### Snippet Output Format

| Snippet type | Output |
|--------------|--------|
| Named variable | `⇒ TypeName varName = value` |
| Rejected snippet | `✖ Rejected snippet: …` |
| Recoverable issue | `⚠ Recoverable issue: …` |
| Exception | `ExceptionClassName: message` followed by stack trace |

#### New Java Notebook Starter Imports

When creating a new `.java` notebook, the first cell is pre-populated with:

```java
import java.awt.Color;
import java.time.*;
import java.util.*;
import static java.lang.Math.*;
import smile.plot.swing.*;
import static smile.swing.SmileUtilities.*;
import org.apache.commons.csv.CSVFormat;
import smile.io.*;
import smile.data.*;
import smile.data.formula.*;
// … and many more SMILE packages
```

### 6.2 Scala Kernel

The Scala kernel uses the JSR-223 `ScriptEngine` API (`"scala"` engine name). Output is captured by redirecting `System.out` and `System.err` around each evaluation. The Scala engine initialization is **deferred 5 seconds** at startup to avoid capturing LSP/MCP process output.

Returned values are printed as:
```
$result0: fully.qualified.Type = value
```

### 6.3 Python Kernel (iPython)

The Python kernel launches an **iPython REPL** subprocess (`ipython --simple-prompt --colors NoColor --no-banner --no-pdb`). Multi-line code is submitted via iPython's `%cpaste` magic. The kernel reads output lines and displays them in the output area.

**Prerequisite:** iPython must be installed:
```bash
pip install ipython
```

If iPython is not found, Studio shows an informational dialog with the install command.

### 6.4 Restarting and Stopping a Kernel

| Action | How |
|--------|-----|
| Restart | **Cell > Restart Kernel** or toolbar **↺** button. Requires confirmation. Clears all outputs. |
| Stop | **Cell > Stop** or toolbar **⛔** button. Sends an interrupt to the kernel. |

### 6.5 Magic Commands (Java Kernel)

Lines starting with `//!` inside a Java cell are intercepted before being sent to JShell. The currently supported magic is:

```java
//!mvn groupId:artifactId:version
```

This resolves the Maven dependency (transitively) via Eclipse Aether and adds the JARs to the JShell classpath at runtime, allowing you to use any Maven artifact without restarting the kernel.

**Example:**

```java
//!mvn org.apache.commons:commons-math3:3.6.1
```

---

## 7. Project Explorer

The **Project** tab in the left panel shows the file tree rooted at the current working directory.

- **Navigate** the tree by expanding folders.
- **Double-click** a supported source file (`.java`, `.jsh`, `.scala`, `.sc`, `.py`, `.ipynb`) to open it in the notebook.
- **Double-click** any other non-binary text file to open it in the **Notepad** editor.
- Binary files are silently ignored on double-click.

---

## 8. Kernel Explorer

The **Kernel** tab in the left panel shows a live tree of runtime objects tracked by the active notebook's kernel. Switch between notebooks in the tab strip to refresh the explorer automatically.

The tree has four top-level categories:

| Category | Contents |
|----------|----------|
| **DataFrames** | Variables whose type is `DataFrame` |
| **Matrix** | Variables whose type contains `[]` (arrays and matrices) |
| **Models** | Variables of ML model types (classifiers, regressors, etc.) |
| **Services** | Persisted model entries registered as inference services |

### 8.1 Inspecting Variables

Double-click any **DataFrames** or **Matrix** leaf node to open the variable in a SMILE viewer window:

```java
// Equivalent action generated by Studio:
var dfWindow = smile.swing.SmileUtilities.show(df);
dfWindow.setTitle("df");
```

### 8.2 Saving Models

Double-click a **Models** leaf node to open a **Save dialog**. Studio serializes the selected model variable using `smile.io.Write.object(model, path)` and saves it to a `.sml` file. After saving, if the model is a `ClassificationModel` or `RegressionModel`, it is automatically registered under the **Services** node with its schema.

### 8.3 Starting an Inference Service

Double-click a **Services** leaf node to open the **Start Service** dialog, which configures and launches a REST inference server for the saved model.

---

## 9. AI Agent Panel

The right panel hosts three AI agents, each in its own tab. All agents require an AI service to be configured (see [Section 11](#11-settings--ai-service-configuration)).

### 9.1 Clair the Analyst

> 📊 *Clair the Analyst* — end-to-end ML/AI assistant

Clair handles the complete data science workflow:

- Automatic ML/AI solutions from natural language requirements
- Data loading from CSV, ARFF, JSON, Avro, Parquet, Iceberg, SQL
- Advanced interactive data visualization
- Model training, evaluation, and ensembling
- Inference server management

### 9.2 James the Java Guru

> ☕ *James the Java Guru* — Java programming assistant

James helps with Java and SMILE-specific code:

- Code completion and generation in the notebook (via `Tab`)
- Reviewing and explaining Java code
- SMILE API guidance

### 9.3 Guido the Pythonista

> 🐍 *Guido the Pythonista* — Python programming assistant

Guido assists with Python notebooks:

- Code completion and generation
- Python data science library guidance

### 9.4 Intent Types

Each intent (conversation turn) has a **type selector** in the footer:

| Type | Legend | Description |
|------|--------|-------------|
| **Instructions** | `>` | Natural language prompt sent to the AI agent |
| **Command** | `/` | Slash command (see below) |
| **Shell** | `!` | Shell command executed in a subprocess |
| **Raw** | — | Display-only entry (not editable) |

Switch the intent type using the combo box, or use the shortcuts below.

### 9.5 Slash Commands

Type `/` followed by a command name and press `Ctrl + Enter`:

| Command | Description |
|---------|-------------|
| `/help` | List all available slash commands |
| `/memory show` | Display the long-term memory file (`SMILE.md`) |
| `/memory add <text>` | Append notes to `SMILE.md` |
| `/memory edit` | Open `SMILE.md` in a Notepad window |
| `/memory refresh` | Reload `SMILE.md` from disk into the agent context |
| `/plan <goal>` | Enter plan mode with a stated goal |
| `/plan off` | Exit plan mode |
| `/compact [instructions]` | Summarize the conversation and compact the context window |
| `/clear` | Clear the current conversation session |
| `/edit <file>` | Open a file in the Notepad editor |
| `/train` | Train a machine learning model (runs `smile train`) |
| `/predict` | Run batch inference (runs `smile predict`) |
| `/serve` | Start an inference service (runs `smile serve`) |

Additional custom slash commands can be defined as agent skills in `SMILE.md`.

**Hint window:** As you type a slash command, a hint tooltip appears showing the expected arguments.

### 9.6 Shell Commands

Set the intent type to **Shell** (or prefix your input with `!`) and enter any shell command. On Windows, commands run through `powershell.exe -Command`; on Unix/macOS, through `bash -c`. The output is streamed in real time to the output area. A **Stop** button appears to forcibly terminate long-running processes.

### 9.7 Long-Term Memory (SMILE.md)

Agents can maintain long-term, project-specific context stored in `SMILE.md` within the current working directory. This file is automatically loaded into the agent's system prompt. Use `/memory add` or `/memory edit` to update it.

**Initializing context:**
```
/init
```
Clair's `/init` skill creates a `SMILE.md` file by analysing the project and recording its structure, key decisions, and preferences.

### 9.8 Reasoning Effort

Each intent input shows a **Reasoning Effort** combo box. The available levels depend on the configured LLM:

| Level | Effect |
|-------|--------|
| *(default)* | Provider's default thinking behavior |
| `low` / `medium` / `high` | Explicit thinking budget (supported by Anthropic, OpenAI o-series, etc.) |

Increasing reasoning effort produces more careful responses at the cost of higher latency and token usage.

### 9.9 Auto-Compact

If a conversation session exceeds **180,000 tokens** (configurable via the system property `smile.agent.auto.compact`), the agent automatically runs `/compact` to summarize the conversation and free context window space.

---

## 10. Notepad

The **Notepad** is a standalone text editor window opened for non-notebook files. It is accessible via:

- Double-clicking a non-binary, non-source file in the Project Explorer
- The `/edit <file>` agent command
- The `/memory edit` command

### Features

| Feature | Description |
|---------|-------------|
| Syntax highlighting | Detected from file extension (Java, Python, Markdown, SQL, Scala, JSON, YAML, Shell, etc.) |
| Code folding | Enabled for all code languages |
| LSP auto-completion | Triggers on `.` for Java and Python files |
| Spell checking | English spell checker loaded from `data/eng_dic.zip` |
| Find / Replace | `Ctrl+F` (dialog), `Ctrl+Shift+F` (toolbar), `Ctrl+H` (replace dialog), `Ctrl+Shift+H` (replace toolbar) |
| Go To Line | Available in the Search menu |
| Error strip | Right-side gutter with error/warning markers |
| Unsaved change tracking | Prompts before close |

---

## 11. Settings — AI Service Configuration

Open via **File > Settings…**. Choose an AI service provider from the drop-down:

| Provider | Notes |
|----------|-------|
| **OpenAI** | GPT models; set API key, optional base URL override, model |
| **Azure OpenAI** | Legacy Azure endpoint; requires API key, base URL, model |
| **Anthropic** | Claude models; set API key, optional base URL, model |
| **Google Gemini** | Gemini models via native API; set API key, model |
| **Google Vertex AI** | Gemini via Vertex; set API key, base URL, model |

All fields (API key, base URL, model) are stored in Java `Preferences` (OS keychain / registry). API keys set in Settings take precedence over environment variables.

**Supported models** are listed as suggestions in each provider's combo box; you can also type any model name manually since the fields are editable.

After clicking **OK** the new LLM instance is initialized immediately.

> **Security note:** API keys are stored in the JVM `Preferences` store. On macOS this is the system Keychain; on Windows it is the Registry; on Linux it is `~/.java/.userPrefs`.

---

## 12. Status Bar

The status bar at the bottom of the window displays:

- **Left** – Status messages from current operations (kernel initialization, LSP startup, MCP connections, file saves, kernel restarts). Messages automatically reset to *"Ready"* after 60 seconds.
- **Right** – Live system metrics refreshed every second: JVM heap usage and CPU load percentage.

```
Ty server initialized                            Heap: 1.2 GB  CPU: 8%
```

---

## 13. Font Size

The monospaced font used in all code editors, output areas, and agent intent panes can be resized globally:

| Shortcut | Action |
|----------|--------|
| `Ctrl + =` | Increase font size |
| `Ctrl + -` | Decrease font size |

The Markdown rendering font size scales proportionally (by ±0.1 em per step).

---

## 14. MCP (Model Context Protocol) Integration

SMILE Studio automatically connects to MCP servers defined in any of the following configuration files at startup (in order):

1. `$SMILE_HOME/conf/mcp.json`
2. `~/.smile/mcp.json`
3. `.smile/mcp.json` (project-level, in current working directory)

All three files are loaded if they exist; tools from all connected servers become available to agents. Servers are gracefully shut down on application exit.

**Example `mcp.json`:**

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/your/project"]
    },
    "web-search": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-brave-search"],
      "env": { "BRAVE_API_KEY": "your_key_here" }
    }
  }
}
```

---

## 15. LSP (Language Server Protocol) Integration

SMILE Studio starts two language servers in the background:

| Server | Language | Provides |
|--------|----------|---------|
| **Ty** | Python | Type checking, diagnostics |
| **JDT LS** | Java | Completions, hover, diagnostics |

Both servers are started automatically. The `jdtls` binary is expected at `$SMILE_HOME/jdtls/bin/jdtls`.

Auto-completion is powered by the LSP providers and activates:

- **Automatically** after 300 ms of inactivity (configurable)
- **On `.`** — immediately triggers member completion for Java and Python

The completion popup is provided by RSyntaxTextArea's `AutoCompletion` infrastructure wired to a custom `LspCompletionProvider`.

---

## 16. Themes / Look-and-Feel

SMILE Studio uses [FlatLaf](https://www.formdev.com/flatlaf/) for its look-and-feel. The theme is stored in application preferences under the key `Theme`:

| Value | Description |
|-------|-------------|
| `Light` | FlatLaf Light (default on non-macOS) |
| `Dark` | FlatLaf Dark |
| `IntelliJ` | FlatLaf IntelliJ |
| `Darcula` | FlatLaf Darcula |
| `macLight` | FlatMac Light (default on macOS) |
| `macDark` | FlatMac Dark |

Change the theme by setting the `Theme` preference (e.g., programmatically or via a custom preference editor) and restarting the application.

JetBrains Mono is installed as the default monospaced font via `FlatJetBrainsMonoFont.install()`.

**macOS:** Full window content mode and transparent title bar are enabled automatically for a native look.

**Windows:** Per-monitor DPI scaling is disabled (`sun.java2d.uiScale=1.0`) to prevent blurry icons.

---

## 17. Keyboard Shortcuts Reference

### Notebook

| Shortcut | Action |
|----------|--------|
| `Ctrl + Enter` | Run cell, stay |
| `Shift + Enter` | Run cell, go to next / create new |
| `Alt + Enter` | Run cell, insert cell below |
| `Tab` | AI line completion (requires AI service) |
| `Ctrl + =` | Increase font size |
| `Ctrl + -` | Decrease font size |

### Notepad

| Shortcut | Action |
|----------|--------|
| `Ctrl + F` | Find dialog |
| `Ctrl + H` | Replace dialog |
| `Ctrl + Shift + F` | Find toolbar (inline) |
| `Ctrl + Shift + H` | Replace toolbar (inline) |
| `Ctrl + G` | Go to line |
| `Ctrl + S` | Save file |
| `.` | Trigger LSP auto-completion |

### Agent Panel

| Shortcut | Action |
|----------|--------|
| `Ctrl + Enter` | Execute current intent |

---

## 18. Configuration Files

| File / Location | Purpose |
|-----------------|---------|
| `.smile/studio.properties` (cwd) | List of previously opened file paths, restored at startup |
| `.smile/SMILE.md` or `SMILE.md` (cwd) | Agent long-term memory / project context |
| `$SMILE_HOME/conf/mcp.json` | Global MCP server definitions |
| `~/.smile/mcp.json` | User-level MCP server definitions |
| `.smile/mcp.json` (cwd) | Project-level MCP server definitions |
| Java `Preferences` node | AI service keys, selected theme, auto-save flag, Markdown font size |

---

## 19. Troubleshooting

### "Cannot start SMILE Studio as JVM is running in headless mode"

The display environment is not set. On Linux, ensure `DISPLAY` is set or run through a desktop session. On CI servers, use a virtual display (`Xvfb`).

### Python kernel fails to start

Ensure `ipython` is installed in the Python environment on `PATH`:

```bash
pip install ipython
python -c "import IPython; print(IPython.__version__)"
```

### Scala kernel produces no output

The Scala script engine needs the `smile.home` system property and the classpath to include Scala libraries. Ensure you are launching Studio via the provided `smile studio` script which sets these properties.

### JDT LS / Ty server doesn't start

Check that `$SMILE_HOME/jdtls/bin/jdtls` exists and is executable. Errors are logged to the application log and shown briefly in the status bar.

### AI features not working (Tab completion, code generation, agents)

Open **File > Settings…** and verify your AI provider credentials. Check the status bar for initialization errors. Ensure network access to the provider's API endpoint is available.

### Notebook not saving

If **Save** shows an error dialog, check file system permissions for the target path. `Untitled.java` notebooks must be saved via **Save As…** before auto-save takes effect.

### Variables not appearing in Kernel Explorer

The Kernel Explorer refreshes when you **switch notebook tabs**. Switch away and back, or run a cell to trigger a refresh. Only named (non-scratch) variables appear; JShell scratch variables (e.g., `$1`, `$2`) are excluded.

### Out-of-memory errors in JShell

The remote JVM is allocated up to 75% of the system RAM (`-XX:MaxRAMPercentage=75`). On memory-constrained machines, reduce this by restarting the kernel after modifying the heap settings via a `//!` magic in the first cell, or add JVM options in the launch script.

---

*SMILE Studio is free software under the GNU General Public License v3. For commercial use enquiries contact smile.sales@outlook.com.*

