# SMILE — ONNX Runtime GenAI (`smile.onnx.genai`)

Idiomatic Java wrappers over the [ONNX Runtime GenAI](https://onnxruntime.ai/docs/genai/)
C API, built on Panama FFM bindings in `smile.onnx.genai.foreign`.

GenAI has **no one-shot `generate()`**: the loop is
`AppendTokens` → `while (!IsDone) GenerateNextToken`. This package mirrors that
contract and adds a `LanguageModel` adapter for future smile-serve wiring.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Java | 25 (project standard) with Panama FFM |
| Native libs | `onnxruntime` + `onnxruntime-genai`; set `ONNXRUNTIME_NATIVE_PATH` / `ONNXRUNTIME_GENAI_NATIVE_PATH` |
| JVM flag | `--enable-native-access=ALL-UNNAMED` (Gradle test/run already set this) |
| Model | A GenAI model directory with `genai_config.json` (e.g. Phi-3 CPU) |

Download GenAI binaries from the
[onnxruntime-genai releases](https://github.com/microsoft/onnxruntime-genai/releases)
and ORT from the
[onnxruntime releases](https://github.com/microsoft/onnxruntime/releases).

### Native libraries

Both shared libraries must be loadable. With pip (`onnxruntime-genai-cuda` /
`onnxruntime-genai`), typical Windows locations are:

| Library | Directory |
|---|---|
| `onnxruntime.dll` | `…\site-packages\onnxruntime\capi` |
| `onnxruntime-genai.dll` | `…\site-packages\onnxruntime_genai` |

**Use environment variables** (recommended). `GenAI` preloads these DLLs by
absolute path before FFM lookup, which also avoids an older
`C:\Windows\System32\onnxruntime.dll` winning the Windows search order:

| Variable | Meaning |
|---|---|
| `ONNXRUNTIME_NATIVE_PATH` | Directory containing `onnxruntime` (e.g. pip `capi`) |
| `ONNXRUNTIME_GENAI_NATIVE_PATH` | Directory containing `onnxruntime-genai` |
| `SMILE_ONNX_GENAI_MODEL` | GenAI model directory (`genai_config.json`); needed for native model tests |
| `SMILE_ONNX_GENAI_PROVIDER` | `auto` (default cascade), `cuda`, `npu`, `ryzenai`/`hybrid`, `openvino`, `qnn`, `dml`/`directml`, or `cpu` |

```powershell
$ort = "$env:LOCALAPPDATA\Python\pythoncore-3.14-64\Lib\site-packages\onnxruntime\capi"
$genai = "$env:LOCALAPPDATA\Python\pythoncore-3.14-64\Lib\site-packages\onnxruntime_genai"
$model = "C:\path\to\phi3-mini-4k-instruct\cpu_and_mobile\cpu-int4-rtn-block-32-acc-level-4"

$env:ONNXRUNTIME_NATIVE_PATH = $ort
$env:ONNXRUNTIME_GENAI_NATIVE_PATH = $genai
$env:SMILE_ONNX_GENAI_MODEL = $model
# optional: $env:SMILE_ONNX_GENAI_PROVIDER = "auto"   # or "cuda" / "cpu"
```

`Model.open` / `SimpleGenAI.open` / `GenAiChatModel.open` run an accelerator
cascade when preference is `auto`: **CUDA → RyzenAI → OpenVINO NPU → QNN →
DirectML (Windows) → CPU**. Each step is attempted only when matching EP natives
are present; Java failures fall through. On non-Windows, DirectML is skipped
automatically. Classical **Vitis AI** (general ONNX on AMD NPU) is configured on
`smile.onnx.SessionOptions`, not via this GenAI preference.
`Model.of` always uses `genai_config.json` providers unchanged.

Check availability:

```java
if (!GenAI.available()) {
    // natives missing — skip or fail fast
}
GenAI.init(); // throws GenAIException with install hints if load fails
```

---

## Package map

| Class | Role |
|---|---|
| `GenAI` | Load probe, telemetry, logging, `shutdown()` |
| `GenAIException` | Unchecked native / wrapper errors |
| `Config` | Providers + JSON overlay |
| `Model` | Loaded GenAI model |
| `Tokenizer` / `TokenizerStream` | Encode / decode / chat template / streaming decode |
| `Sequences` | Batch of token-id sequences |
| `GeneratorParams` | Search options (`max_length`, `temperature`, …) |
| `Generator` | Token loop (`Iterable<Integer>`) |
| `SimpleGenAI` | Convenience prompt → text (+ optional `Consumer<String>`) |
| `Tensor` / `NamedTensors` | Inputs for `Generator.setInputs` |
| `Images` / `Audios` / `MultiModalProcessor` | Vision / audio path |
| `Adapters` | LoRA load / activate |
| `GenAiChatModel` | `smile.llm.LanguageModel` adapter |

---

## Quick start

```java
import smile.onnx.genai.*;

try (var genai = SimpleGenAI.of("models/phi-3-mini-4k-instruct-cpu")) {
    try (var params = genai.createGeneratorParams()) {
        params.setSearchOption("max_length", 256)
              .setSearchOption("temperature", 0.8);
        String reply = genai.generate(params, "Write a haiku about Java.", System.out::print);
    }
}
```

Canonical low-level loop:

```java
try (var model = Model.of(modelDir);
     var tokenizer = model.createTokenizer();
     var params = model.createGeneratorParams();
     var generator = Generator.of(model, params);
     var stream = tokenizer.createStream()) {
    params.setSearchOption("max_length", 128);
    generator.appendTokens(tokenizer.encodeToArray(prompt));
    while (!generator.isDone()) {
        generator.generateNextToken();
        System.out.print(stream.decode(generator.getLastToken(0)));
    }
}
```

---

## Chat / serve seam (`GenAiChatModel`)

```java
try (var chat = GenAiChatModel.of("models/phi-3-mini-4k-instruct-cpu")) {
    var completion = chat.chat(
            new Message[]{ new Message(Role.user, "Hello!") },
            64, 0.7, 0.9, false, 0,
            new GenerationListener() {
                @Override public void onText(String chunk) {
                    System.out.print(chunk);
                }
            },
            null);
}
```

`GenAiChatModel`:

- `family()` → `"onnx/genai"`
- Builds GenAI chat-template JSON from `smile.llm.Message[]` (including `tools`)
- Streams via `GenerationListener.onText`; strips `<think>…</think>` and reports
  `onThinkingTokens` while streaming
- Honors `BooleanSupplier cancelRequested` between tokens
- Multimodal: local image/audio file paths through `MultiModalProcessor`
- Final content is sanitized with `AssistantTextSanitizer` (also via serve’s
  `ToolCallPostProcessor` for structured `tool_calls`)

### smile-serve OGA fallback

`ChatService` keeps Torch for **CUDA + builtin Llama/Qwen**. Otherwise it tries
ORT GenAI:

1. Local / HF snapshot with `genai_config.json` → `GenAiChatModel.open` (no Olive)
2. Else allowlisted plain HF (`GenAISupportedModels`) + Olive `auto-opt` → open
3. Else chat stays unavailable (HTTP 503)

| Artifact | Location | Override |
|---|---|---|
| HF checkpoints / GenAI-ready repos | Hub cache (`HF_HOME` / `HF_HUB_CACHE`) | same as `huggingface_hub` |
| Olive-converted GenAI | `{SMILE_CACHE}/olive/...` | `smile.chat.oga.cache-dir` |

Serve config (`smile.chat.oga.*`): `enabled`, `precision` (`auto` = FP8 on CUDA
else int4), `cache-dir`, `olive-command`, optional `device` / `provider`.
Olive `--device`/`--provider` follow `GenAI.resolveOliveTarget()` (same EP
cascade as `Model.open`, honor `SMILE_ONNX_GENAI_PROVIDER`).

**Tools I/O (Phase 1):** OpenAI `tools` reach the GenAI chat template; completions
are post-processed to structured `tool_calls` (`JsonToolCallParser` /
Qwen3 XML). No agent loop / tool execution.

**Multimodal (Phase 2):** serve materializes data-URL / HTTP media to temp files
(`GenAiMediaMaterializer`) before `GenAiChatModel` multimodal chat.

Continuous batching / GenAI `OgaEngine` remains out of scope.

---

## CUDA / NPU providers

`Model.open` cascade (`SMILE_ONNX_GENAI_PROVIDER=auto`):

```java
try (var model = Model.open(modelDir)) {
    // model.provider() is cuda | ryzenai | openvino | qnn | dml | default
}
```

| Preference | Behavior |
|---|---|
| `auto` | CUDA → RyzenAI → OpenVINO NPU → QNN → DirectML (Windows) → CPU |
| `cuda` / `gpu` | CUDA only (throw if fails) |
| `npu` | Skip CUDA; try RyzenAI → OpenVINO → QNN |
| `ryzenai` / `hybrid` | AMD OGA RyzenAI only (hybrid or NPU-only **model** determines mode) |
| `openvino` | OpenVINO with `device_type=NPU` |
| `qnn` | Qualcomm QNN |
| `dml` / `directml` | Windows DirectML only (throw if fails / not Windows) |
| `cpu` | No accelerator attempts (CI) |

**AMD Ryzen AI (LLMs):** hybrid (NPU+iGPU) vs NPU-only is which model package you
load (AMD HF hybrid vs NPU collections). Needs
`onnxruntime-genai-directml-ryzenai` (or Ryzen AI MSI), NPU drivers, and a GPU
driver for hybrid.

**Windows DirectML:** general GPU via `onnxruntime_providers_dml` (iGPU/dGPU).
Tried on `auto` after NPU candidates and before CPU; inactive off Windows.

**AMD Vitis AI (general ONNX on NPU):** use
`smile.onnx.SessionOptions.appendVitisAiExecutionProvider()` — not this GenAI
cascade.

**Out of scope for GenAI cascade:** ROCm/MIGraphX, NvTensorRtRtx (TensorRT-RTX).

Or set GenAI providers explicitly:

```java
try (var config = Config.of(modelDir)) {
    config.clearProviders()
          .appendProvider("cuda")
          .setProviderOption("cuda", "device_id", "0");
    try (var model = Model.of(config)) { ... }
}
```

---

## Tests

Unit tests under `deep/src/test/java/smile/onnx/genai/`:

- `GenAIExceptionTest` / `GenAIProviderPreferenceTest` — no natives required
- `GenAINativeTest` — needs natives; model tests need `SMILE_ONNX_GENAI_MODEL`

GitHub Actions (`.github/workflows/ci.yml`) installs CPU `onnxruntime` /
`onnxruntime-genai`, sets `ONNXRUNTIME_NATIVE_PATH` /
`ONNXRUNTIME_GENAI_NATIVE_PATH`, and forces `SMILE_ONNX_GENAI_PROVIDER=cpu`
(runners have no GPU). Without `SMILE_ONNX_GENAI_MODEL`, model cases are skipped;
`sequencesRoundTrip` still runs when natives load.

Set the environment variables in the shell, then run tests. Both sbt and Gradle
fork the test JVM and inherit these variables. Tests call `Model.open`,
so they pick up CUDA when the GenAI CUDA EP works and otherwise use CPU:

```powershell
$env:ONNXRUNTIME_NATIVE_PATH = $ort
$env:ONNXRUNTIME_GENAI_NATIVE_PATH = $genai
$env:SMILE_ONNX_GENAI_MODEL = $model
# $env:SMILE_ONNX_GENAI_PROVIDER = "auto"  # default; use "cpu" to force CPU

# If you change SMILE_* after a prior Gradle run, avoid a stale configuration-cache
# entry that pinned the old env:
#   ./gradlew --no-configuration-cache :deep:test --tests "smile.onnx.genai.*"
sbt "deep/testOnly smile.onnx.genai.*"
# or
./gradlew :deep:test --tests "smile.onnx.genai.*"
```

Do **not** pass native/model settings as `sbt "-D…"` or `sbt "-J-D…"` — those
do not configure the forked test JVM reliably (and `-J-D` fails under sbt 2).

Without `SMILE_ONNX_GENAI_MODEL`, only library-level checks run. Point it at a
**real** GenAI model directory (e.g. Phi-3 CPU) for full generate smokes.
Tiny onnxruntime-genai fixtures under `test/models/` (Identity `dummy_*.onnx`)
only support load / tokenize / create-generator, not a full decode loop.

---

## Shutdown

Call `GenAI.shutdown()` only after every GenAI `AutoCloseable` is closed.
The next GenAI API call re-initializes the library.
