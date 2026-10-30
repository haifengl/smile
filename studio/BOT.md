# SMILE Bot & Agent Hub User Guide

> **SMILE Bot** (powered by **Agent Hub**) is the always-on autonomous runtime for [SMILE Studio](README.md). While SMILE Studio provides an interactive desktop IDE for notebooks and conversational agents, Agent Hub runs in the background (locally or on a server) to execute scheduled routines, ingest external events (timers, webhooks, emails, and calendar feeds), maintain cross-project memory logs, stage outbound drafts for human approval, and publish live HTML dashboards.
>
> For the interactive IDE guide, see [README.md](README.md). For command-line tools, see [CLI.md](CLI.md).

---

## Table of Contents

1. [Overview & Core Concepts](#1-overview--core-concepts)
   - 1.1 [Why a Separate Bot Runtime?](#11-why-a-separate-bot-runtime)
   - 1.2 [Three-Part Architecture: Bot Persona → Routine → Workflow](#12-three-part-architecture-bot-persona--routine--workflow)
   - 1.3 [Key Operational Patterns](#13-key-operational-patterns)
2. [Starting Agent Hub](#2-starting-agent-hub)
   - 2.1 [From SMILE Studio (GUI)](#21-from-smile-studio-gui)
   - 2.2 [From the Command Line (`smile hub`)](#22-from-the-command-line-smile-hub)
   - 2.3 [Auto-Start & Lifecycle Configuration (`conf/studio.json`)](#23-auto-start--lifecycle-configuration-confstudiojson)
3. [Built-In Bot Personas & Default Routines](#3-built-in-bot-personas--default-routines)
   - 3.1 [`all-agents-memory` — Daily Memory Logger (`*`)](#31-all-agents-memory--daily-memory-logger-)
   - 3.2 [`frank` — Chief of Staff (Primary Bot)](#32-frank--chief-of-staff-primary-bot)
   - 3.3 [`julien` — Developer & Product Evangelist](#33-julien--developer--product-evangelist)
   - 3.4 [`mike` — Founder-Led B2B Sales](#34-mike--founder-led-b2b-sales)
4. [SMILE Studio Bot Interface](#4-smile-studio-bot-interface)
   - 4.1 [Agent Hub Menu](#41-agent-hub-menu)
   - 4.2 [Bots Explorer Tab (`BotExplorer`)](#42-bots-explorer-tab-botexplorer)
   - 4.3 [Status Bar Bot & Approval Badge](#43-status-bar-bot--approval-badge)
   - 4.4 [Conversational Setup via `BotConfig` Tool](#44-conversational-setup-via-botconfig-tool)
5. [Configuring Bot Routines (`BotManager`)](#5-configuring-bot-routines-botmanager)
   - 5.1 [Routine Definition Tab](#51-routine-definition-tab)
   - 5.2 [Context Projects Tab (`conf/projects.json`)](#52-context-projects-tab-confprojectsjson)
   - 5.3 [Auto Review Rules Tab](#53-auto-review-rules-tab)
6. [Bot Activity (`BotActivity`)](#6-bot-activity-botactivity)
   - 6.1 [Approvals & Batch Draft Queue](#61-approvals--batch-draft-queue)
   - 6.2 [Run History & Session Transcripts](#62-run-history--session-transcripts)
   - 6.3 [Interactive Bot Pages](#63-interactive-bot-pages)
7. [External Event Sources](#7-external-event-sources)
   - 7.1 [Timer Source (`cron` and `intervalSeconds`)](#71-timer-source-cron-and-intervalseconds)
   - 7.2 [Webhook Intake (`POST /v1/events/{source}`)](#72-webhook-intake-post-v1eventssource)
   - 7.3 [Email Intake (`POST /v1/events/email/{mailbox}`)](#73-email-intake-post-v1eventsemailmailbox)
   - 7.4 [Calendar Polling (`.ics` / `webcal`)](#74-calendar-polling-ics--webcal)
   - 7.5 [Event Pipeline: Filter, Coalesce, Settle, Budget, Wake](#75-event-pipeline-filter-coalesce-settle-budget-wake)
8. [Configuration Files & Workspace Layout](#8-configuration-files--workspace-layout)
   - 8.1 [Bot Workspace Directory (`$SMILE_HOME/bot/`)](#81-bot-workspace-directory-smile_homebot)
   - 8.2 [`conf/bots.json` Schema Reference](#82-confbotsjson-schema-reference)
   - 8.3 [`conf/projects.json` Schema Reference](#83-confprojectsjson-schema-reference)
   - 8.4 [Shared LLM Configuration (`conf/llm.json`)](#84-shared-llm-configuration-confllmjson)
9. [REST API & Operations Reference](#9-rest-api--operations-reference)
   - 9.1 [Event Intake Endpoints](#91-event-intake-endpoints)
   - 9.2 [Admin & Bot Management Endpoints (`/v1/admin/*`)](#92-admin--bot-management-endpoints-v1admin)
   - 9.3 [Bot Pages Endpoints (`/v1/pages/*`)](#93-bot-pages-endpoints-v1pages)
   - 9.4 [Health & Metrics (`/q/health`, `/q/metrics`)](#94-health--metrics-qhealth-qmetrics)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Overview & Core Concepts

### 1.1 Why a Separate Bot Runtime?

In SMILE Studio, you open project folders and collaborate interactively with agents (Frank, Steve, Clair, Ada, James, Guido, Julien, Mike, Chuck). However, autonomous workflows—such as nightly memory consolidation across all your projects, morning signal scans, community monitoring, inbox triage, and pre-meeting calendar briefs—need to run on reliable schedules and respond to webhooks without interrupting your active coding or notebook tabs.

**Agent Hub** (`smile hub`) solves this by running as a dedicated service:

- **No session interleaving:** Autonomous runs execute in isolated or dedicated Bot sessions under `$SMILE_HOME/bot/`, never scrambling your interactive Studio chat tabs.
- **Attach-or-spawn model:** If `smile hub` is already running in a terminal or on a server, SMILE Studio attaches to it automatically. Otherwise, Studio can launch and manage the Hub as a local service process.
- **Shared configuration & credentials:** Studio and Agent Hub share LLM provider settings (`$SMILE_HOME/conf/llm.json`), OS keyring credentials (`SecretStore`), Bot definitions (`$SMILE_HOME/conf/bots.json`), and tracked Studio project paths (`$SMILE_HOME/conf/projects.json`).

### 1.2 Three-Part Architecture: Bot Persona → Routine → Workflow

SMILE Bot separates **who** acts, **when** they wake, and **how** they do the work:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. BOT PERSONA (Who)                                                    │
│    botId: "frank", "julien", "mike", "all-agents-memory", ...           │
│    persona: Agent spec ("chief-of-staff", "evangelist", "sales", "*")   │
│    Workspace & durable memory: $SMILE_HOME/bot/                         │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ owns 1..N
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. ROUTINE (When & Under What Guardrails)                               │
│    routineId: "frank-daily-recap", "mike-signal-watch", ...             │
│    Trigger: timer (cron / interval), email, calendar, webhook           │
│    Model Override: optional per-routine model (e.g. fast watch model)   │
│    Policy: sessionMode, confirmConsequential, approvalTtlMinutes        │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ executes
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. WORKFLOW (How)                                                       │
│    Kind: SKILL (e.g. /daily-log, /daily-recap, /cold-email)             │
│          PROMPT (natural-language instructions)                         │
│          TOOL (direct tool invocation)                                  │
│    Pre-conditions: requireActivityToday, useContextProjects,            │
│                    awaitRoutineId                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Key Operational Patterns

1. **Primary Bot Front Door (`@frank`):** Frank (`chief-of-staff`) is the default primary coordinator. He consolidates daily logs across all personas and projects, triages executive email, guards your calendar, and delegates specialist tasks to peer agents via `AgentRequest`.
2. **Setup Is a Message, Not Just a Form:** You can configure routines visually via **Agent Hub > Configure Bots & Routines…** or simply ask `@frank` in natural language (e.g., *"Pause Julien's community watch over the weekend"* or *"Add a weekday 9 AM routine for Clair"*). Agent-initiated changes use the `BotConfig` tool and require your explicit approval.
3. **Cross-Project Memory Enrichment (`projects.json`):** Whenever you open a project in SMILE Studio, its path and `.smile/` workspace metadata are registered in `$SMILE_HOME/conf/projects.json`. Cross-project routines (`daily-log` at 19:00 and `frank-daily-recap` at 20:00) inspect both `$SMILE_HOME/bot/` and every tracked Studio project without merging project files.
4. **Zero-Waste Idle Skipping (`requireActivityToday`):** When `requireActivityToday` is enabled on a routine (such as `daily-log`), Agent Hub checks whether the target persona modified any session files today before calling the LLM. Idle personas are recorded as `SKIPPED_IDLE` with zero token cost.
5. **Two-Tier Model Routing (Watch vs. Act):** High-frequency scanning routines (such as `julien-community-watch` or `mike-signal-watch`) can specify a fast, low-cost `model` override (e.g., `claude-haiku-4-5`), while synthesis and drafting routines use the persona's default frontier model.
6. **Batch Draft Queue & Auto Review Guardrails:** Outbound emails, social posts, consequential shell/file actions, and automation writes are intercepted into a durable approval queue. Standing Auto Review rules let you auto-allow trusted patterns while guaranteeing that **`REQUIRE_APPROVAL` always wins over `ALWAYS_ALLOW`**.
7. **Interactive Bot Pages:** Routines update self-contained HTML dashboards in `$SMILE_HOME/bot/pages/` (`executive-brief.html`, `sales-pipeline.html`, `community-radar.html`, `token-usage.html`) that you can preview inside Studio or open in your browser.

---

## 2. Starting Agent Hub

### 2.1 From SMILE Studio (GUI)

You can start, stop, or attach to Agent Hub from three places in the Studio window:

1. **Menu Bar:** Select **Agent Hub > Start Agent Hub** (or **Stop Agent Hub** / **Restart Agent Hub**).
2. **Kernel Explorer:** Expand **Services > Agent Hub Service**, right-click the node, and choose **Start Agent Hub**.
3. **Bot Activity Dialog:** Click the `[Bots]` badge on the right side of the Status Bar (or select **Agent Hub > Approvals & Run History…**) and click **Start / Attach Hub**.

When Studio starts a local Agent Hub process, it opens an **Agent Hub Service** log window (`ProcessFrame`) showing live server output and automatically syncs your tracked Studio projects from `conf/projects.json`. If an Agent Hub is already running at the configured `host:port`, Studio attaches to it without spawning a second JVM.

### 2.2 From the Command Line (`smile hub`)

To run Agent Hub as a standalone 24/7 background or server process, use the `smile hub` subcommand:

```bash
# macOS / Linux
path/to/smile/bin/smile hub [options]

# Windows
path\to\smile\bin\smile.bat hub [options]
```

#### CLI Options

| Option | Default | Description |
|--------|---------|-------------|
| `--host <addr>` | `localhost` | Network interface to bind (`quarkus.http.host`) |
| `-p`, `--port <n>` | `8890` | HTTP listen port (`quarkus.http.port`) |
| `--profile <name>` | `local` | Quarkus runtime profile: `local` (embedded H2) or `server` (PostgreSQL) |
| `-w`, `--workspace <path>` | `$SMILE_HOME/bot` | Bot workspace directory (`hub.agent.workspace`) |
| `--admin-token <token>` | `dev-admin-token` | Bearer token required by `/v1/admin/*` endpoints (`hub.admin.token`) |
| `--log-level <level>` | `INFO` | Logging level (`INFO`, `DEBUG`, `WARN`, `ERROR`) |
| `-D<key>=<value>` | — | Pass a system property to the Hub JVM (e.g., `-Dhub.webhook.secret.default=mysecret`) |
| `-J<arg>`, `--jvm-arg <arg>` | — | Pass a JVM flag to the Hub process (e.g., `-J-Xmx4g`) |

**Examples:**

```bash
# Start local Agent Hub on default port 8890 with embedded H2 storage
smile hub

# Start on a custom port with a custom admin token and webhook secret
smile hub --port 8890 --admin-token "prod-secret-token" \
  -Dhub.webhook.secret.default="whsec_12345"
```

### 2.3 Auto-Start & Lifecycle Configuration (`conf/studio.json`)

Configure Studio's connection and lifecycle behavior in `$SMILE_HOME/conf/studio.json` or `~/.smile/studio.json`:

```json
{
  "agentHub": {
    "autoStart": true,
    "host": "localhost",
    "port": 8890,
    "adminToken": "dev-admin-token",
    "keepAliveOnExit": true
  }
}
```

| Field | Default | Meaning |
|-------|---------|---------|
| `autoStart` | `false` | Automatically start (or attach to) Agent Hub when SMILE Studio launches |
| `host` | `"localhost"` | Target host for Agent Hub |
| `port` | `8890` | HTTP port for Agent Hub |
| `adminToken` | `"dev-admin-token"` | Bearer token used by Studio to authenticate against `/v1/admin/*` |
| `keepAliveOnExit` | `false` | When `true`, leave the local Agent Hub process running after you close SMILE Studio so scheduled routines continue 24/7 |

---

## 3. Built-In Bot Personas & Default Routines

Out of the box, `$SMILE_HOME/conf/bots.json` ships with four Bot entries and nine coordinated routines:

| Bot ID | Persona | Role | Default Routines |
|--------|---------|------|------------------|
| `all-agents-memory` | `*` (All active personas) | Daily Memory Logger | `daily-log` (Daily 19:00) |
| `frank` | `chief-of-staff` | Primary Bot & Executive Coordinator | `frank-daily-recap` (Daily 20:00), `frank-inbox` (Email), `frank-calendar` (Calendar) |
| `julien` | `evangelist` | Developer & Product Evangelist | `julien-community-watch` (Every 4h), `julien-community-post` (Mon/Wed/Fri 10:00) |
| `mike` | `sales` | Founder-Led B2B Sales | `mike-signal-watch` (Mon–Fri 08:00), `mike-outreach-sweep` (Mon–Fri 08:30), `mike-replies` (Email) |

### 3.1 `all-agents-memory` — Daily Memory Logger (`*`)

- **`daily-log` (7:00 PM Daily Memory Log)**
  - **Trigger:** `timer` (`cron: "0 19 * * *"`, `missedFirePolicy: "FIRE_ONCE"`)
  - **Persona expansion:** Because `persona` is `"*"`, Agent Hub expands the run across every distinct persona configured in `bots.json` and every persona active in `contextProjects`.
  - **Idle check:** `requireActivityToday: true` — any persona with zero modified `conversation.jsonl` sessions today (across `$SMILE_HOME/bot/` and all tracked `contextProjects`) is skipped without invoking the LLM (`SKIPPED_IDLE`).
  - **Workflow:** Runs the `/daily-log` skill (`useContextProjects: true`) to append structured, indexable entries to each active workspace's `memory/YYYY-MM-DD.md`.

### 3.2 `frank` — Chief of Staff (Primary Bot)

- **`frank-daily-recap` (8:00 PM End-of-Day Consolidation & Briefing)**
  - **Trigger:** `timer` (`cron: "0 20 * * *"`, `missedFirePolicy: "FIRE_ONCE"`)
  - **Upstream barrier:** `awaitRoutineId: "daily-log"` — if the 7:00 PM `daily-log` routine has not yet completed today (for example, if the machine was asleep and woke at 8:05 PM), Agent Hub automatically runs `daily-log` first before starting `frank-daily-recap`.
  - **Workflow:** Runs `/daily-recap` (`useContextProjects: true`) to synthesize the day's logs across `$SMILE_HOME/bot/` and all Studio projects, promote durable facts to `MEMORY.md`, and refresh `$SMILE_HOME/bot/pages/executive-brief.html`.
- **`frank-inbox` (Executive Email Triage)**
  - **Trigger:** `email` (`mailbox: "frank"`, `settleSeconds: 45`, `maxTurnsPerHour: 10`)
  - **Workflow:** Triages inbound emails against active priorities in `MEMORY.md`, delegates specialist work to peer agents via `AgentRequest`, and stages outbound replies into the Batch Draft Queue (`confirmConsequential: true`).
- **`frank-calendar` (Calendar Guard & 15m Pre-Meeting Brief)**
  - **Trigger:** `calendar` (`calendarUrl: "file://${smile.home}/bot/calendar/frank.ics"`, poll interval `300s`, `reminderBeforeSeconds: 900`)
  - **Workflow:** Checks schedule conflicts on `calendar.invite` / `calendar.updated` (asking whether to Accept/Decline with a 15-minute approval TTL) and prepares attendee/project context briefs 15 minutes before meetings (`calendar.reminder`).

### 3.3 `julien` — Developer & Product Evangelist

- **`julien-community-watch` (4-Hour Community Watch)**
  - **Trigger:** `timer` (`intervalSeconds: 14400`, `missedFirePolicy: "SKIP"`, `quietStart: "22:00"`, `quietEnd: "08:00"`)
  - **Model override:** `claude-haiku-4-5` (lightweight watch tier)
  - **Workflow:** Scans Hacker News, Reddit, GitHub Discussions, and Stack Overflow for new relevant threads not yet recorded in `.smile/memory/community-feed.json`, forwards buying signals to `@mike`, and triggers `julien-community-post` if urgent questions need answers.
- **`julien-community-post` (Mon/Wed/Fri Proactive Community Post & Answer Sweep)**
  - **Trigger:** `timer` (`cron: "0 10 * * MON,WED,FRI"`, `missedFirePolicy: "SKIP"`)
  - **Workflow:** Runs the `/social-writing` skill to draft technical answers for queued threads and one proactive educational post, polishes prose with `/humanize`, stages all drafts into the Batch Draft Queue for review, and refreshes `$SMILE_HOME/bot/pages/community-radar.html`.

### 3.4 `mike` — Founder-Led B2B Sales

- **`mike-signal-watch` (Weekday Morning Signal Watch)**
  - **Trigger:** `timer` (`cron: "0 8 * * MON-FRI"`, `missedFirePolicy: "FIRE_ONCE"`)
  - **Model override:** `claude-haiku-4-5`
  - **Workflow:** Runs `/signal-scanning` to scan buying signals from the last 24 hours, qualify prospects against `ICP.md`, and update `.smile/memory/pipeline.json`.
- **`mike-outreach-sweep` (Weekday Multi-Channel Outreach Sweep)**
  - **Trigger:** `timer` (`cron: "30 8 * * MON-FRI"`, `missedFirePolicy: "FIRE_ONCE"`, `awaitRoutineId: "mike-signal-watch"`)
  - **Workflow:** Runs `/cold-email` for qualified prospects and due follow-ups in `pipeline.json`, drafts channel-appropriate messages (`email`, `linkedin_connect`, `linkedin_dm`, `messaging`), polishes with `/humanize`, stages the batch in the Draft Queue for approval, and updates `$SMILE_HOME/bot/pages/sales-pipeline.html`.
- **`mike-replies` (Inbound Prospect Reply Handler)**
  - **Trigger:** `email` (`mailbox: "mike"`, `settleSeconds: 30`, `maxTurnsPerHour: 10`)
  - **Workflow:** Updates prospect stage in `pipeline.json`, coordinates meeting scheduling with `@frank` if requested, and stages a reply draft for user approval.

---

## 4. SMILE Studio Bot Interface

### 4.1 Agent Hub Menu

The **Agent Hub** menu in the Studio menu bar provides direct access to service lifecycle, configuration, approvals, dashboards, and diagnostics:

| Menu Item | Action |
|-----------|--------|
| **Start Agent Hub** | Start the local Agent Hub service (or attach if already running) |
| **Stop Agent Hub** | Stop the Studio-managed Agent Hub process |
| **Restart Agent Hub** | Stop and restart the local Agent Hub process |
| **Bot Routines…** | Open the `Bot Routines` tab in the main workspace (`BotManager`) |
| **Bot Activities…** | Open the `Bot Activities` tab in the main workspace (`BotActivity`) |
| **Health** | Open `http://<host>:<port>/q/health` in the default browser |
| **Metrics** | Open `http://<host>:<port>/q/metrics` in the default browser |

### 4.2 Bots Explorer Tab (`BotExplorer`)

In the left-hand explorer sidebar, select the **Bots** tab to view all configured Bot Personas and their Routines under **Agent Hub**:

```
Agent Hub (Running)
├── ★ all-agents-memory (Daily Memory Logger)
│   └── ● daily-log — 7:00 PM Daily Memory Log
├── @frank (Frank — Chief of Staff (Primary Bot))
│   ├── ● frank-daily-recap — 8:00 PM End-of-Day Consolidation & Briefing
│   ├── ● frank-inbox — Executive Email Triage
│   └── ● frank-calendar — Calendar Guard & 15m Pre-Meeting Brief
├── @julien (Julien — Developer & Product Evangelist)
│   ├── ● julien-community-watch — 4-Hour Community Watch (HN, Reddit, GitHub, SO)
│   └── ● julien-community-post — Mon/Wed/Fri Proactive Community Post & Answer Sweep
└── @mike (Mike — Founder-Led B2B Sales)
    ├── ● mike-signal-watch — Weekday Morning Signal Watch (08:00)
    ├── ● mike-outreach-sweep — Weekday Multi-Channel Outreach Sweep (08:30)
    └── ● mike-replies — Inbound Prospect Reply Handler
```

- **Status indicators:** `●` indicates an enabled routine under an enabled bot; `○` indicates a disabled routine or bot.
- **Click a routine node:** Opens or focuses the **Bot Routines** workspace tab (`BotManager`) pre-selected on that routine.
- **Double-click a `@bot` node:** Opens the **Bot Activities** workspace tab (`BotActivity`).
- **Right-click context menu:**
  - **Run Now (`<routineId>`)** *(on a routine node)* — Immediately triggers the routine on Agent Hub (starting the Hub first if needed).
  - **Enable / Disable Routine** *(on a routine node)* — Toggles the routine in `bots.json` and hot-reloads Agent Hub.
  - **Configure Routine…** *(on a routine node)* — Opens the `Bot Routines` workspace tab for that routine.
  - **Delete Routine** *(on a routine node)* — Prompts for confirmation and removes the routine from `bots.json`.
  - **New Routine…** — Opens the `Bot Routines` workspace tab prepared for a new routine (pre-filled with the selected Bot persona if right-clicked on a bot or routine node).
  - **Bot Routines…** — Opens the `Bot Routines` workspace tab.
  - **Bot Activities…** — Opens the `Bot Activities` workspace tab.
  - **Start / Stop Agent Hub** — Starts or stops the Hub process.

### 4.3 Status Bar Bot & Approval Badge

The right side of the Studio status bar displays a live, clickable badge polled every 10 seconds:

| Badge State | Meaning |
|-------------|---------|
| `[Bots]` | Agent Hub is currently offline. Click to open the `Agent Hub` workspace tab and start the Hub or view offline Bot Pages. |
| `[Hub: UP]` | Agent Hub is running and no approvals are waiting. Click to inspect runs or pages. |
| `[Approvals (N)]` *(amber warning)* | `N` pending approvals, staged outreach drafts, or questions are waiting for your review. Click to review and resolve them. |

### 4.4 Conversational Setup via `BotConfig` Tool

In any Studio agent tab (such as `@frank`), you can inspect or modify Bot routines in natural language. Agents use the built-in `BotConfig` tool, which supports five actions:

| Action | Description | Approval Required? |
|--------|-------------|--------------------|
| `list` | Inspect the current `bots.json` configuration | No (read-only) |
| `upsert_routine` | Create a new routine or update an existing routine under a Bot persona | **Yes** (`AUTOMATION_WRITE`) |
| `enable_routine` | Enable a routine by `routine_id` | **Yes** (`AUTOMATION_WRITE`) |
| `disable_routine` | Disable a routine by `routine_id` | **Yes** (`AUTOMATION_WRITE`) |
| `delete_routine` | Delete a routine from `bots.json` | **Yes** (`AUTOMATION_WRITE`) |

Every mutating action prompts you with an **Automation Write Approval** confirmation before `bots.json` is modified.

---

## 5. Configuring Bot Routines (`BotManager`)

Open **Agent Hub > Bot Routines…** (or click any routine in the **Bots** explorer tab) to open the `Bot Routines` tab and edit `$SMILE_HOME/conf/bots.json` and `$SMILE_HOME/conf/projects.json` visually. Changes can be edited even when Agent Hub is offline and are hot-reloaded immediately when Agent Hub is running.

### 5.1 Routine Definition Tab

Select any routine in the **Bots** explorer tab (or right-click to choose **New Routine…**) to load or create a routine in the form:

#### Identity & Model Routing
- **Bot ID & Persona:** Select or enter the owning `botId` (`frank`, `all-agents-memory`, `julien`, `mike`, or a custom ID) and target agent spec `persona` (`chief-of-staff`, `*`, `evangelist`, `sales`, `data-scientist`, `architect`, `java-coder`, `pythonista`, `desktop-operator`).
- **Routine ID & Enabled:** Unique identifier (e.g., `clair-weekly-audit`) and active toggle.
- **Display Name:** Human-readable title.
- **Model Override:** Choose `<Default Persona Model>` to use the persona's standard model, or select/type a specific model ID (such as `claude-haiku-4-5` or `gpt-5.4-mini`) for lightweight scanning routines.

#### Trigger & Schedule
- **Source:** `timer`, `email`, `calendar`, or `webhook`.
- **Missed-Fire Policy:**
  - `FIRE_ONCE` — If Agent Hub was offline when a scheduled tick occurred, fire once catch-up upon startup (recommended for daily logs, recaps, and weekday signal watches).
  - `SKIP` — Skip missed ticks and wait for the next scheduled time (recommended for high-frequency polling or social posting).
- **Cron (5-field):** Standard POSIX cron expression (`minute hour day-of-month month day-of-week`, e.g., `0 19 * * *` or `30 8 * * MON-FRI`).
- **Interval (sec):** Fixed poll/fire interval in seconds (used when `cron` is blank for `timer`, or as the feed poll interval for `calendar`).
- **Mailbox / Feed URL / Path:**
  - For `email`: mailbox name (e.g., `frank` or `mike`, matching `POST /v1/events/email/{mailbox}`).
  - For `calendar`: `.ics` file URI or HTTP/webcal URL (supports `${smile.home}`, e.g., `file://${smile.home}/bot/calendar/frank.ics`).
  - For `webhook`: webhook source segment (matching `POST /v1/events/{source}`).
- **Quiet Start / Quiet End (`HH:mm`):** Optional local quiet hours (e.g., `22:00` to `08:00`) during which non-urgent events are held until quiet hours end.
- **Settle (sec):** Coalescing settle window in seconds. Bursts of events arriving within this window are merged into a single agent turn.
- **Max Turns / Hour:** Per-routine hourly turn budget cap to prevent runaway token spend.

#### Workflow & Cross-Project Enrichment
- **Workflow Kind:**
  - `SKILL` — Invokes an agent slash skill (e.g., `daily-log`, `daily-recap`, `social-writing`, `signal-scanning`, `cold-email`).
  - `PROMPT` — Sends the target text as a direct instruction prompt.
  - `TOOL` — Instructs the agent to execute a specific tool.
- **Target (Skill / Prompt):** Skill name, prompt body, or tool name.
- **Instructions:** Supplementary instructions appended to the workflow prompt (supports `${smile.home}` placeholder expansion).
- **Await Routine ID:** Optional upstream `routineId` dependency (e.g., `frank-daily-recap` awaits `daily-log`; `mike-outreach-sweep` awaits `mike-signal-watch`). If the upstream routine has not completed today, Agent Hub runs it first.
- **Skip if persona had 0 sessions today (`requireActivityToday`):** Skips waking the LLM when the persona had no modified sessions today.
- **Enrich prompt with Studio Context Projects (`useContextProjects`):** Appends the list of tracked Studio project paths and `.smile/` directories from `conf/projects.json` to the turn prompt.

#### Session & Approval Guardrails
- **Session Mode:**
  - `ISOLATED_PER_RUN` *(default)* — Creates a fresh, isolated session under `$SMILE_HOME/bot/.smile/<persona>/sessions/<timestamp>/` for each run so context never bloats across days.
  - `PERSISTENT_BOT` — Reuses the resident Bot session across wakes.
- **Require approval for consequential actions (`confirmConsequential`):** Gates consequential tool calls (shell commands, external file writes, peer delegation, outbound communications) through the approval queue.
- **Approval TTL (min):** Time-to-live in minutes for pending approvals (`0` = no expiry, used for Batch Draft Queue items; `> 0` = automatically marks unreviewed items `EXPIRED` and fails closed after the deadline, used for time-sensitive calendar invites).

Click **Save to bots.json** to persist changes, or click **Run Routine Now** to test the selected routine immediately.

### 5.2 Context Projects Tab (`conf/projects.json`)

Whenever SMILE Studio opens a project folder, it automatically registers the workspace in `$SMILE_HOME/conf/projects.json`. The **Context Projects** tab displays all tracked projects (`Project Name`, `Path`, `Workspace (.smile)`, and discovered `Personas`).

- Click **+ Add Project Folder…** to register an additional project directory manually.
- Click **Remove Selected** to stop including a project in cross-project routines (`daily-log` and `frank-daily-recap`).

### 5.3 Auto Review Rules Tab

Standing Auto Review rules control whether consequential actions pause for human approval or execute automatically. Each rule has four fields:

| Field | Values | Description |
|-------|--------|-------------|
| `id` | String (e.g. `rule-require-outbound-approval`) | Unique rule identifier |
| `category` | `SHELL`, `CONNECTOR`, `COMPUTER`, `AUTOMATION_WRITE`, `OUTBOUND_COMMUNICATION`, `DELEGATION` | Action category evaluated by the policy engine |
| `pattern` | `*`, substring, or case-insensitive regex | Matched against the command, recipient, or action summary |
| `decision` | `REQUIRE_APPROVAL` or `ALWAYS_ALLOW` | Whether matching actions must wait for approval or run automatically |

> **Safety Precedence Invariant:** When multiple Auto Review rules match an action, **`REQUIRE_APPROVAL` always wins over `ALWAYS_ALLOW`**. By default, `bots.json` includes standing `REQUIRE_APPROVAL` rules for `OUTBOUND_COMMUNICATION` (`*`) and `AUTOMATION_WRITE` (`*`).

---

## 6. Bot Activities (`BotActivity`)

Open **Agent Hub > Bot Activities…** (or click the status bar `[Bots]` / `[Approvals (N)]` badge) to open the `Bot Activities` tab inside the main workspace alongside notebooks and text editors.

### 6.1 Approvals & Batch Draft Queue

Autonomous runs never block on modal desktop popups. Instead, any human-in-the-loop interaction is recorded in Agent Hub's `IoABotApproval` queue and displayed in the **Approvals & Batch Draft Queue** tab:

#### Approval Kinds

| Kind | Source | Description |
|------|--------|-------------|
| `BATCH_DRAFT` | Outbound email, LinkedIn, or community post routines | Multi-channel messages staged for batch review, inline editing, and approval |
| `TOOL_CONFIRM` | Consequential tool gate (`Bash`, `PowerShell`, `KillShell`, `AgentRequest`, out-of-workspace file edits) | Tool call waiting for permission to execute |
| `QUESTION` | `AskUserQuestion` tool | Clarifying question or calendar Accept/Decline prompt from a routine |
| `AUTOMATION_WRITE` | `BotConfig` tool | Agent-proposed creation, modification, or deletion of a routine in `bots.json` |
| `SECURE_HANDOFF` | Browser authentication / 2FA / CAPTCHA | Requests user completion of an interactive login flow, saving session state under `$SMILE_HOME/bot/.smile/browser-state/` |

#### Reviewing, Diffing, Editing, and Resolving

1. **Inspect & Diff:** Select any row in the table. The bottom split pane shows:
   - **Left (`Previous Draft / Prior Payload`):** The baseline payload from a prior draft version (if regenerated or revised) so you can see what changed.
   - **Right (`Current / Editable Draft Payload`):** The live JSON or text payload, which you can edit directly in place before approving.
2. **Single-Item Actions:**
   - **Allow Once (`ALLOW_ONCE`)** — Approve and unblock the waiting run (or release the draft).
   - **Edit & Approve (`EDIT_AND_APPROVE`)** — Save your inline edits from the right-hand editor and approve (`EDITED_AND_APPROVED`).
   - **Always Allow Rule (`ALWAYS_ALLOW_RULE`)** — Approve this item and add a standing `ALWAYS_ALLOW` rule for the pattern in the **Rule Pattern** field (subject to the invariant that `REQUIRE_APPROVAL` rules still take precedence).
   - **Deny (`DENY`)** — Reject the action or discard the draft.
3. **Batch Actions:**
   - Click **Select All Pending Drafts** (or check individual rows in the `Select` column) and click **Approve Checked Batch** to approve an entire morning outreach or community answer batch in one click.

### 6.2 Run History & Session Transcripts

The **Run History & Transcripts** tab lists every routine execution (`IoABotRun`) with its `Run ID`, `Bot`, `Routine`, `Persona`, `Model`, `Status`, `Started At`, and `Summary / Error`:

| Run Status | Meaning |
|------------|---------|
| `RUNNING` | Turn is currently executing |
| `WAITING_APPROVAL` | Turn is paused waiting for a pending `QUESTION`, `TOOL_CONFIRM`, `AUTOMATION_WRITE`, or `SECURE_HANDOFF` resolution |
| `COMPLETED` | Routine finished normally (and any `BATCH_DRAFT` items were staged) |
| `SKIPPED_IDLE` | Skipped before calling the LLM because `requireActivityToday` was `true` and the persona had 0 modified sessions today |
| `FAILED` | Turn terminated with an error (shown in `Summary / Error`) |
| `CANCELLED` | Stopped manually by the user |

- **View Transcript:** Select any run row to load its full `conversation.jsonl` transcript in the lower pane.
- **Stop / Cancel Run:** Click **Stop / Cancel Selected Run** to interrupt an in-flight `RUNNING` or `WAITING_APPROVAL` turn.

### 6.3 Interactive Bot Pages

Routines generate and refresh self-contained HTML dashboards under `$SMILE_HOME/bot/pages/`. The **Interactive Bot Pages** tab lets you preview pages inside Studio or open them in your default web browser (`http://<host>:<port>/v1/pages/<slug>` when Agent Hub is running, or directly from disk when offline):

| Slug | File | Owner | Contents |
|------|------|-------|----------|
| `executive-brief` | `executive-brief.html` | `@frank` | End-of-day cross-project recap, active priorities, calendar alerts, and pending decisions |
| `sales-pipeline` | `sales-pipeline.html` | `@mike` | Qualified prospects, buying signals, pipeline stages, and staged multi-channel outreach |
| `community-radar` | `community-radar.html` | `@julien` | High-signal Hacker News, Reddit, GitHub, and Stack Overflow threads and drafted answers |
| `token-usage` | `token-usage.html` | System | Routine execution counts, idle-skip savings, model tier breakdown, and event telemetry |

You can also create custom HTML files in `$SMILE_HOME/bot/pages/<slug>.html` from any routine; Agent Hub discovers and serves them automatically at `/v1/pages/<slug>`.

---

## 7. External Event Sources

Every routine in `bots.json` compiles into an `EventSubscription` managed by Agent Hub's `SourceManager` and `AgentEventRouter`. All external event payloads are wrapped in a provenance-tagged `<event>` envelope (`neutralizing` any embedded `<event>` or `<system-reminder>` tags) so untrusted inputs are treated as data rather than system instructions.

### 7.1 Timer Source (`cron` and `intervalSeconds`)

Active timer routines are driven by `TimerSource`:
- **Cron schedules (`cron`):** 5-field POSIX cron expressions (`minute hour day-of-month month day-of-week`) evaluated in the local system time zone. Supports ranges (`MON-FRI`), lists (`MON,WED,FRI`), steps (`*/15`), and wildcards (`*`).
- **Fixed intervals (`intervalSeconds`):** Fires every $N$ seconds (e.g., `14400` for every 4 hours).
- **Missed-fire recovery:** On startup, Agent Hub compares the current time against each routine's `lastFiredAt` timestamp in `IoABot`. If a scheduled fire was missed while the Hub was stopped, `FIRE_ONCE` coalesces all missed ticks into a single catch-up wake, whereas `SKIP` advances the cursor to the next future tick.

### 7.2 Webhook Intake (`POST /v1/events/{source}`)

External services (GitHub, CI/CD, Stripe, monitoring alerts) can push JSON events to `POST /v1/events/{source}`:

- **HMAC-SHA256 Authentication:** Every request must carry the `X-IoA-Signature-256: sha256=<hex>` header computed over the raw request body using the source's secret (`hub.webhook.secret.<source>`, falling back to `hub.webhook.secret.default`). Signatures are verified in constant time (`MessageDigest.isEqual`). Unauthenticated requests are rejected with `401 Unauthorized` and logged as `REJECTED_AUTH`.
- **Body size limit:** Enforced by `hub.webhook.max-body-bytes` (default `1048576` bytes / 1 MB; returns `413 Payload Too Large` if exceeded).
- **Attachments:** Base64 attachments in the payload are sanitized and written under `$SMILE_HOME/bot/.smile/hub/events/webhook/<eventId>/<filename>`.

**Example webhook request:**

```bash
SECRET="my-webhook-secret"
BODY='{"id":"evt-101","type":" alerts.deploy ","subject":"Production deploy succeeded","text":"Release v2.4.0 is live."}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | awk '{print $2}')

curl -X POST http://localhost:8890/v1/events/deploy-alerts \
  -H "Content-Type: application/json" \
  -H "X-IoA-Signature-256: sha256=$SIG" \
  -d "$BODY"
```

### 7.3 Email Intake (`POST /v1/events/email/{mailbox}`)

Inbound email webhooks (e.g., from SendGrid, Postmark, Mailgun, Amazon SES, or an IMAP bridge) post to `POST /v1/events/email/{mailbox}` (where `{mailbox}` matches the routine's `trigger.mailbox`, such as `frank` or `mike`):

- **HMAC Verification:** Verified via `X-IoA-Signature-256` against `hub.email.secret.<mailbox>` (fallback `hub.email.secret.default`).
- **Format Normalization:** Accepts either structured JSON or raw RFC 5322 MIME (`message/rfc822`) payloads.
- **SPF / DKIM Verification:** `EmailSource` verifies SPF/DKIM authentication results; spoofed or failing messages are rejected with `401 Unauthorized` (`REJECTED_AUTH`).
- **Sender Allowlist & Subject Filtering:** Unwraps angle-bracketed `From:` addresses and filters against the routine's `senderAllowlist` (default `["*"]`) and optional `subjectRegex`.

### 7.4 Calendar Polling (`.ics` / `webcal`)

`CalendarSource` polls iCalendar (`.ics` or `webcal://`) feeds configured in `trigger.calendarUrl`:

- **Local or Remote Feeds:** Supports `file://${smile.home}/bot/calendar/frank.ics` as well as `https://` and `webcal://` feed URLs.
- **Event Types Emitted:** Parses RFC 5545 `VEVENT` entries and emits:
  - `calendar.invite` — New meeting invitation discovered.
  - `calendar.updated` — Meeting time, location, or attendees changed (`SEQUENCE` / `LAST-MODIFIED`).
  - `calendar.cancelled` — Meeting status changed to `CANCELLED`.
  - `calendar.reminder` — Fired `reminderBeforeSeconds` (default `900` = 15 minutes) before a scheduled meeting starts.
- **Cursor Persistence:** Poll cursors are persisted in `IoASourceState` and `IoABot` so restarts never re-fire duplicate reminders.

### 7.5 Event Pipeline: Filter, Coalesce, Settle, Budget, Wake

Every incoming event passes through a five-stage pipeline (`AgentEventRouter` + `EventIntake`) and is recorded in the append-only `IoAEventRecord` audit table with one of six verdicts:

1. **Auth & Kill Switch (`REJECTED_AUTH` / `503`):** Verifies HMAC/SPF/DKIM and checks that the source is enabled in `IoASourceState`.
2. **Consent & Filter (`DENIED_CONSENT` / `FILTERED`):** Verifies that an enabled subscription exists for the source and matches `path`/`mailbox`, `typeMatch` glob, `senderAllowlist`, and `subjectRegex`.
3. **Deduplication & Coalescing (`COALESCED`):** Deduplicates by `(source, dedup_key)` and merges bursts arriving within `settleSeconds` (or during `quietStart`–`quietEnd` quiet hours) into a single pending batch.
4. **Rate-Limit Budget (`DENIED_BUDGET`):** Enforces `maxTurnsPerHour` (default `12`) so an event storm cannot exhaust your LLM token budget.
5. **Admit & Wake (`ADMITTED`):** Persists the admitted record and dispatches the routine turn to `BotService`.

---

## 8. Configuration Files & Workspace Layout

### 8.1 Bot Workspace Directory (`$SMILE_HOME/bot/`)

Agent Hub uses `$SMILE_HOME/bot/` as its dedicated working directory (`hub.agent.workspace`), keeping autonomous state cleanly separated from your code repositories:

```
$SMILE_HOME/
├── conf/
│   ├── bots.json              # Declarative Bot Personas, Routines & Auto Review rules
│   ├── projects.json          # Registry of SMILE Studio project workspaces
│   ├── llm.json               # Shared LLM provider & model configuration
│   └── studio.json            # Studio inferenceServer, agentHub & plugins settings
└── bot/
    ├── MEMORY.md              # Consolidated long-term executive memory
    ├── memory/
    │   └── YYYY-MM-DD.md      # Daily consolidated memory logs
    ├── pages/
    │   ├── executive-brief.html
    │   ├── sales-pipeline.html
    │   ├── community-radar.html
    │   └── token-usage.html
    ├── calendar/
    │   └── frank.ics          # Local iCalendar feed for @frank
    ├── data/
    │   └── ioa_hub.mv.db      # Embedded H2 database (runs, approvals, events, state)
    └── .smile/
        ├── <persona>/
        │   └── sessions/      # Per-run conversation.jsonl transcripts
        ├── browser-state/     # Saved Playwright auth states from SECURE_HANDOFF
        └── hub/events/        # Extracted webhook & email attachments
```

### 8.2 `conf/bots.json` Schema Reference

`$SMILE_HOME/conf/bots.json` (fallback `~/.smile/bots.json`, override `-Dioa.bots.config=<path>`) is the single source of truth for Bot Personas, Routines, and Auto Review rules:

```json
{
  "primaryBot": "chief-of-staff",
  "dailyLogTime": "19:00",
  "contextProjects": [],
  "autoReviewRules": [
    {
      "id": "rule-require-outbound-approval",
      "category": "OUTBOUND_COMMUNICATION",
      "pattern": "*",
      "decision": "REQUIRE_APPROVAL"
    },
    {
      "id": "rule-require-automation-write-approval",
      "category": "AUTOMATION_WRITE",
      "pattern": "*",
      "decision": "REQUIRE_APPROVAL"
    }
  ],
  "bots": [
    {
      "botId": "frank",
      "name": "Frank — Chief of Staff (Primary Bot)",
      "description": "Primary Bot front door: consolidates end-of-day recaps, triages executive email, and guards the user's calendar.",
      "persona": "chief-of-staff",
      "enabled": true,
      "routines": [
        {
          "routineId": "frank-daily-recap",
          "name": "8:00 PM End-of-Day Consolidation & Briefing",
          "enabled": true,
          "model": null,
          "trigger": {
            "source": "timer",
            "cron": "0 20 * * *",
            "intervalSeconds": 0,
            "missedFirePolicy": "FIRE_ONCE",
            "reminderBeforeSeconds": 0,
            "settleSeconds": 0,
            "maxTurnsPerHour": 0
          },
          "workflow": {
            "kind": "SKILL",
            "target": "daily-recap",
            "instructions": "Consolidate today's memory/YYYY-MM-DD.md logs...",
            "requireActivityToday": false,
            "useContextProjects": true,
            "awaitRoutineId": "daily-log"
          },
          "policy": {
            "sessionMode": "ISOLATED_PER_RUN",
            "confirmConsequential": false,
            "approvalTtlMinutes": 0
          }
        }
      ]
    }
  ]
}
```

### 8.3 `conf/projects.json` Schema Reference

`$SMILE_HOME/conf/projects.json` (fallback `~/.smile/projects.json`, override `-Dioa.projects.config=<path>`) is updated automatically whenever SMILE Studio opens a workspace:

```json
{
  "projects": [
    {
      "name": "customer-churn",
      "path": "/Users/alice/projects/customer-churn",
      "workspaceDir": "/Users/alice/projects/customer-churn/.smile",
      "memoryDir": "/Users/alice/projects/customer-churn/.smile/memory",
      "longTermMemory": "/Users/alice/projects/customer-churn/.smile/MEMORY.md",
      "agents": ["chief-of-staff", "product-manager", "data-scientist", "architect", "java-coder", "pythonista"],
      "lastOpenedAt": "2026-10-25T18:30:00Z"
    }
  ]
}
```

### 8.4 Shared LLM Configuration (`conf/llm.json`)

When you configure AI providers and models in SMILE Studio (**File > Settings…**), Studio writes non-secret provider metadata (default service, base URLs, configured models) to `$SMILE_HOME/conf/llm.json` (fallback `~/.smile/llm.json`) and stores API keys in `SecretStore` (OS keyring or `~/.smile/credentials/*.key`, with environment variables such as `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, and `GOOGLE_API_KEY` taking highest precedence). Agent Hub reads this same `llm.json` and `SecretStore` automatically, so you only configure your LLM credentials once.

---

## 9. REST API & Operations Reference

By default, Agent Hub listens on `http://localhost:8890`. Admin endpoints require the HTTP header `Authorization: Bearer <adminToken>` (configured via `hub.admin.token` or `--admin-token`). All errors follow RFC 9457 `application/problem+json`.

### 9.1 Event Intake Endpoints

| Method & Path | Auth | Description |
|---------------|------|-------------|
| `POST /v1/events/{source}` | `X-IoA-Signature-256: sha256=<hex>` | Ingest a signed JSON webhook event for `{source}` |
| `POST /v1/events/email/{mailbox}` | `X-IoA-Signature-256: sha256=<hex>` + SPF/DKIM | Ingest a JSON or RFC 5322 MIME email for `{mailbox}` |

### 9.2 Admin & Bot Management Endpoints (`/v1/admin/*`)

| Method & Path | Description |
|---------------|-------------|
| `GET /v1/admin/bots` | Return `BotsOverview` (`BotsConfig`, per-routine runtime metadata, and `pendingApprovalCount`) |
| `PUT /v1/admin/bots` | Save a full `BotsConfig` to `conf/bots.json` and hot-reload active sources |
| `PUT /v1/admin/bots/{botId}/routines/{routineId}` | Create or update a single routine and hot-reload |
| `DELETE /v1/admin/bots/{botId}/routines/{routineId}` | Delete a routine from `conf/bots.json` |
| `POST /v1/admin/bots/{botId}/routines/{routineId}/enable` | Enable a routine |
| `POST /v1/admin/bots/{botId}/routines/{routineId}/disable` | Disable a routine |
| `POST /v1/admin/bots/{botId}/routines/{routineId}/run` | Immediately trigger a routine ("Run Now") |
| `PUT /v1/admin/projects` | Sync tracked Studio projects (`projects.json`) |
| `GET /v1/admin/runs?botId=&routineId=&persona=&status=&limit=50` | Query routine execution history (`IoABotRun`) |
| `GET /v1/admin/runs/{runId}/transcript` | Fetch the plain-text session transcript for a run |
| `POST /v1/admin/runs/{runId}/cancel` | Cancel an in-flight run |
| `GET /v1/admin/approvals?status=&runId=&kind=&limit=100` | List pending or historical approval items (`IoABotApproval`) |
| `POST /v1/admin/approvals/{id}/resolve` | Resolve a single approval (`ALLOW_ONCE`, `EDIT_AND_APPROVE`, `ALWAYS_ALLOW_RULE`, `DENY`) |
| `POST /v1/admin/approvals/batch-resolve` | Batch-resolve multiple approval IDs with optional per-item inline edits |
| `GET /v1/admin/sources` | List event sources (`timer`, `webhook`, `email`, `calendar`) and kill-switch states |
| `POST /v1/admin/sources/{name}/enable` | Enable an event source |
| `POST /v1/admin/sources/{name}/disable` | Activate the persistent kill switch for an event source (`503` on intake) |
| `GET /v1/admin/subscriptions?enabledOnly=false` | List active and disabled event subscriptions |
| `GET /v1/admin/events?source=&verdict=&since=&limit=100` | Query the append-only `IoAEventRecord` audit log |

### 9.3 Bot Pages Endpoints (`/v1/pages/*`)

| Method & Path | Description |
|---------------|-------------|
| `GET /v1/pages` | List available HTML Bot Pages (`slug`, `title`, `path`, `updatedAt`) |
| `GET /v1/pages/{slug}` | Serve `$SMILE_HOME/bot/pages/{slug}.html` as `text/html; charset=UTF-8` |

### 9.4 Health & Metrics (`/q/health`, `/q/metrics`)

- **`GET /q/health`** — Returns overall service status (`UP` or `DEGRADED`), active run count, and per-source health.
- **`GET /q/metrics`** — Returns JSON telemetry including verdict counters (`totalReceived`, `totalAdmitted`, `totalWakes`, `totalCoalescedEvents`, `coalesceRatio`, `totalFiltered`, `totalDeniedConsent`, `totalDeniedBudget`, `totalRejectedAuth`), admission latency percentiles (`latencyP50Millis`, `latencyP99Millis`, `latencyMaxMillis`), `replayBacklogCount`, `turnsLastHourByAgent`, and `queueDepthByAgent`.

---

## 10. Troubleshooting

### Agent Hub fails to start from SMILE Studio
- Verify that `$SMILE_HOME/hub/quarkus-run.jar` exists. If you are building from source across the sibling repositories, run `./gradlew :hub:quarkusBuild` in the `ioa` repository and ensure the runner JAR is copied to `$SMILE_HOME/hub/quarkus-run.jar`.
- Check if another process is already bound to port `8890`. If another service is using port `8890`, change `"port"` in `$SMILE_HOME/conf/studio.json` under `"agentHub"`.

### Routine run fails with "No LLM client configured in `${smile.home}/conf/llm.json`"
- Open **File > Settings…** in SMILE Studio, select your AI service provider, enter your API key and default model, and click **OK**. This writes `$SMILE_HOME/conf/llm.json` and stores your key in `SecretStore` so Agent Hub can authenticate with the provider.

### `daily-log` routine shows status `SKIPPED_IDLE`
- This is expected when `requireActivityToday` is enabled and the target persona had zero modified `conversation.jsonl` session files today in either `$SMILE_HOME/bot/` or any tracked Studio project in `conf/projects.json`. To force an immediate run regardless of activity, uncheck **Skip if persona had 0 sessions today** in **Configure Bots & Routines…** and click **Save to bots.json**.

### Webhook or Email POST returns `401 Unauthorized`
- Every `POST /v1/events/{source}` and `POST /v1/events/email/{mailbox}` request requires a valid `X-IoA-Signature-256: sha256=<hex>` HMAC-SHA256 header computed over the exact raw request body bytes using `hub.webhook.secret.<source>` (or `hub.webhook.secret.default`) / `hub.email.secret.<mailbox>` (or `hub.email.secret.default`).
- For email events, ensure the payload's SPF/DKIM headers do not report `fail`.

### Routine is stuck in `WAITING_APPROVAL`
- Click the amber `[Approvals (N)]` badge in the Studio status bar (or open **Agent Hub > Approvals & Run History…**) to approve or deny the pending `QUESTION`, `TOOL_CONFIRM`, `AUTOMATION_WRITE`, or `SECURE_HANDOFF` item. Note that `BATCH_DRAFT` items are staged asynchronously and do not block the routine from completing.

---

*SMILE Agent Studio is licensed under the Business Source License 1.1 (BSL 1.1) — free for personal, academic, and non-production evaluation use, converting to AGPLv3 four years after release. For commercial production licensing, see https://www.aihalo.dev/licensing.html or contact sales@aihalo.dev.*
