# SMILE Studio User Guide

> **SMILE Studio** is an agentic IDE for data science using Python or [SMILE](https://www.aihalo.dev//) on JVM. It combines agentic AI, interactive notebooks, and workspace explorers in a modern desktop application. The launcher (`smile` / `smile.bat`) also exposes several command-line entry points. For the User Guide for CLI, see [CLI.md](CLI.md).

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
   - 9.1 [Frank the Chief of Staff](#91-frank-the-chief-of-staff)
   - 9.2 [Steve the Product Manager](#92-steve-the-product-manager)
   - 9.3 [Clair the Analyst](#93-clair-the-analyst)
   - 9.4 [Ada the Architect](#94-ada-the-architect)
   - 9.5 [James the Java Guru](#95-james-the-java-guru)
   - 9.6 [Guido the Pythonista](#96-guido-the-pythonista)
   - 9.7 [Chuck the Desktop Operator](#97-chuck-the-desktop-operator)
   - 9.8 [Intent Types](#98-intent-types)
   - 9.9 [Slash Commands](#99-slash-commands)
   - 9.10 [Shell Commands](#910-shell-commands)
   - 9.11 [Long-Term Memory (SMILE.md)](#911-long-term-memory-smilemd)
   - 9.12 [Reasoning Effort](#912-reasoning-effort)
   - 9.13 [Auto-Compact](#913-auto-compact)
10. [Notepad](#10-notepad)
11. [Settings — AI Service Configuration](#11-settings--ai-service-configuration)
12. [Status Bar](#12-status-bar)
13. [Font Size](#13-font-size)
14. [MCP (Model Context Protocol) Integration](#14-mcp-model-context-protocol-integration)
    - 14.1 [Web Search](#141-web-search)
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

Getting your first project up and running takes just a few minutes.
Follow these quick steps to set up your environment and start
interacting with your data with natural language.

1. **Download & Unzip**

Download the latest SMILE package from the
[latest release page](https://github.com/haifengl/smile/releases/latest)
(grab the `smile-<version>.zip` asset) and unzip it on your machine.

2. **Configure Your Environment**

Run the setup script to configure everything automatically:
```shell
/path/to/smile/bin/setup
```

3. **Prepare Your Project Directory**

Create a new directory for your work (e.g., `myproject`) and place your
datasets into the `myproject/input` folder.

4. **Launch SMILE Studio**

Navigate into your project folder and start the studio application:

```shell
cd myproject/
/path/to/smile/bin/smile
```

5. **Initialize & Prompt**

Once inside the studio, run `/init` to describe your project and goals.
From there, you can run `/automl`, use other slash commands, or simply type
out what you want to do in natural language!


> **Note:** SMILE Studio must be started in a graphical (non-headless) environment. If the JVM is running in headless mode the application will print an error and exit.

### System Requirements

- Java 25+ (with `--enable-native-access=ALL-UNNAMED` for the Java kernel's JShell remote VM)
- Python 3.x with `ipython` installed for Python notebooks (`pip install ipython`)
- Scala engine (included via the JSR-223 script engine)
- At least 4 GB RAM; 8 GB recommended for large datasets

---

## 3. Application Layout

```
┌───────────────────────────────────────────────────────────────────────────┐
│  Menu Bar:  File  |  Cell  |  Help                                        │
│  Toolbar:  [New] [Open] [Save] [SaveAs] | [AddCell] [Run] [Clear]         │
│             [Restart] [Stop]                                              │
├──────────────────────────────┬────────────────────────────────────────────┤
│  Explorer Tabs               │  Agent Tabs                                │
│  ┌──────────┬──────────┐     │  🤝 Frank | 🎯 Steve | 📊 Clair | 📐 Ada   │
│  │ Project  │  Kernel  │     │  ☕ James | 🐍 Guido | 🖥️ Chuck            │
│  └──────────┴──────────┘     │  [Intent input / conversation area]        │
│                              │                                            │
│  File tree / Variable tree   ├────────────────────────────────────────────┤
│                              │  Notebook Tabs                             │
│                              │  ┌─────────────────────────────────┐       │
│                              │  │  Cell 1: [▶][⏭][▾][↑][↓][🧹][⌦]│       │
│                              │  │  [code editor]                  │       │
│                              │  │  [output area]                  │       │
│                              │  ├─────────────────────────────────┤       │
│                              │  │  Cell 2 …                       │       │
│                              │  └─────────────────────────────────┘       │
├──────────────────────────────┴────────────────────────────────────────────┤
│  Status Bar:  [status message]         [Heap: 512 MB  CPU: 12%]           │
└───────────────────────────────────────────────────────────────────────────┘
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
| **Auto Save** | Toggle auto-save (idle-triggered, with a 60-second fallback) |
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

### Find Menu

| Action | Description |
|--------|-------------|
| **Find…** | Open the Find dialog for the selected tab (`Ctrl+F`) |
| **Replace…** | Open the Replace dialog for the selected tab (`Ctrl+H`) |

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

> **Jupyter compatibility:** When saving a `.ipynb` file, Studio writes back code cells, markdown cells, and raw cells along with cell execution counts, cell outputs, metadata, and attachments.

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

Enable **File > Auto Save** to automatically save every open file that has unsaved changes. Saving is **idle-triggered**: a few seconds after you stop editing (a change restarts a short debounce), the file is written. A repeating **60-second** tick remains as a safety net, bounding the worst-case data loss. Only files that have a path on disk are auto-saved; new `Untitled` notebooks are not. The setting is remembered across restarts.

### 5.9 External File Changes

If a file that is open in the notebook is modified by an external process (e.g., git checkout, another editor), Studio detects the change via a background file-watcher.

If the tab has **no unsaved edits**, Studio reloads it from disk immediately and reports the reload in the status bar — there is nothing to lose, so no dialog interrupts you.

If the tab **has unsaved edits**, Studio prompts before discarding them:

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
- **Double-click** a supported source file (`.jsh`, `.sc`, `.py`, `.ipynb`) to open it in the notebook.
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

Studio runs **one** inference service for the whole session, no matter how many
models you train. The **Services** node contains a single **Inference Service**
child; every saved model appears beneath it as a load candidate.

- **Double-click the Inference Service node** to open the **Start Service**
  dialog (host and port) and launch the service. The service starts empty.
- **Double-click a saved model** to load it into the running service, starting
  the service first if it is not already running. Loading a second model reuses
  the same JVM and port.

Models are loaded through `POST /api/v1/models/load`, so one process serves many
models instead of one process per model.

### 8.4 Inference Menu

The **Inference** menu manages the service lifecycle:

| Item | Action |
|------|--------|
| **Start Inference Service** | Start the service using the configured host and port |
| **Stop Inference Service** | Stop the service and release its resources |
| **Restart Inference Service** | Stop, then start again |
| **Health** | Open `/q/health` in the browser |
| **Metrics** | Open `/q/metrics` (Prometheus) in the browser |
| **Open Web UI** | Open the service's web interface |

### 8.5 Inference Server Configuration

The service can start automatically when Studio starts. Configure it in
`studio.json`, read from `$SMILE_HOME/conf/studio.json` or `~/.smile/studio.json`
(system-wide only — the project-local `./.smile/studio.json` is deliberately not
consulted, because a server binding is machine-wide):

```json
{
  "inferenceServer": {
    "autoStart": false,
    "host": "localhost",
    "port": 8888,
    "modelPath": "./model"
  }
}
```

| Field | Default | Meaning |
|-------|---------|---------|
| `autoStart` | `false` | Start the service when Studio starts |
| `host` | `localhost` | Bind host (loopback keeps the `@LocalhostOnly` management APIs reachable) |
| `port` | `8888` | HTTP port |
| `modelPath` | `./model` | A model file or directory to load at startup, relative to the working directory |

`modelPath` defaults to `./model` — the project folder Studio is launched from,
which is also where the agents look for `input/` and `output/`. The service is
started only when `autoStart` is `true` **and** `modelPath` exists on disk; a
project with no `model/` directory is left alone, and you can start the service
later from the **Inference** menu or by double-clicking a model in the Kernel tree.

---

## 9. AI Agent Panel

The right panel hosts the AI agents, each in its own tab. The tabs appear in the order
**Frank**, **Steve**, **Clair**, **Ada**, **James**, **Guido**, **Chuck** — Frank, the
chief of staff, leads the strip. All agents require an AI service to be configured
(see [Section 11](#11-settings--ai-service-configuration)).

### 9.1 Frank the Chief of Staff

> 🤝 *Frank the Chief of Staff* — strategic partner and cross-agent coordinator

Frank sits closest to you and keeps the whole picture in view:

- Prioritizing what deserves your attention and defending the priority order
- Coordinating cross-functional initiatives across the other agents
- Turning raw inputs into a short brief: what matters, why, and who acts
- Holding work accountable to the goals you committed to

### 9.2 Steve the Product Manager

> 🎯 *Steve the Product Manager* — product strategy and prioritization

Steve frames the problem before it is built:

- Opportunity mapping and problem validation
- User interviews and Jobs-to-Be-Done analysis
- Competitor analysis and product positioning
- PRDs, feature prioritization, and scope cutting
- Metrics frameworks, experiment design, and launch plans

When the requirements are settled, Steve hands off to Ada for architecture design.

### 9.3 Clair the Analyst

> 📊 *Clair the Analyst* — end-to-end ML/AI assistant

Clair handles the complete data science workflow:

- Automatic ML/AI solutions from natural language requirements
- Data loading from CSV, ARFF, JSON, Avro, Parquet, Iceberg, SQL
- Advanced interactive data visualization
- Model training, evaluation, and ensembling
- Inference server management

### 9.4 Ada the Architect

> 📐 *Ada the Architect* — software architecture and system design

Ada turns requirements into a buildable design:

- Feasibility and constraint analysis
- Domain design, decomposition, and contract design
- Functional design and non-functional requirements
- Architecture decision records (ADRs)
- Units generation and work breakdown

When the design is settled, Ada hands off to the developer agents for implementation.

### 9.5 James the Java Guru

> ☕ *James the Java Guru* — Java programming assistant

James helps with Java and SMILE-specific code:

- Code completion and generation in the notebook (via `Tab`)
- Reviewing and explaining Java code
- SMILE API guidance

### 9.6 Guido the Pythonista

> 🐍 *Guido the Pythonista* — Python programming assistant

Guido assists with Python notebooks:

- Code completion and generation
- Python data science library guidance

### 9.7 Chuck the Desktop Operator

> 🖥️ *Chuck the Desktop Operator* — desktop and GUI automation

Chuck drives what has no command line:

- Operating native applications through the GUI
- Navigating installers, wizards, and dialogs step by step
- Filling in forms and reading information that is only on screen
- Verifying each step with a screenshot before moving on

Chuck confirms before any action that is hard to undo, and never types secrets without consent.

### 9.8 Intent Types

Each intent (conversation turn) has a **type selector** in the footer:

| Type | Legend | Description |
|------|--------|-------------|
| **Instructions** | `>` | Natural language prompt sent to the AI agent |
| **Command** | `/` | Slash command (see below) |
| **Shell** | `!` | Shell command executed in a subprocess |
| **Raw** | — | Display-only entry (not editable) |

Switch the intent type using the combo box, or use the shortcuts below.

### 9.9 Slash Commands

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
| `/resume` | Choose a previous session and restore its messages into context |
| `/edit <file>` | Open a file in the Notepad editor |
| `/train` | Train a machine learning model (runs `smile train`) |
| `/predict` | Run batch inference (runs `smile predict`) |
| `/serve` | Start an inference service (runs `smile serve`) |

Additional custom slash commands can be defined as agent skills in `SMILE.md`.

**Hint window:** As you type a slash command, a hint tooltip appears showing the expected arguments.

### 9.10 Shell Commands

Set the intent type to **Shell** (or prefix your input with `!`) and enter any shell command. On Windows, commands run through `powershell.exe -Command`; on Unix/macOS, through `bash -c`. The output is streamed in real time to the output area. A **Stop** button appears to forcibly terminate long-running processes.

### 9.11 Long-Term Memory (SMILE.md)

Agents can maintain long-term, project-specific context stored in `SMILE.md` within the current working directory. This file is automatically loaded into the agent's system prompt. Use `/memory add` or `/memory edit` to update it.

**Initializing context:**
```
/init
```
Clair's `/init` skill creates a `SMILE.md` file by analysing the project and recording its structure, key decisions, and preferences.

### 9.12 Reasoning Effort

Each intent input shows a **Reasoning Effort** combo box. The available levels depend on the configured LLM:

| Level | Effect |
|-------|--------|
| *(default)* | Studio's starting selection. Omit the effort field and let the server use its own budget |
| `minimal` / `low` / `medium` / `high` | Shared set for OpenAI, Gemini, and compatible servers. Anthropic uses `low` / `medium` / `high` / `xhigh` / `max` |

Increasing reasoning effort produces more careful responses at the cost of higher latency and token usage.

On an OpenAI-compatible server, hidden reasoning counts toward the output-token cap. That cap is often 8192 when the request does not set one, so a long prompt can spend the whole budget on thinking and show almost no answer. You may set `smile.agent.max-output-tokens` to change that budget. The effort box on each turn chooses the thinking budget.

If a reply still stops at the output limit, the next attempt uses reasoning effort `high`. If that also stops at the limit, one more attempt uses `low`, and then the turn ends. The retry does not use `medium`, because some models reject it.

Thinking tokens stay out of the output panel unless the system property `smile.agent.show-thinking` is `true`. A long chain of thought would otherwise bury the answer.

### 9.13 Auto-Compact

Auto-compact runs `/compact` before the assumed context window is full. OpenAI, Anthropic, and Gemini assume a **1,000,000** token window and compact after **900,000** tokens. An OpenAI-compatible server (a local or on-prem model) assumes a **200,000** token window and compacts after **180,000** tokens, because the inference engine often sets a smaller limit than the weights allow. Set the system property `smile.agent.auto-compact` to use one token threshold for every provider.

When auto-compact finishes, the agent keeps only the summary and continues the task that was in progress. A `/compact` you type yourself summarizes and stops.

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
| LSP auto-completion | Triggers on `.` for Python files; for Java files only when JDT LS is installed (see §15) |
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

> **Note:** the top-level key is `servers` (VS Code). The alias `mcpServers`
> (Claude Desktop and most other harnesses) is also accepted, so an existing
> `mcp.json` from another tool can be read as-is. Java-style `//` and `/* */`
> comments are permitted, so a server can be commented out rather than deleted.

```json
{
  "servers": {
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

### 14.1 Web Search

SMILE Studio can search the web in two ways, and it prefers the provider's
native search to save context space:

| Provider | Web search | Setup |
|----------|-----------|-------|
| **OpenAI** | Built-in (server-side) | None — the model searches natively |
| **Anthropic** (managed service) | Built-in (server-side) | None |
| **Google Gemini** | Built-in (server-side) | None |
| **OpenAI-compatible** (`ChatCompletions`) | `WebSearch` tool (SerpApi) | Set `SERPAPI_KEY` |
| **Anthropic** (self-hosted base URL) | `WebSearch` tool (SerpApi) | Set `SERPAPI_KEY` |

When a provider has native search, the `WebSearch` tool is **not** added to the
conversation, and any MCP server tagged with `x-smile-provides: ["web-search"]`
is skipped — its tools are not advertised to the model. This keeps the context
window small. The server stays connected, so a tool call that still arrives is
served normally.

#### SerpApi (client-side `WebSearch` tool)

The `WebSearch` tool is backed by [SerpApi](https://serpapi.com/). It needs an
API key in the `SERPAPI_KEY` environment variable:

```shell
# macOS / Linux
export SERPAPI_KEY="your_api_key"

# Windows (PowerShell)
$env:SERPAPI_KEY = "your_api_key"
```

Get a key from the [SerpApi dashboard](https://serpapi.com/dashboard). SerpApi
offers a **free plan with 250 searches per month** (50 per hour), which is
usually enough for personal desktop use. Paid plans start at $25/month for
1,000 searches. Only successful searches count toward the quota.

If `SERPAPI_KEY` is missing or rejected, the tool returns an error and the agent
falls back to an Exa MCP server if one is configured (see below). If neither is
available, the agent tells you that web search is unavailable rather than
fabricating results.

#### Exa MCP server (alternative)

[Exa](https://exa.ai/) provides a hosted MCP server with `web_search_exa` and
`web_fetch_exa` tools. Add it to `mcp.json`:

```json
{
  "servers": {
    "exa": {
      "type": "streamable-http",
      "url": "https://mcp.exa.ai/mcp",
      "x-smile-provides": ["web-search"]
    }
  }
}
```

Exa works anonymously with rate limits. For higher limits, sign in with OAuth
(most clients prompt automatically) or pass an API key from the
[Exa dashboard](https://dashboard.exa.ai/api-keys) as `?exaApiKey=…` on the URL
or an `Authorization: Bearer …` header. The free tier includes **$10 of credits
per month** plus a $10 onboarding bonus.

> **`x-smile-provides` is a SMILE extension, not a standard MCP field.** It
> declares the capabilities a server offers so Studio can omit its tools when
> the provider already covers them natively. The `x-` prefix avoids a collision
> with a future standard field, and other MCP clients ignore the key. The only
> tag defined today is `web-search`. Omit the field to always advertise the
> server's tools.

---

## 15. LSP (Language Server Protocol) Integration

SMILE Studio starts the **Ty** language server for Python in the background:

| Server | Language | Provides | Bundled |
|--------|----------|----------|---------|
| **Ty** | Python | Type checking, diagnostics | Yes — started automatically |
| **JDT LS** | Java | Completions, hover, diagnostics | **No — opt-in, see below** |

### Java LSP (opt-in)

**JDT LS is not bundled with SMILE Studio.** Eclipse JDT LS is designed for
Gradle/Maven projects and does not handle JShell scripts well, which is what the Java
kernel actually executes, so it is not a good fit for the default install.

The integration is still present and works if you want it: Studio starts JDT LS
automatically **only if** `$SMILE_HOME/jdtls` exists. To enable Java completions,
hover, and diagnostics, install JDT LS yourself so that the launcher is at:

```
$SMILE_HOME/jdtls/bin/jdtls
```

If that directory is absent, Studio skips the Java server silently and Java
notebooks fall back to syntax highlighting plus AI completion (`Tab`).

Auto-completion is powered by the LSP providers and activates:

- **Automatically** after 300 ms of inactivity (configurable)
- **On `.`** — immediately triggers member completion for Python, and for Java when JDT LS is installed

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

**macOS:** Full window content mode and transparent title bar are enabled automatically for a native look. Metal rendering pipeline is disabled (`sun.java2d.metal=false`) to avoid CoreVideo `CVDisplayLink` crashes during display sleep/wake.

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
| `$SMILE_HOME/conf/smile.ini` | JVM options applied to every launch (see below) |

### JVM Options (`conf/smile.ini`)

The `conf/smile.ini` file holds JVM options that the `smile` / `smile.bat` launcher
passes to every launch — they apply to all Studio subcommands, not just the GUI.
Each line is one option. JVM and garbage-collector flags take a `-J` prefix, but
**system properties must be written in the plain `-Dname=value` form** (no `-J`):

```ini
# System property — plain -D form, no -J prefix
-Dsmile.agent.max-output-tokens=16384

# JVM flag — -J prefix is stripped by the launcher
-J-XX:+UseStringDeduplication
-J-XX:MaxRAMPercentage=75
```

> **Always use `-Dname=value` for system properties.** The two launchers handle
> the `-J`-prefixed form of a property inconsistently, and no single `-J` form
> works on both:
>
> - **Windows** (`smile.bat`): the launcher feeds the file through `call`, and
>   `cmd.exe` splits `call` arguments on `=` as well as on spaces. A line like
>   `-J-Dcomputer.enabled=true` reaches the JVM as `-Dcomputer.enabled` (empty
>   value — `System.getProperty` returns `""`) and leaves a stray `true` as an
>   application argument. The option silently does nothing.
> - **macOS / Linux** (`smile`): the bash launcher splits only on whitespace, so
>   the bare `-J-Dname=value` works there — but it does **not** strip quotes.
>   Quoting the token to work around Windows (as in `"-J-Dcomputer.enabled=true"`)
>   makes bash pass the literal quoted string through as an application argument,
>   which breaks on macOS.
>
> The plain `-Dname=value` form has neither problem: both launchers recognize the
> `-D…` prefix and forward the whole token verbatim. This is the form
> `smile -Dkey=val` already documents.
>
> `-XX:name=value` flags (e.g. `-J-XX:MaxRAMPercentage=75`) are unaffected and
> keep their `-J` prefix on both platforms. See
> [CLI.md §9](CLI.md#9-jvm-tuning-confsmileini) for the full set of default flags.

> **Note:** options in `conf/smile.ini` are read once, at process start. Edit the
> file and restart Studio for a change to take effect.

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

The Scala script engine needs the `smile.home` system property and the classpath to include Scala libraries. Ensure you are launching Studio via the provided `smile` launcher (run with no arguments), which sets `-Dsmile.home`, `-Dscala.usejavacp=true`, and `-Dscala.repl.autoruncode`.

### Ty server doesn't start

The `ty` binary ships in the bundled venv (`$SMILE_HOME/venv`), which the `smile`
launch script activates. If you launch Studio by another route, ensure that venv is
active so `ty` is on `PATH`. Errors are logged to the application log and shown
briefly in the status bar.

### Java completions don't work

This is expected on a default install — **JDT LS is not bundled** (see §15). To enable
Java completions, hover, and diagnostics, install JDT LS so that
`$SMILE_HOME/jdtls/bin/jdtls` exists and is executable. If it is installed and still
does not start, check the application log; errors are also shown briefly in the status bar.

### AI features not working (Tab completion, code generation, agents)

Open **File > Settings…** and verify your AI provider credentials. Check the status bar for initialization errors. Ensure network access to the provider's API endpoint is available.

### Web search fails

The root cause depends on the provider (see [§14.1](#141-web-search)):

- **OpenAI / Anthropic (managed) / Gemini** — search is built in; a failure is
  usually a network or provider-side issue, not a missing key.
- **OpenAI-compatible or self-hosted Anthropic** — the `WebSearch` tool needs
  `SERPAPI_KEY`. If it is unset, the agent reports that web search is
  unavailable. Set the variable (see [§14.1](#141-web-search)) and restart
  Studio so the process picks it up. A rejected or exhausted key is reported
  separately.
- **Exa MCP server** — if `web_search_exa` returns an auth or rate-limit error,
  sign in with OAuth or add an Exa API key. Anonymous access is rate-limited.

If neither the `WebSearch` tool nor an Exa MCP server is available, the agent
tells you that web search is unavailable instead of fabricating results.

### Notebook not saving

If **Save** shows an error dialog, check file system permissions for the target path. `Untitled.java` notebooks must be saved via **Save As…** before auto-save takes effect.

### Variables not appearing in Kernel Explorer

The Kernel Explorer refreshes when you **switch notebook tabs**. Switch away and back, or run a cell to trigger a refresh. Only named (non-scratch) variables appear; JShell scratch variables (e.g., `$1`, `$2`) are excluded.

### Out-of-memory errors in JShell

The remote JVM is allocated up to 75% of the system RAM (`-XX:MaxRAMPercentage=75`). On memory-constrained machines, reduce this by restarting the kernel after modifying the heap settings via a `//!` magic in the first cell, or add JVM options in the launch script.

---

*SMILE Studio is free software under the GNU General Public License v3. For commercial use enquiries contact sales@aihalo.dev*

