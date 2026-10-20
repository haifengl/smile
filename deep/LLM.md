# SMILE — Large Language Models (`smile.llm`)

The `smile.llm` package provides the building blocks and complete inference
stacks for decoder-only LLMs on top of the same FFM-backed LibTorch bridge used
by `smile.deep`. It ships a LLaMA-3 implementation, a Qwen3.5 hybrid
(Gated DeltaNet + gated full attention) implementation, a tiktoken BPE
tokenizer, a continuous-batching inference engine, KV-cache / radix prefix
reuse, weight quantization, and tensor parallelism.

For the shared tensor / layer / optimizer API, see [README.md](README.md).
For ONNX-based generative models, see [ONNX_GENAI.md](ONNX_GENAI.md).

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Package Map](#package-map)
3. [Core Types](#core-types)
4. [Tokenizer (`smile.llm.tokenizer`)](#tokenizer-smilellmtokenizer)
5. [Positional Encodings](#positional-encodings)
6. [Transformer Primitives](#transformer-primitives)
7. [LLaMA (`smile.llm.model.llama`)](#llama-smilellmmodelllama)
8. [Qwen3.5 (`smile.llm.model.qwen`)](#qwen35-smilellmmodellqwen)
9. [MiniMax-M3 (`smile.llm.model.minimax`)](#minimax-m3-smilellmmodelminimax)
10. [Inference Engine (`smile.llm.engine`)](#inference-engine-smilellmengine)
11. [KV Cache & Radix Prefix Reuse (`smile.llm.cache`)](#kv-cache--radix-prefix-reuse-smilellmcache)
12. [Attention Backends (`smile.llm.attention`)](#attention-backends-smilellmattention)
13. [Quantization (`smile.llm.quant`)](#quantization-smilellmquant)
14. [Tensor Parallelism (`smile.llm.parallel`)](#tensor-parallelism-smilellmparallel)
15. [Tool Calling & Chat Templates](#tool-calling--chat-templates)

---

## Prerequisites

`smile.llm` runs on the same native stack as `smile.deep`: the `smile_torch`
shared library and its LibTorch dependencies must be discoverable by the
platform loader, and FFM access must be enabled
(`--enable-native-access=ALL-UNNAMED`). See
[Prerequisites & Dependencies](README.md#prerequisites--dependencies) in the
module README for the per-platform details.

GPU inference additionally requires the CUDA-enabled LibTorch libraries. On
Ampere or newer hardware the model loads in BFloat16; on older GPUs Float16 is
used. CPU inference runs in Float32.

---

## Package Map

```
smile.llm
├── Role, Message, ContentPart, TextPart, ImageUrlPart, VideoUrlPart, AudioUrlPart
├── ChatCompletion, FinishReason, ChatOptions, ToolChoice
├── ToolDefinition, FunctionDefinition, ToolCall, FunctionCall
├── LanguageModel.java     Common façade for chat-capable decoder LLMs
├── GenerationListener.java, GenerationListeners.java
├── tokenizer/     Tokenizer interface + Tiktoken (BPE) implementation
├── transformer/   Shared primitives: Attention, FeedForward,
│                  PositionalEncoding, RotaryPositionalEncoding
├── model/llama/   LLaMA-3: Llama, LlamaModel, LlamaBlock,
│                  GroupedQueryAttention, LlamaModelArgs, Tokenizer
├── model/qwen/    Qwen3.5 hybrid text stack + vision tower
├── engine/        Continuous-batching runtime: InferenceEngine, ModelExecutor,
│                  GenerationRequest, GenerationHandle, speculative decoding
├── cache/         KvCachePool, RadixCache, KV layout / metadata
├── attention/     Attention backend selection (torch-native / FlashInfer)
├── quant/         FP8 / NVFP4 / Marlin weight quantization
├── parallel/      Tensor-parallel primitives
├── checkpoint/    SafeTensors loader tuning
├── template/      ChatTemplate interface + Qwen3 template
└── tool/          Tool-call parsers (JSON, Qwen3 XML)
```

---

## Core Types

| Type | Kind | Purpose |
|---|---|---|
| `Role` | `enum` | `system`, `user`, `assistant`, `tool` |
| `Message` | `record` | A dialog turn — role + ordered `ContentPart`s, plus optional tool fields |
| `ContentPart` | sealed interface | `TextPart`, `ImageUrlPart`, `VideoUrlPart`, `AudioUrlPart` |
| `FinishReason` | `enum` | `stop`, `length`, `tool_calls` |
| `ChatCompletion` | `record` | Inference result — text, token arrays, log-probs, finish reason, tool calls |
| `ChatOptions` | `record` | Per-request tool-calling options (`tools`, `toolChoice`, `parallelToolCalls`) |
| `LanguageModel` | interface | Common façade: `encodeChat`, `generate`, `chat` |

```java
// Build a simple conversation
Message[] dialog = {
    Message.system("You are a helpful assistant."),
    Message.user("What is the capital of France?")
};

// Inspect a completion
ChatCompletion reply = llama.chat(dialog, 256, 0.6, 0.9, false, 0L, null);
System.out.println(reply.content());   // "The capital of France is Paris."
System.out.println(reply.reason());    // FinishReason.stop
```

`Message` also carries multimodal parts and tool fields:

```java
// Multimodal turn: text interleaved with an image
Message turn = new Message(Role.user, List.of(
        new TextPart("What is in this picture?"),
        new ImageUrlPart("https://example.com/cat.jpg")));

// Assistant turn that requested a tool, and the tool result that answers it
Message call   = new Message(Role.assistant, List.of(), List.of(toolCall), null, null);
Message result = new Message(Role.tool, "{\"temp\": 21}", null, toolCall.id(), null);
```

`LanguageModel` is the single-prompt façade. Concurrent multi-request
scheduling belongs in `InferenceEngine`, not on this interface.

```java
public interface LanguageModel {
    String family();                       // e.g. "meta/llama3", "alibaba/qwen3.5"
    String name();
    int maxSeqLen();
    int[] encodeChat(Message... dialog);
    ChatCompletion generate(int[] prompt, int maxGenLen, double temperature,
                            double topp, boolean logprobs, long seed,
                            GenerationListener listener, BooleanSupplier cancelRequested);
    ChatCompletion chat(Message[] dialog, int maxGenLen, double temperature,
                        double topp, boolean logprobs, long seed,
                        GenerationListener listener, BooleanSupplier cancelRequested);
}
```

Passing a non-null `cancelRequested` supplier enables cooperative cancel: the
implementation checks it between decode steps and throws
`CancellationException` when it returns `true` (KV is still unbound in
`finally`).

---

## Tokenizer (`smile.llm.tokenizer`)

`Tokenizer` is the encoding/decoding interface. `Tiktoken` is the BPE
implementation compatible with OpenAI's tiktoken library (used by LLaMA-3):

```java
import smile.llm.tokenizer.Tiktoken;

Tiktoken tok = new Tiktoken(
        pattern,           // regex splitting pattern
        specialTokens,     // map of special-token string → rank
        ranks,             // merged BPE vocabulary (bytes → rank)
        bosId, eosId       // BOS / EOS token IDs
);

// Encode with BOS+EOS
int[] ids = tok.encode("Hello, world!", true, true);

// Decode back to text
String text = tok.decode(ids);

// Vocabulary size
int vocab = tok.size();
```

`Tiktoken` handles:

- BPE merge table look-ups for regular tokens
- Special token injection with a separate regex guard
- UTF-8-safe decoding (with a strict `tryDecode` variant that throws
  `CharacterCodingException` on invalid byte sequences)

`HuggingFaceBpeVocab` loads a HuggingFace `tokenizer.json` vocabulary into the
same representation.

---

## Positional Encodings

Two implementations are provided:

| Class | Algorithm | Used by |
|---|---|---|
| `PositionalEncoding` | Sinusoidal (sin/cos, fixed) | Original Transformer |
| `RotaryPositionalEncoding` | RoPE (complex-number rotation) | LLaMA |

```java
// Sinusoidal — precomputes a [maxLen × dim] table once
PositionalEncoding pe = new PositionalEncoding(512, 2048);
Tensor out = pe.forward(embeddingTensor);   // adds positional signal

// RoPE — called inside Attention.forward()
RotaryPositionalEncoding rope = new RotaryPositionalEncoding(headDim, maxSeqLen);
```

Qwen3.5 uses a partial / interleaved multi-axis RoPE (`InterleavedMRope`,
`PartialRotaryEncoding`) for its multimodal positions.

---

## Transformer Primitives

`smile.llm.transformer` holds the shared building blocks reused by both model
families:

| Class | Role |
|---|---|
| `Attention` | Scalar-dot-product attention over the selected backend |
| `FeedForward` | SwiGLU-style MLP block |
| `PositionalEncoding` | Sinusoidal encoding |
| `RotaryPositionalEncoding` | RoPE |

---

## LLaMA (`smile.llm.model.llama`)

A full LLaMA-3 inference implementation:

| Class | Role |
|---|---|
| `LlamaModelArgs` | Hyperparameter record; loaded from `params.json` / HF `config.json` |
| `LlamaModel` | Top-level module — embedding + N × `LlamaBlock` + output projection |
| `LlamaBlock` | Single decoder block: `GroupedQueryAttention` + `FeedForward` + RMS norms |
| `GroupedQueryAttention` | Grouped-query attention with KV-cache and RoPE |
| `Tokenizer` (llama) | Thin wrapper around `smile.llm.tokenizer.Tokenizer` |
| `Llama` | High-level entry point — `build()`, single-prompt `generate()` / `chat()` |

**Loading a checkpoint:**

```java
import smile.llm.model.llama.Llama;

// Loads params.json + *.pt checkpoint(s) from the directory
Llama llama = Llama.build(
        "model/Meta-Llama-3-8B-Instruct",  // checkpoint dir
        "model/Meta-Llama-3-8B-Instruct/tokenizer.model",
        /*maxBatchSize=*/ 4,
        /*maxSeqLen=*/    2048,
        /*deviceId=*/     (byte) 0           // CUDA:0; use -1 for CPU
);
```

`Llama.build` supports two on-disk layouts: **Meta** (`params.json` plus
`consolidated.*.pt` shards) and **HuggingFace** (`config.json` plus
`*.safetensors`, optionally indexed by `model.safetensors.index.json`). An
overload accepts `memFractionStatic` (SGLang-style static-region fraction of
total GPU memory for weights + KV) and a KV-cache dtype override.

**Text generation (raw token IDs):**

```java
int[] prompt = llama.tokenizer.encode("Once upon a time", true, false);
ChatCompletion result = llama.generate(
        prompt,
        /*maxGenLen=*/   200,
        /*temperature=*/ 0.6,
        /*topp=*/        0.9,
        /*logprobs=*/    false,
        /*seed=*/        42L,
        /*listener=*/    null
);
System.out.println(result.content());
```

**Chat completion (dialog format):**

```java
import smile.llm.Message;

ChatCompletion reply = llama.chat(
        new Message[]{
            Message.system("Be concise."),
            Message.user("Explain RoPE in one sentence.")
        },
        /*maxGenLen=*/   128,
        /*temperature=*/ 0.7,
        /*topp=*/        0.9,
        /*logprobs=*/    false,
        /*seed=*/        0L,
        /*listener=*/    null
);
System.out.println(reply.content());
```

**Streaming** via `GenerationListener` (serve uses
`GenerationListeners.toPublisher`):

```java
import smile.llm.GenerationListener;

GenerationListener listener = new GenerationListener() {
    @Override public void onText(String chunk) { System.out.print(chunk); }
};
int[] prompt = llama.tokenizer.encode("Tell me a joke", true, false);
llama.generate(prompt, 200, 0.8, 0.95, false, 0L, listener);
```

---

## Qwen3.5 (`smile.llm.model.qwen`)

`Qwen` implements the Qwen3.5 hybrid text stack: a mix of efficient linear
attention (`GatedDeltaNet`) for most layers and full gated attention for the
rest, with optional sparse MoE, a unified thinking / non-thinking mode, and a
native vision tower for multimodal input.

| Class | Role |
|---|---|
| `QwenModelArgs` | Hyperparameters from `config.json` |
| `QwenModel` | Top-level decoder module |
| `QwenBlock` | Decoder block (Gated DeltaNet or gated attention + MLP) |
| `GatedDeltaNet` / `GatedDeltaRule` | Linear-attention core |
| `GatedAttention` | Full gated attention layer |
| `QwenMtp` | Multi-token-prediction head (speculative decoding) |
| `QwenVisionTower` / `QwenVisionArgs` | Vision encoder for multimodal input |
| `QwenVlProcessor` | Image / video preprocessing → `ProcessedMultimodal` |
| `Qwen` | High-level entry point — `build()`, `generate()`, `chat()` |

**Loading a checkpoint:**

```java
import smile.llm.model.qwen.Qwen;

Qwen qwen = Qwen.build(
        "model/Qwen3.5-8B",   // HF checkpoint dir (config.json + weights)
        /*maxBatchSize=*/ 4,
        /*maxSeqLen=*/    4096,   // <= 0 uses the config value
        /*deviceId=*/     (byte) 0
);
```

Overloads add `memFractionStatic`, a KV-cache dtype override, KV page size,
`ParallelConfig` (tensor parallelism), and safetensors loader thread count.

**Generation and chat** use the same `LanguageModel` surface as LLaMA, plus a
text-prompt convenience:

```java
ChatCompletion out = qwen.complete("Write a haiku about the sea.", 64, 0.7, 0.9, false, 0L, null);
ChatCompletion reply = qwen.chat(dialog, 256, 0.7, 0.9, false, 0L, null);
```

**Speculative decoding** is enabled by default when MTP weights are present:

```java
qwen.setSpeculativeEnabled(true);   // native MTP draft/verify
```

**Prefix replay** for hybrid models must be opted into explicitly (off by
default for hybrid safety):

```java
qwen.setPrefixReplayEnabled(true);  // radix prefix reuse + DeltaNet state restore
```

---

## MiniMax-M3 (`smile.llm.model.minimax`)

MiniMax-M3 is a 428B-parameter (23B active) multimodal MoE language model. The text tower has GQA
attention (64 query / 4 KV heads, Gemma-style RMSNorm, per-head QK norm, partial RoPE), 3 dense and
57 sparse-MoE layers (128 routed experts, top-4, sigmoid routing with a selection bias, one shared
expert, `swigluoai` activation), and **MiniMax Sparse Attention (MSA)** on the MoE layers: an index branch
scores 128-token KV blocks (max of the raw `index_q . index_k` dot product, one index-key head shared by
the 4 index-query heads) and attention runs only over each query's top-16 blocks plus its local block.

```java
MiniMax model = MiniMax.build("/models/MiniMax-M3-MXFP8", /*maxBatch*/ 8, /*maxSeqLen*/ 32768,
        new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, /*memFractionStatic*/ 0.85, /*pageSize*/ 16);
ChatCompletion reply = model.chat(dialog, 512, 0.6, 0.95, false, 0, null);
```

| Class | Role |
|---|---|
| `MiniMax` | `LanguageModel` + `ModelExecutor` facade: `build`, bind / prefill / decode / finish, `encodeChat` |
| `MiniMaxModelArgs` | Hyperparameters from `config.json` (`text_config`), weight mode |
| `MiniMaxModel`, `MiniMaxBlock` | Decoder stack (one replica per expert-parallel rank) |
| `MiniMaxAttention`, `MiniMaxSparse`, `MiniMaxIndexCache` | GQA, MSA block selection, index-key cache addressed by the KV pool's slots (so radix prefix reuse covers it) |
| `MiniMaxMoE`, `MiniMaxMlp` | Router + experts (stacked weights), dense/shared FFN |
| `MiniMaxShard` | Expert-parallel slice: rank, size, device |
| `MiniMaxTokenizer`, `MiniMaxWeightNames` | Byte-level BPE (all added tokens atomic, as in the reference); HF tensor name mapping |

**Checkpoints.** BF16 (`MiniMaxAI/MiniMax-M3`) and MXFP8 (`MiniMaxAI/MiniMax-M3-MXFP8`, FP8 E4M3 weights with an
E8M0 scale per 32 elements). Vision tower, projector and MTP tensors are skipped (the published checkpoint has no
MTP weights); image / video / audio input is rejected.

**Expert parallelism.** Each rank holds a contiguous slice of every layer's routed experts (allocated directly on its
device); attention, dense FFNs, the router and the shared expert are replicated. Every rank routes all tokens, computes
the tokens assigned to its own experts, and the partial outputs are summed with an all-reduce. In `smile-serve` the
device list comes from `smile.chat.devices` (the number of ranks is its length). The full MXFP8 model is ~440 GB, so it
does not fit one 8 x 40 GB node; the GPU test checkpoints are truncated to the first 4 / 16 layers.

**MXFP8 weight modes** (`MiniMaxWeightMode`; `smile.chat.quantization` or `SMILE_MINIMAX_MXFP8` =
`auto|marlin|fp8|fp8emu|dequant`):

| Mode | Weights on device | GEMM | Hardware |
|---|---|---|---|
| `MARLIN` | FP8 + E8M0 (Marlin layout) | weight-only Marlin kernel, bf16 activations (vLLM generalized Marlin, vendored in `third_party/vllm_marlin`) | any CUDA GPU with bf16 (A100 and up) |
| `FP8` | FP8 + 128x128 float scales (lossless re-expression of the E8M0 scales) | native block-scaled FP8 GEMM, activations quantized to FP8 (W8A8) | Hopper (sm_90) and newer |
| `FP8_EMULATED` | same as `FP8` | emulated with dequantization | any CUDA GPU (tests) |
| `DENSE` | bf16 (dequantized while loading) | bf16 matmul | reference, 2x the memory |

`auto` picks `FP8` on Hopper+ only after `Fp8BlockGemm.selfCheck` (native vs emulated GEMM on a random problem) passes,
otherwise `MARLIN`. **The native FP8 path has not been run on Hopper hardware yet**; run
`MxFp8BlockFp8Test` there first. W8A8 is slightly noisier than W8A16 (activations are rounded to FP8).

**Chat.** `MiniMaxChatTemplate` is a byte-exact port of `chat_template.jinja` (verified against transformers on golden
prompts). The request field `thinking` (`enabled|disabled|adaptive`, or `{"type": ...}`; also
`chat_template_kwargs.thinking_mode`) selects the thinking mode; unset means the model decides. Reasoning
(`<mm:think>`) is returned as `<think>...</think>` like the other models. Tool calls use the
`]<]minimax[>[<tool_call>` XML format, parsed by `MiniMaxToolCallParser` (typed from the request's tool schemas).

**Decode performance.** On the `MARLIN` path a decode step is a handful of fused kernels (`smile_minimax_ops.cu`: Gemma
RMSNorm, QK norm + RoPE, swigluoai, MoE routing, MSA block scoring/top-k) around the Marlin GEMMs; q/k/v and the two
index projections run as one GEMM, and decode attention treats the query heads of a KV group as query rows (no K/V
expansion). Slot indices, positions and masks are built once per step (`MiniMaxStep`). The whole step is captured in a
CUDA graph per (batch, window): batches up to `SMILE_MINIMAX_CUDA_GRAPH_MAX_BATCH` (32), padded to the next power of
two with copies of row 0 (so a padded row rewrites exactly what row 0 writes), the
attention window rounded up to a multiple of 256 (at most ~12.5% above the context), at most
`SMILE_MINIMAX_CUDA_GRAPH_MAX_ENTRIES` (48) graphs; larger batches, and any capture failure, run eagerly (set
`SMILE_MINIMAX_GRAPH_DEBUG=1` to print the failure's stack trace; the warning is only logged).
`SMILE_MINIMAX_CUDA_GRAPH=0` disables graphs, `SMILE_FUSED_OPS=0` the fused kernels, `SMILE_MINIMAX_FUSE_QKV=0` the
merged projection. Measured on A100 (context 512): 4 layers on 1 GPU 13.7 -> 3.9 ms/step (batch 8); 16 layers on 8 GPUs
24 -> 11 ms/step (batch 8), 24 -> 15 ms (batch 32).

**Limits.** MSA block selection during prefill is a reference tensor implementation that materializes an `[batch,
groups, query, context]` score matrix, so long prompts need a lot of memory and prefill is slow; decode attention still
runs over the whole (masked) window; non-Marlin weight modes route on the host (no CUDA graphs); no speculative
decoding, log-probabilities, tensor-parallel attention or multi-node execution.
Opt-in tests that need a checkpoint or GPUs read `MINIMAX_*` environment variables (see the test javadoc).

---

## Inference Engine (`smile.llm.engine`)

`InferenceEngine` is the continuous-batching runtime used by serve. It admits
work up to `maxInFlight` while KV pages are free (**Fluid Injection**), prefills
(optionally chunked under a token budget), then runs a batched `decodeStep`
over all decoding requests. `GenerationHandle.abort()` performs **Instant
Eviction** of queued and in-flight KV.

| Type | Role |
|---|---|
| `InferenceEngine` | Scheduler + worker thread; `submit(...)` returns a `GenerationHandle` |
| `ModelExecutor` | Low-level surface: `bind` / `prefill` / `decodeStep` (implemented by `Llama`, `Qwen`) |
| `GenerationRequest` | One job: prompt tokens or dialog, sampling params, optional multimodal input |
| `GenerationHandle` | In-flight handle: `future()`, `abort()`, `isAborted()` |
| `SpeculativeDecoding` | MTP draft / verify helpers |
| `DecodeCudaGraph` / `VerifyCudaGraph` | CUDA-graph capture for decode / verify |
| `Sampling` | Temperature / top-p sampling |

```java
import smile.llm.engine.InferenceEngine;
import smile.llm.engine.GenerationRequest;
import smile.llm.engine.GenerationHandle;

try (var engine = new InferenceEngine(llama, /*maxInFlight=*/ 8)) {
    GenerationHandle handle = engine.submit(
            GenerationRequest.ofTokens(prompt, 256, 0.6, 0.9, false, 0L, listener));
    ChatCompletion reply = handle.future().join();
}
```

`GenerationRequest` can also carry a dialog (encoded by the engine), a
`QwenVlProcessor.ProcessedMultimodal` for vision input, `ChatOptions` for tool
calling, and speculative-decoding flags.

Runtime introspection: `queueSize()`, `inFlight()`, `kvFreeSlots()`,
`kvFreePages()`, `activeDecodeCount()`, and cumulative timing counters
(`queueWaitMsTotal()`, `prefillMsTotal()`, `decodeMsTotal()`).

> **Serve / multi-request:** one prompt per `GenerationRequest`, with
> `smile.chat.max-batch-size` as the in-flight cap. Streaming disconnect calls
> `GenerationHandle.abort()`, which cooperatively stops decode between steps
> and frees KV.

---

## KV Cache & Radix Prefix Reuse (`smile.llm.cache`)

`KvCachePool` is a paged KV-cache allocator. Pages are the unit of allocation
(`DEFAULT_PAGE_SIZE = 16` tokens), and `RadixCache` organizes cached prefixes in
a compressed radix tree so shared system prompts and conversation histories are
computed once and reused across requests.

| Type | Role |
|---|---|
| `KvCachePool` | Paged KV storage; `bind` / `put` / `get` / `finishRequest` |
| `RadixCache` | Longest-prefix match / insert over token sequences |
| `KvCacheLayout` | Layer / head / dim layout descriptor |
| `FlashInferKvMetadata` | CSR metadata for the FlashInfer attention path |
| `KvCacheExhaustedException` | Thrown when no free pages remain |

```java
// Static-region budget (SGLang --mem-fraction-static semantics)
KvCachePool.StaticKvBudget budget = KvCachePool.computeStaticKvBudget(...);
KvCachePool pool = KvCachePool.allocate(layout, device, ScalarType.BFloat16, budget);

pool.setPrefixReuseEnabled(true);   // enable radix match/insert across requests
```

Prefix reuse is enabled per model via `Llama.setPrefixReuseEnabled(...)` /
`Qwen.setPrefixReplayEnabled(...)`.

---

## Attention Backends (`smile.llm.attention`)

`AttentionBackends.install(...)` selects the process-wide attention kernel at
chat-service startup:

| Backend | Notes |
|---|---|
| `TORCH_NATIVE` | Default; pure LibTorch attention |
| `FLASHINFER` | Requires a `libsmile_torch` built with `USE_FLASHINFER`; falls back to `TORCH_NATIVE` with a warning when unavailable |

```java
AttentionBackends.install(AttentionBackend.FLASHINFER);
```

---

## Quantization (`smile.llm.quant`)

Weight-quantization backends for memory- and bandwidth-bound inference. The
checkpoint format is detected automatically and resolved to a GEMM backend:

| `QuantFormat` | Meaning |
|---|---|
| `DENSE` | Dense BF16 / FP16 / FP32 weights |
| `FP8` | Native FP8 (e4m3 / e5m2) weights + scales |
| `NVFP4` | Native NVFP4 weights (Blackwell) |
| `GPTQ` | HuggingFace GPTQ INT4 |
| `AWQ` | HuggingFace AWQ INT4 |

```java
QuantPolicy.Resolved resolved = QuantPolicy.resolve(checkpointDir, device, backendOverride);
// resolved.format()  → QuantFormat
// resolved.backend() → WeightGemmBackend (FP8 / NVFP4 primary, Marlin Ampere failover)
```

`QuantLinearFactory` builds the appropriate linear layer (`Fp8Linear`,
`Fp8BlockLinear`, `Nvfp4Linear`, `MarlinLinear`) for the resolved format.

---

## Tensor Parallelism (`smile.llm.parallel`)

In-process tensor parallelism for multi-GPU inference. Pipeline axes are
reserved for future multi-node PP.

| Type | Role |
|---|---|
| `ParallelConfig` | `(tpSize, ppSize, dpSize, devices)`; `single(...)`, `tensorParallel(...)` |
| `TensorParallelGroup` | Rank group + collective ops |
| `ColumnParallelLinear` / `RowParallelLinear` | Sharded linear layers |
| `WeightSharding` / `TensorShardSpec` | Weight partitioning |

```java
import smile.llm.parallel.ParallelConfig;

ParallelConfig tp = ParallelConfig.tensorParallel((byte) 0, (byte) 1);
Qwen qwen = Qwen.build(dir, /*maxBatchSize=*/ 8, /*maxSeqLen=*/ 8192,
        (byte) 0, /*memFractionStatic=*/ 0.85, "bfloat16", tp);
```

---

## Tool Calling & Chat Templates

`ChatOptions` carries the tools and selection policy for a request; the model
injects them into the chat template and parses tool calls out of the output.

```java
import smile.llm.ChatOptions;
import smile.llm.ToolDefinition;
import smile.llm.FunctionDefinition;

ChatOptions options = new ChatOptions(
        new ToolDefinition[]{ new ToolDefinition(new FunctionDefinition(
                "get_weather", "Get the current weather", parametersSchema)) },
        ToolChoice.AUTO,
        /*parallelToolCalls=*/ true);

int[] prompt = model.encodeChat(dialog, options);
```

| Type | Role |
|---|---|
| `ChatTemplate` | Interface for model-specific prompt formatting |
| `Qwen3ChatTemplate` | Qwen3 chat template (thinking / non-thinking, tools) |
| `MiniMaxChatTemplate` | MiniMax-M3 chat template (thinking modes, tools, tool results) |
| `ToolCallParser` | Interface for parsing tool calls from generated text |
| `JsonToolCallParser` / `Qwen3XmlToolCallParser` / `MiniMaxToolCallParser` | Concrete parsers |
| `AssistantTextSanitizer` | Strips tool-call markup from assistant text |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL v3 licensed.*
