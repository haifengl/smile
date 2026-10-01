# Shared Agent Request Queue

Status: implemented (ioa `:agent:test`, Studio `studio/test` green)
Area: agent harness (`../ioa`) + Studio UI (`studio/`)
Author: desktop-operator

## 1. Problem

A prompt sent while a turn is already running does not behave as queued work from the
user's point of view:

- The new `Intent` looks like it fired immediately. In fact the turn is enqueued and will
  run later, but nothing in the UI says "waiting".
- Worse, the current `Intent` **steals the running turn's output**. `AgentCLI.chat()` sets
  `activeIntent = intent` at submit time (line ~991), and the session's `onNext` /
  `onStatus` callbacks append to `activeIntent` (lines ~184-207). So while turn A is
  streaming, submitting B repoints the stream into B. B's box fills with A's tokens and A's
  box goes quiet.

The user also wants to manage queued work: cancel it, reorder it, and edit it (editing
removes it from the queue and returns it to the composer).

### Why this is not just a UI bug

`../ioa` already owns the single FIFO that both the user prompt and peer-agent requests
enter (`AgentSession.queue`, an `ArrayDeque<Queued>`). `AgentSession.dispatch` runs one
turn at a time and `pump()` only starts the next when `busy` is false. So the *queue*
already exists and is already shared by both producers (`AgentSession.start(String)` for
the user; the `AgentRequest` tool via `AgentEndpoint.accept` for a peer).

What is missing:

1. The queue is **private and unobservable**. Listeners learn about an item only through
   `onQueued(AgentRequest)` (no id, no position) and `onStarted(runId, label)` (no id).
   Nothing tells the UI the queue depth or an item's position, and nothing tells the UI
   *which* item started, so the UI reconstructs this by guessing (pop order into
   `pendingTurns`).
2. There is **no way to mutate** a queued item (cancel / reorder / edit). `accept` returns
   `null` ("unused correlation id placeholder"), so items have no identity to address.
3. Studio keeps a **second, parallel queue** (`AgentCLI.pendingTurns`) that mirrors the
   session queue by convention. The two drift: cancel/reorder on the session side would not
   be reflected, and pop-order mapping breaks the moment anything is removed out of order.

## 2. Goals / non-goals

Goals

- One shared, observable request queue in `../ioa`, used by the user path and the
  agent-collaboration path alike.
- Stable identity per queued item so any front-end can address it.
- Queue mutation: cancel, reorder, and (via cancel) edit.
- Studio `Intent` shows queue state: a waiting item is visibly "Queued", positions update
  as the queue advances, and the composer shows queue depth.
- Fix the stream-attribution bug: a not-yet-started turn never receives another turn's
  output.
- Fix the `AskUserQuestion` deserialization crash (section 7).

Non-goals

- Remote delivery. The registry path stays "not available yet".
- Persisting the queue across restarts. The queue remains in-memory, per session.
- Nested/subagent queues. Subagent runs keep their current tab behaviour; only top-level
  turns occupy the request queue.

## 3. Current design (for reference)

`../ioa`

- `ioa.agent.AgentRequest` — immutable envelope `(from, to, task, artifacts, expiresAt,
  kind)`; `Kind.TASK` or `Kind.NOTICE`; `modelPrompt()` renders the prompt the model sees.
- `ioa.agent.AgentSession` — `ArrayDeque<Queued> queue`, `boolean busy`, `pump()`,
  `dispatch()`, `finish()`, `skipExpired()`. `start(prompt)` → `accept(fromUser(...))`.
  `accept(request, client, model)` appends and fires `onQueued`.
- `ioa.agent.AgentListener` — `onStarted/onNext/onStatus/onQuestion/onComplete/
  onException/onQueued/onNotice/onSkipped`.
- `ioa.agent.AgentEndpoint.accept(AgentRequest)` — returns `null` on success, else an error
  string (used by the `AgentRequest` tool).

`studio/`

- `AgentCLI` — `intents` panel of `Intent`s; `activeIntent`; `pendingTurns`
  (`ArrayDeque<Intent>`); `sessionListener()` maps session callbacks to the UI; `chat()`
  enqueues via `agent.session().accept(...)`.
- `Intent` — composer/output widget. Exposes `setStatus`, `setProgress`, `setStopAction`,
  `output()`, `editor()`. No queue affordance.
- `Workspace.openAgent` — a second `AgentListener` that selects the agent tab on
  `onQueued`.

## 4. Proposed design — `../ioa`

### 4.1 `QueuedRequest` (new record, `ioa.agent`)

```java
public record QueuedRequest(
        String id,               // stable, assigned on enqueue
        AgentRequest request,    // the envelope (TASK or NOTICE)
        LLM client,              // may be null -> session default
        Model model,             // may be null -> agent.turnModel()
        Instant enqueuedAt) { }
```

`Enqueued` (the existing private `AgentSession.Queued`) is replaced by this public record so
listeners and the UI can carry a reference to an item.

### 4.2 `AgentRequestQueue` (new class, `ioa.agent`)

Extracts the FIFO out of `AgentSession` into a reusable, thread-safe waiting list. It still
shares exactly one instance per session, so both producers continue to use one queue.

Split of responsibility: `AgentRequestQueue` owns the **waiting list** — identity, order,
lookup, snapshot, and the `cancel`/`move` mutations. `AgentSession` keeps the **turn
sequencing** (`pump`/`dispatch`/`finish`) and fires the listener events, because the decision
to start, skip (expired), or deliver a notice depends on the request kind and expiry, which
the session already owns. This keeps the async control flow in one place and makes the queue
a pure, unit-testable data structure.

```java
public final class AgentRequestQueue {
    public enum Action { ENQUEUED, STARTED, CANCELLED, REORDERED, SKIPPED, NOTICE }

    public record Event(Action action, QueuedRequest item, int position, int size) { }

    public record Snapshot(List<QueuedRequest> running, List<QueuedRequest> waiting) {
        public int size() { return waiting.size(); }
    }

    /** Appends a request, assigns an id, notifies observers, and starts it if idle. */
    public QueuedRequest enqueue(AgentRequest request, LLM client, Model model);

    /** Removes a waiting item by id. Returns the removed item, or null if absent/ running. */
    public QueuedRequest cancel(String id);

    /** Moves a waiting item by delta (-1 up, +1 down). Clamped to the waiting range. */
    public boolean move(String id, int delta);

    /** Immutable view of the queue for rendering. */
    public Snapshot snapshot();

    public boolean isBusy();
    public boolean isEmpty();

    /** Observer hooks the session forwards its own listeners to. */
    void onMutated(Consumer<Event> observer);
}
```

(No `EDITED` action: "edit" is a UI gesture that is exactly a `cancel` followed by resubmit.
The local intent is re-opened for editing by the UI, which removes it from the queue via the
same `CANCELLED` event.)

### 4.2.1 `EnqueueResult` (new record, `ioa.agent`)

Resolve the pre-existing asymmetry between the two `accept` overloads (one returns an error
string, the other a placeholder) with a single small result type:

```java
public record EnqueueResult(String id, String error) {
    public boolean ok() { return error == null; }
}
```

- `AgentEndpoint.accept(AgentRequest)` keeps returning the error string (interface contract,
  unchanged for external callers) and delegates.
- `AgentSession.accept(AgentRequest, LLM, Model)` and `start(String)` return `EnqueueResult`.
- `AgentRequestQueue.enqueue(...)` returns `QueuedRequest` (the queue layer always succeeds;
  validation lives in the session/tool).

Internals (unchanged semantics): `ArrayDeque<QueuedRequest> waiting`, one `running`
reference guarded by a private lock, `pump()` starts the head only when idle. Ids are
`UUID`-derived short strings (stable across the item's life).

`enqueue` assigns the id and the session fires `ENQUEUED` **before** `accept` returns it.
Delivery goes through the session's event dispatcher (see 4.5), so a host can guarantee the
caller binds the id before `ENQUEUED` runs by installing a deferring dispatcher.

### 4.3 `AgentSession` changes

- Delegates its `queue`/`busy`/`pump`/`finish` logic to an `AgentRequestQueue`.
- `accept(AgentRequest request, LLM client, Model model)` now **returns `EnqueueResult`**
  carrying the queue id. The one-arg `accept(AgentRequest)` keeps its error-string contract
  for `AgentEndpoint` and delegates to it.
- `start(String prompt)` returns `EnqueueResult`.
- New: `cancel(String id)`, `move(String id, int delta)`, `queued()` (snapshot),
  `queuePosition(String id)`, and `setEventDispatcher(Consumer<Runnable>)` (section 4.5).
- `isBusy()` keeps meaning "running or waiting".
- `skipExpired` and the expiry-notice path stay; they now emit `SKIPPED`/`NOTICE` events.

### 4.4 `AgentListener` changes

Add one queue-centric callback and keep the existing ones:

```java
/** A queue mutation: enqueue, start, cancel, reorder, skip, or notice. */
default void onQueueChanged(AgentRequestQueue.Event event) { }
```

- `onQueued(AgentRequest)` is kept and emitted for `ENQUEUED` for callers that only care
  that something arrived (e.g. `Workspace` tab selection). Both signals fire.
- `onStarted(null, label)` keeps meaning "a top-level turn is now running"; Studio
  correlates it with the preceding `STARTED` event's id.
- `onSkipped` is kept and also surfaces as a `SKIPPED` event.

Rationale for keeping the old callbacks: `Workspace` and existing tests use them; the
change stays additive, which keeps the cross-repo blast radius small.

### 4.5 Ordering contract and the event dispatcher (option A+B)

The race: `accept`/`start` fire `ENQUEUED` on the caller's thread *before* returning the id,
so a consumer that handles events inline sees `ENQUEUED` while its `id → row` map is still
empty, and an unknown-id rule that creates a row then produces a duplicate. A second window
opens if the consumer delivers `ENQUEUED` and `STARTED` through different queues and
`STARTED` overtakes `ENQUEUED`.

Two mechanisms close it:

- **A — dispatcher.** `AgentSession.setEventDispatcher(Consumer<Runnable>)` routes every
  `onQueueChanged` through a caller-supplied dispatcher; the default runs inline (unchanged
  for headless callers and tests). A host installs a deferring, FIFO dispatcher —
  `SwingUtilities::invokeLater` in Studio — so events are delivered after `accept` returns
  and `ENQUEUED` stays ahead of `STARTED`.
- **B — ENQUEUED is authoritative.** A consumer must tolerate an unknown id and create on
  `ENQUEUED`, so a consumer that does *not* defer degrades to correctness instead of
  duplication. Documented on `AgentListener.onQueueChanged`.

Studio does both: it binds `result.id()` synchronously after `accept` (an optimization) and
sets the dispatcher (the guarantee). Ordering the turn itself relies on `STARTED` being
fired before `agent.stream` is invoked; since both happen on the caller thread (or, with a
dispatcher, on the dispatcher thread in order), `STARTED` precedes the first `onNext`. Studio
additionally buffers any `onNext` that arrives before promotion, so a synchronous LLM cannot
drop the first tokens.

`onQueueChanged` is the only callback routed through the dispatcher; the coarse `onQueued`
and the stream callbacks keep their own threads.

## 5. Proposed design — Studio UI

### 5.1 `Intent` — queue affordances

New state and methods on `Intent`:

```java
public void showQueued(int position, int size);   // badge: "Queued · 2 of 3"
public void clearQueued();                          // drop the badge (started/cancelled)
public void setQueueControls(Runnable cancel, Runnable edit,
                             Runnable moveUp, Runnable moveDown);
public void setQueueControlsEnabled(boolean canMoveUp, boolean canMoveDown);
```

Rendering (composer footer, right side next to the progress pane):

- A small badge label (`⏳ Queued · 2 of 3`) while the item waits. It sits in the
  `progressPane` area, mutually exclusive with the progress bar (a waiting turn is not
  running, so the stop button is hidden and the badge shown).
- Controls, only while waiting:
  - **Edit** `✏️` — cancel this queued item and re-open it as an editable composer with its
    text (section 5.3). Shown **only for local user prompts**; hidden for peer requests.
  - **Cancel** `❌` — cancel this queued item.
  - **Move up** `⬆️` / **Move down** `⬇️` — reorder among waiting items; disabled at the
    ends.
- When the turn starts: `clearQueued()`, then `setProgress(true)` + `setStatus("Thinking…")`
  (existing path).

Strings go through the `Intent` bundle (section 6).

### 5.2 `AgentCLI` — id-based binding

Replace the pop-order `pendingTurns` with an id-keyed map and let the session drive the UI:

```java
private final Map<String, Intent> queuedIntents = new HashMap<>();
private Intent lastUserIntent;   // the composer Intent the user just submitted
```

- `chat(intent, prompt)`:
  1. resolve model / set conversation params (unchanged),
  2. `lastUserIntent = intent;`
  3. `String id = agent.session().accept(AgentRequest.fromUser(...), client, model);`
  4. `queuedIntents.put(id, intent); intent.showQueued(position, size);`

  It **no longer sets `activeIntent`.** `activeIntent` is set only by `onStarted`.
- `onQueueChanged(Event e)` (marshalled with `invokeLater`):
  - `ENQUEUED`: if `queuedIntents` already has the id, just `showQueued(position,size)`;
    otherwise this is a peer request — `openRequest(e.item().request().modelPrompt())`, then
    `queuedIntents.put(id, intent)`, `intent.showQueued(...)`, `setQueueControls(...)`.
  - `STARTED`: `Intent intent = queuedIntents.remove(id); if (intent != null) activeIntent =
    intent;` then `activeIntent.clearQueued(); activeIntent.setProgress(true);
    activeIntent.setStatus("Thinking…");` — the existing `onStarted` body, now driven by the
    id.
  - `CANCELLED` / `SKIPPED`: remove from map; show a reason line; `clearQueued()`.
  - `REORDERED`: re-`showQueued` every waiting intent with its new position.
  - `NOTICE`: unchanged (open a read-only intent).
- `onNext` / `onStatus` / `onQuestion` / `onComplete` / `onException` keep using
  `activeIntent`. Because `activeIntent` is now only ever the *running* turn, output can no
  longer leak into a queued intent. This is the core fix.
- `onStarted(runId != null, …)` (subagent tabs) is unchanged.

The existing `pendingTurns` field and its four uses are deleted.

### 5.3 Edit / cancel / reorder semantics

- **Cancel (waiting item).** `session.cancel(id)`. The session removes it and emits
  `CANCELLED`. If the item was a peer TASK (`from` non-blank), the session also sends a
  NOTICE back to the sender: `"Request from @me was cancelled before @you started it."`
  (mirrors the existing expiry notice). The local intent shows the same line and drops the
  badge.
- **Edit (waiting item).** Available **only for local user prompts** (`request.from()` blank).
  A waiting item that arrived from a peer agent (`from` non-blank) gets **no Edit control** —
  it may be cancelled or reordered, but not rewritten and re-homed as a local prompt.
  For a local item, Edit is exactly a cancel plus a re-open: the UI re-enables that `Intent`
  in place (`setEditable(true)`), restores focus, and keeps its text so the user can change
  it. On Enter it is submitted again → a fresh id, a fresh queue position. This matches
  "while editing, it is removed from the queue".
- **Reorder (waiting items).** `session.move(id, ±1)`. Only waiting items move; the running
  item is never reorderable and is not shown a position. Ends disable the arrows.
- **Running item.** Not cancellable from here; the existing stop button
  (`setStopAction` → `INTERRUPTED`) is unchanged. Editing is not offered while running.

Edge case to verify in tests: after an edit, two editable `Intent`s can momentarily exist
(the edited one and the trailing empty composer). `composerIndex()` already skips trailing
editable intents, so insertion stays correct, but focus and "which one Enter submits" must
be pinned by a test.

### 5.4 `Workspace`

`openAgent`'s listener can stay as-is (`onQueued` still fires). Optionally switch it to
`onQueueChanged` with `action == ENQUEUED` to avoid selecting the tab on unrelated
mutations; note it in the change but keep behaviour identical.

## 6. Internationalization

New keys in `studio/src/main/resources/smile/studio/cli/Intent.properties` **and all five
localized bundles** (`en_US`, `zh_CN`, `ja_JP`, `fr_FR`, `es_ES`):

| Key | en_US value |
|---|---|
| `Queued` | `Queued` |
| `QueuedPosition` | `Queued · {0} of {1}` |
| `CancelQueued` | `Cancel this queued request` |
| `EditQueued` | `Edit this queued request` |
| `MoveQueuedUp` | `Move this request earlier in the queue` |
| `MoveQueuedDown` | `Move this request later in the queue` |
| `CancelledQueued` | `Cancelled before it started.` |
| `QueueDepth` | `{0} queued` |

A key present only in the base bundle is a `MissingResourceException` at runtime; the
existing `SearchResourceBundleTest` pattern is the model to extend so a missing translation
fails a test rather than at runtime.

## 7. Fix: `AskUserQuestion` crash on object-shaped choices

### Root cause

The tool schema declares `Question.choices` as `array of string`
(`public List<String> choices;`). The model instead returns an array of objects —
the shape other agent CLIs use:

```json
{"choices": [{"label": "ioa harness", "description": "..."}]}
```

Jackson then fails in `StructuredOutputs.responseTypeFromJson` with
`MismatchedInputException: Cannot deserialize value of type java.lang.String from Object
value` and the whole tool call aborts (the failure is logged by `Tool.callSafely`, so the
turn continues but the question is never asked).

This is a harness bug, not a model bug: the tool is fragile to a plausible, common payload.

### Fix (primary)

Make `Question.choices` tolerant. Keep the field type `List<String>` so every call site
(`Question.GUI`, `AskUserQuestion`, `ExitPlanMode`, tests) is unchanged, and add a custom
deserializer that accepts either shape and flattens objects to their label:

```java
@JsonProperty(required = true)
@JsonPropertyDescription("The available choices for the user to select, as plain strings "
        + "(not objects). Users can also pick 'Other' for custom input.")
@JsonDeserialize(using = ChoiceListDeserializer.class)
public List<String> choices;
```

`ChoiceListDeserializer` accepts, per element:

- a JSON string → itself;
- a JSON object → its `label` field, else `text`, else `title`, else `value`, else its
  first string property; a missing object contributes nothing;
- `null` → kept as `null` (the tool already normalizes null choices).

It must tolerate unknown object fields (they are dropped) and must not throw on a
well-formed-but-unexpected element.

### Also fix the schema description

The `@JsonPropertyDescription` is the model's instruction. Spell out "plain strings, not
objects" and, in `AskUserQuestion`'s `@JsonClassDescription`, add one line:
*"`choices` is an array of plain strings; do not wrap each choice in an object."* This
reduces recurrence.

### Verification / fallback

`function.arguments(Class)` is the OpenAI SDK's converter. If, on verification, its mapper
turns out not to honour `@JsonDeserialize` (annotations are normally honoured regardless of
mapper, but the SDK builds its own), the fallback is a tolerant pre-parse in the
`AskUserQuestion` branch of each client (`ChatCompletions`, `OpenAI`, `Anthropic`,
`GoogleGemini`): parse the raw argument string with `ToolUtils.mapper` into a normalizing
`JsonNode`, flatten `choices`, then bind. A static
`AskUserQuestion.fromJson(String)` helper keeps that in one place.

Either way, add a regression test using the exact failing payload (section 8).

## 8. Testing

`../ioa`

- `AgentRequestQueueTest` (new): enqueue assigns unique ids and reports positions; cancel
  removes by id and returns the item; cancel of a running id is refused; `move` reorders
  and clamps at the ends; `snapshot()` reflects running vs waiting; observers receive
  `ENQUEUED/STARTED/CANCELLED/REORDERED`.
- `AgentSessionTest` (extend): `accept(...)` returns a non-null id; a second enqueue while
  the first runs does not start until the first completes (existing
  `queuedRequestsStartOneAtATime` still passes); cancelling a peer's waiting task delivers a
  NOTICE to the sender; `onQueueChanged` fires with the expected action/position;
  `eventDispatcherDefersQueueEventsUntilAfterAcceptReturns` pins option A (nothing is
  delivered before `start` returns, then `ENQUEUED` precedes `STARTED`); and
  `startedEventPrecedesTurnOutput` pins the ordering that protects the first tokens.
- `AskUserQuestionToolTest` (extend): a `Question` deserialized from
  `{"choices":[{"label":"A","description":"b"},{"label":"B"}]}` yields
  `["A","B"]`; the string form still yields `["A","B"]`; mixed forms work.

`studio/` (`sbt studio/test`)

- `IntentTest` (extended, run): queue keys exist in all six bundles; `showQueued(2,3)` shows
  the badge and stops the progress bar; `setProgress(true)` clears the badge; `setQueueControls`
  hides Edit for a peer request and the arrows follow the position; the four controls invoke
  their actions.
- The `AgentCLI` end-to-end path is covered by the ioa `AgentSessionTest` (queue order, cancel
  notice, ids, events) plus the `IntentTest` UI tests. A dedicated `AgentCLIQueueTest` is not
  added because `AgentCLI` needs a real `Agent` and the Swing `Workspace`, which the Studio
  tests do not scaffold. The output-attribution and cancel/edit/reorder behaviour is verified
  by a manual smoke test (submit A, submit B while A streams, confirm B stays queued with
  position 1 and A's tokens never reach B; cancel, reorder, and edit B).

## 9. Cross-repo build & sequencing

`../ioa` is closed-source and its jars (`studio/lib/ioa-agent.jar`, `ioa-aid.jar`) are
unmanaged local builds. Any change here means:

1. implement + test in `../ioa` (`./gradlew :agent:test`);
2. rebuild the jar so it lands in `../smile/studio/lib` (the `copyJarToDist` step copies it
   on `:agent:build`; that step needs `studio/lib` to exist);
3. build/run Studio (`sbt studio/test`, `sbt studio/stage`).

Changing a SMILE public API can break `../ioa` too; here the only SMILE-side change is
Studio-internal plus new resource keys, so the direction is `../ioa → studio`, not back.

Do `../ioa` first, then Studio, then wire up the UI.

## 10. Decisions and remaining risks

Decisions taken during review:

- **`accept` return type cleaned up.** Both paths return `EnqueueResult(id, error)`; the
  `AgentEndpoint` interface keeps its string error for external callers and delegates.
- **Peer requests are cancel/reorder-only.** Edit is offered only for local user prompts.
- **Position label wording.** `Queued · 2 of 3` (`{0} of {1}`).
- **Event ordering addressed (A+B).** A `setEventDispatcher` hook (A) plus "ENQUEUED is
  authoritative" (B) replace the old assumption that a consumer marshals events itself. See
  4.5.

Remaining risks:

- **Dispatcher must be FIFO.** Option A only preserves `ENQUEUED` → `STARTED` ordering if
  the installed dispatcher preserves task order. `invokeLater` on one EDT does; a
  concurrent executor would not. Documented on `setEventDispatcher`.
- **`studio/` test execution.** After the A+B changes, `sbt` could not run the suite: a
  stale `sbt` held the per-user boot-server pipe, and the refreshed `sbt` runs kept pulling
  in the whole suite and hanging on the WIP `ScalaKernelTest#testTsneScript`. `IntentTest`
  was instead compiled fresh against the new `ioa` jar and the updated resource bundles and
  executed with a small standalone runner: **19/19 pass**, including `queueKeysExistInEveryLocale`
  and the queue-badge/controls tests. A single `sbt studio/test` once no other sbt is active
  is still worth doing for the rest of the module.
