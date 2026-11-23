# SMILE — Large Language Models (`smile.llm`)

The `smile.llm` package provides the building blocks and complete inference
stacks for decoder-only LLMs on top of the same FFM-backed LibTorch bridge used
by `smile.deep`. It ships DeepSeek-V4.1, GLM-5.3-Flash, MiniMax-M3,
Inkling-2-Mini, Qwen3.8 (Gated DeltaNet + gated full attention) and LLaMA-3
implementations, a tiktoken BPE tokenizer, a continuous-batching inference engine, KV-cache / radix prefix
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
7. [DeepSeek-V4.1 (`smile.llm.model.deepseek`)](#deepseek-v41-smilellmmodeldeepseek)
8. [GLM-5.3-Flash (`smile.llm.model.glm`)](#glm-53-flash-smilellmmodelglm)
9. [MiniMax-M3 (`smile.llm.model.minimax`)](#minimax-m3-smilellmmodelminimax)
10. [Inkling-2-Mini (`smile.llm.model.inkling`)](#inkling-2-mini-smilellmmodelinkling)
11. [Qwen3.8 (`smile.llm.model.qwen`)](#qwen38-smilellmmodelqwen)
12. [LLaMA (`smile.llm.model.llama`)](#llama-smilellmmodelllama)
13. [Inference Engine (`smile.llm.engine`)](#inference-engine-smilellmengine)
14. [KV Cache & Radix Prefix Reuse (`smile.llm.cache`)](#kv-cache--radix-prefix-reuse-smilellmcache)
15. [Attention Backends (`smile.llm.attention`)](#attention-backends-smilellmattention)
16. [Quantization (`smile.llm.quant`)](#quantization-smilellmquant)
17. [Tensor Parallelism (`smile.llm.parallel`)](#tensor-parallelism-smilellmparallel)
18. [Tool Calling & Chat Templates](#tool-calling--chat-templates)

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
├── model/deepseek/ DeepSeek-V4.1: MoE, Engram memory, Marlin FP8 experts
├── model/glm/     GLM-5.3-Flash: KDA + sparse MLA, hyper-connections, MoE, MTP
├── model/minimax/ MiniMax-M3 / M3.1-Flash: MoE, MSA, DSpark, vision tower
├── model/inkling/ Inkling-2-Mini: looped MoE, relative-bias attention, DSpark, vision
├── model/qwen/    Qwen3.8 hybrid text stack + vision tower
├── model/llama/   LLaMA-3: Llama, LlamaModel, LlamaBlock,
│                  GroupedQueryAttention, LlamaModelArgs, Tokenizer
├── engine/        Continuous-batching runtime: InferenceEngine, ModelExecutor,
│                  GenerationRequest, GenerationHandle, speculative decoding
├── cache/         KvCachePool, RadixCache, KV layout / metadata
├── attention/     Attention backend selection (torch-native / FlashInfer)
├── quant/         FP8 / NVFP4 / MXFP4 / Marlin weight quantization
├── parallel/      Tensor-parallel primitives
├── checkpoint/    SafeTensors loader tuning
├── template/      ChatTemplate interface + Qwen3 / MiniMax / DeepSeek templates
└── tool/          Tool-call parsers (JSON, Qwen3 XML, MiniMax)
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

Qwen3.8 uses a partial / interleaved multi-axis RoPE (`InterleavedMRope`,
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

## DeepSeek-V4.1 (`smile.llm.model.deepseek`)

`DeepSeek` serves the DeepSeek-V4.1 mixture-of-experts stack: gated decoder
blocks with MLA-style attention, a router over stacked experts, and an **Engram**
memory layer that keeps recent token rows on disk instead of in the KV budget.
Dense projection layers stay packed through weight-only Marlin FP8 kernels, and
expert weights may be stored as **MXFP4**.

| Class | Role |
|---|---|
| `DeepSeek` | High-level `LanguageModel` façade: `build`, bind / prefill / decode / finish, `encodeChat` |
| `DeepSeekModelArgs` | Hyperparameters from `config.json`, weight mode |
| `DeepSeekModel`, `DeepSeekBlock` | Decoder stack |
| `DeepSeekAttention` | MLA-style attention with fused `wq_a + wkv` projection |
| `DeepSeekMoE`, `DeepSeekMarlinExperts` | Router and stacked-experts FFN on Marlin FP8 |
| `DeepSeekEngram`, `DeepSeekEngramTokenMap` | File-backed Engram token rows |
| `DeepSeekLinear` | Dense/shared projections, fused `w1 + w3` shared FFN |
| `DeepSeekMxfp4` | MXFP4 expert weight dequantization |
| `DeepSeekTokenizer`, `DeepSeekChatTemplate` | Byte-level BPE and the V4.1 chat template |

Decode runs as one CUDA graph per rank for each (batch bucket, position capacity): token ids, positions, the
per-sequence cache pointers and the engram rows are static device tensors refreshed before every replay, and the
window slots, group completion and index lengths are derived on the device, so the step has no host work beyond the
engram table reads. The fused kernels (`smile_deepseek_ops.cu`) replace the chains of small ATen ops; the
per-sequence window ring, compressor, indexer and sparse attention reach every sequence's caches through pointer
tables. Environment switches: `SMILE_DS_GRAPH=0` (eager decode), `SMILE_FUSED_OPS=0` (no fused kernels),
`SMILE_DS_OFF=op,op` (turn single fused ops off, e.g. `batch_attn`, `rope`, `sparse_attn`), `SMILE_FUSED_DEBUG=1`
(say why a fused op declined), `SMILE_DS_STAMP=1` (GPU time per section of a graph replay, via `DeepSeek.stampReport`),
`-Dsmile.aten.stats=true` (per-operator host time), `SMILE_DS_ENGRAM_PREFETCH=0` (do not read the engram tables into the
page cache after loading; first touches of their random rows are network round trips).

Prefill runs eagerly, one layer per scope (a layer's temporaries are freed before the next layer, so the memory of a
long prompt does not grow with the layer count). For prompts of 32 or more rows the sparse attention is two bf16
tensor-core GEMMs around the fused `smile_ds_attn_softmax` kernel (keys gathered once per row and shared by all heads,
float32 scores, bf16 probabilities) and the indexer scores come from one GEMM per chunk of rows; the per-head kernels
re-read every key once per head and cost 14 ms (attention) and 24 ms (indexer) per layer at 1024 tokens. `SMILE_DS_ATTN_GEMM=0`
restores the per-head kernels. From 64 sequences the decode graph also uses the gather + GEMM attention over the per-sequence caches (`smile_ds_gather_keys_ptrs`; `-Dsmile.ds.attn.gemm.seqs`): 26.7 -> 8.0 ms of the 128-sequence replay (85.8 -> 73.0 ms). Decode graphs cover batches up to 128 (`SMILE_DS_MAX_GRAPH_BATCH`). Several waiting prompts are prefilled in one pass (`ModelExecutor.prefillBatch`, used by
`InferenceEngine` for executors that report `supportsBatchedPrefill()`): the sequence-independent math (projections, norms,
rope, MoE, hyper-connections, output projection) runs once over the concatenated prompts, the window ring, compressor,
indexer and attention per sequence. 8-GPU A100, one prompt: 16 tokens 98 ms, 1024 tokens 394 ms, 4096 tokens 1450 ms
(about 2600-2800 prompt tokens/s); 24 short prompts 3.8 s one by one, 1.0 s batched. The same prompt prefilled twice
differs by relative L2 0.07-0.2 in the logits (non-deterministic Marlin/NCCL reductions), so compare paths against that
floor, not for bit equality.

**Speculative decoding (DSpark).** The checkpoint ships a draft head under `mtp.*` (three window-attention stages with
128-expert MoEs, a Markov head that conditions each drafted token on the previous one, and a confidence head). The draft
reads the main model's hidden states at the inputs of layers 37-39 (`DeepSeekDraft`, taps captured in prefill, decode and
verify) and drafts a block of 5 tokens greedily. `DeepSeek.speculateStep` verifies the blocks of all speculating sequences
in one CUDA-graph pass: rows are laid out one position of the block after the other, the window ring has 8 spare slots
(slot = position % 136, attended span 128) so rejected rows never overwrite keys later queries need, the compressors save
their partial group after every row and are restored to the last accepted row, and the argmax of every row leaves the
graph. Greedy requests accept the longest matching prefix; sampled requests use speculative sampling against the
deterministic draft (`DeepSeekSampler`: accept with the target probability of the drafted token, else draw from the
target without it; nucleus from the 2048 most probable tokens). A sequence that took a plain step falls out of step with its
draft and stays plain. The verify rows of each sequence follow each other and each row is independent: the compressor pools a
row from the saved partial group (positions before the step) and the other rows' projections (positions inside it), and the
saved group is written once after acceptance. The draft pass is a CUDA graph too (one launch per round). The number of drafts
verified per sequence is chosen from the confidence head, whose output is calibrated (the hit rate of a position given its
predecessors were accepted follows `sigmoid(confidence)`): for each graph row bucket the most probable positions fill it and
the bucket with the most expected tokens per millisecond wins (`SMILE_DS_SPEC_DYNAMIC=0` verifies the full block of 5;
`-Dsmile.ds.spec.fixed.ms` / `-Dsmile.ds.spec.row.ms` set the cost model). On 8 A100 (warm, 96 tokens, T=0.6, through the
engine) speculation gives 86 vs 41 tok/s for one request, 207 vs 137 for 4, 361 vs 242 for 8, 542 vs 392 for 16 and 714 vs
588 for 32; the serve default lets up to 32 requests speculate (`smile.chat.speculative-max-concurrency`; the rest decode
plain). What made it pay: the verify and draft run batched over all sequences, the engram tables are mapped into the process
after the page-cache prefetch (eight rank threads faulting random rows of one file serialize: 6 ms per lookup instead of
0.2 ms; the rows of a lookup are also read once for all ranks), a request near its end stays in the batch
(`ModelExecutor.speculationMayOvershoot`; the engine drops the extra tokens), and rows are packed per sequence. Tokens per
step: prose 1.9-2.8, code 3.6-4.5, JSON 5.7-6.0.
`SMILE_DS_DRAFT=0` does not load the draft head.

Each request runs in its own session over a shared engine executor, and several
sequences decode in one batch. `DeepSeekMarlinExperts` keeps the dense FP8
layers packed, so a checkpoint never expands into dense projection shells.

---

## GLM-5.3-Flash (`smile.llm.model.glm`)

`Glm` serves GLM-5.3-Flash, a 321B FP8 checkpoint that mixes **Kimi-Delta-Attention (KDA)** layers (a gated delta-rule linear attention with a recurrent state)
with **sparse multi-head latent attention (MLA)** layers whose keys are chosen by a top-k indexer. Every decoder layer sits between **manifold-constrained
hyper-connections** (the residual is several parallel streams mixed by a doubly-stochastic matrix), the MoE has 288 routed experts plus a shared expert, and the
checkpoint carries a vision tower and an MTP layer. It runs on 8 A100 80 GB in one JVM with one thread per GPU: attention and dense weights are tensor-parallel,
the routed experts are split over the GPUs (TP8 + EP8) and multiplied as weight-only FP8 by the Marlin MoE kernel, which needs no FP8 tensor cores.

| Class | Role |
|---|---|
| `Glm` | `LanguageModel` + `ModelExecutor`: one replica thread per device, bind / prefill chunks / decode / speculative rounds, prefix snapshots |
| `GlmModelArgs`, `GlmTokenizer` | Hyperparameters from `config.json`; the tokenizer and chat template |
| `GlmModel`, `GlmBlock` | Text model and decoder layer (hyper-connection `pre` / `post` / `comb` around the token mixer and the feed-forward); the reference path in float32 |
| `GlmKda` | KDA layer: `S = S * exp(g); S += k (beta (v - S^T k))^T; o = S^T q`, a two-kernel prep and scan for prefill |
| `GlmMla` | Latent attention over the indexer's top-k tokens, with the key up-projection absorbed exactly; tensor-core single-pass prefill kernel |
| `GlmMlp`, `GlmMarlinExperts` | Dense SwiGLU or sigmoid-routed MoE with a shared expert; the experts a rank owns as FP8 + group-128 scales in the Marlin layout |
| `GlmDecodeGraph` | One CUDA graph per batch size for a decode step (inputs at fixed device addresses) |
| `GlmWindow`, `GlmWindowGraph`, `GlmMtpGraph` | Speculative verify window and MTP draft/absorb as CUDA graphs; `commit` keeps the accepted rows (KDA states, MLA rings) without rerunning the model |
| `GlmPrefixCache` | Whole-request snapshots (the KDA state cannot be rewound), resumed from the longest stored prefix; image prompts take part |
| `GlmVisionTower`, `GlmVisionProcessor`, `GlmMultimodal` | Vision tower (fp32) and the prompt with its patch rows; each GPU keeps an LRU of encoded images |

**Decode** (HTTP, 8x A100 80 GB, 32 slots, temperature 0.6, 256-token essays): plain decode 66 / 325 / 519 / 787 tok/s for 1 / 8 / 16 / 32 concurrent requests;
MTP speculation (3 drafts, off by default: `-Dsmile.glm.speculative=true` loads the layer and `smile.chat.speculative=true` turns it on) 95 / 475 / 681 / 906 tok/s,
x1.15 to x1.46. A plain step takes 13.6 ms for one sequence and 25.6 ms for 32. The output distribution is unchanged: the target verifies the drafts in one
CUDA-graph window and keeps the longest matching prefix.

**Prefill and long context.** A 2048-token chunk at 32k context takes 321 ms (about 6.4k prompt tok/s); a 115k-token prompt 10.8 s through HTTP; needle-in-a-haystack
12/12 up to 115k tokens; a 524k-token prompt completes. Fidelity against an fp32 Hugging Face run is 98-99.8 % on short prompts and 99.5-99.7 % on long ones.
`serve/README.md` section 2.2 has the tables and switches (`SMILE_GLM_GRAPH`, `SMILE_GLM_MTP`, `GLM_ATTEND_TC`, `GLM_X3_GEMM`, `-Dsmile.glm.prefix.cache.mb`).

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
E8M0 scale per 32 elements). The text loader skips the vision tensors (loaded separately, see below) and the published
checkpoint has no MTP weights; audio input is rejected (images and video are supported, see below).

**MiniMax-M3.1-Flash** (`MiniMaxAI/MiniMax-M3.1-Flash`, ~250 GB) keeps MXFP8 for attention and the dense/shared FFNs but stores the
routed experts as NVFP4 (`moe_quant_algo: NVFP4`, compressed-tensors `weight_packed` / `weight_scale` (E4M3 per 16) /
`weight_global_scale`). The experts stay FP4-resident and run through the vendored vLLM Marlin MoE kernel (`kFE2M1f` with
E4M3 group-16 scales and a per-expert float32 global scale, `smile_marlin_moe_nvfp4_gemm`; scales repacked like vLLM's
`prepare_nvfp4_moe_layer_for_marlin`). `SMILE_MINIMAX_MXFP8=dequant` expands them to bfloat16 instead (reference path). Checked on a
4-layer slice against that dense path: mean |logprob delta| 0.003 on 71/3000/6600-token prompts. Flash enables sparse
attention in every layer (also the three dense-FFN ones). Not run on Hopper/Blackwell natively.
Expert parallelism replicates everything that is not an expert (16 GB per GPU for Flash), which does not fit 8x40 GB next to
29 GB of experts, so NVFP4 checkpoints default to **tensor-parallel non-expert weights** (`SMILE_MINIMAX_TP=on|off|auto`,
single process only): query heads split across ranks (one KV head serves two ranks when there are 4 KV heads on 8 ranks, as in
vLLM), `o_proj` and the dense/shared FFNs are row-parallel (the shared expert's partial sum rides the routed experts'
all-reduce), embedding and `lm_head` are split by vocabulary. Per GPU: ~31.5-32 GiB of weights, ~8 GB left for KV, index cache
and activations (use `smile.chat.mem-fraction-static` around 0.88, a short prefill chunk, and `SMILE_MINIMAX_VISION=0` or expect
rank 0 to carry the 1.7 GB vision tower). Checked against vLLM (image `vllm/vllm-openai:minimax-m31-flash`, TP8, Marlin): 4 layers
mean |logprob delta| 0.004, growing smoothly with depth to 0.65-0.85 at 60 layers on random-word prompts, which equals the gap between two
of our own runs that differ only by a few ULPs (fused vs unfused QKV: 0.59-0.86), i.e. the model's noise floor; chat prompts answer correctly.

**DSpark speculative decoding (Flash).** The checkpoint ships a 2.3 GB `dspark/` draft (5 dense decoder layers, hidden 6144, sliding
window 4096, block of 7 tokens) that replaces the EAGLE-style MTP head. `MiniMax.build` loads it on rank 0's device next to the
target (`MiniMax.build(..., loadDraft)` / `smile.chat.speculative=false` or `SMILE_MINIMAX_DSPARK=0` skip it; it needs bf16 CUDA and costs ~3 GB on GPU 0, so with the vision tower use
`SMILE_MINIMAX_VISION=0` or `mem-fraction-static` around 0.92). How it works (`MiniMaxDspark`, `MiniMaxDsparkArgs`):
- The target taps the residual stream **leaving** layers 3, 17, 31, 45, 59 for every committed token (`MiniMaxModel.setAuxLayers`,
  `forwardAux`, `takeAux`; vLLM adds 1 to `dspark_target_layer_ids`, so "entering layer 4/18/32/46/60"). Prefill chunks and
  verify windows return them; plain CUDA-graph decode does not.
- Per token the draft computes `hidden_norm(fc(concat(taps)))` and stores the K/V of its 5 layers (`absorb`, a ring of the last
  ~4100 positions per request, ~42 MB at full window).
- A round (`draft`) runs the block `[anchor, mask x 6]` (mask id 200058) through the 5 layers in one parallel pass (non-causal
  inside the block, all in-window context visible), applies the target's own embedding and `lm_head` (through the tensor-parallel
  collectives, no 4.8 GB replica), then samples the 7 tokens left to right with a rank-256 Markov transition bias
  (`argmax(base_i + markov_w2[markov_w1[prev]])`); a confidence head is computed but not used yet.
- `MiniMax.speculateStep` verifies `[anchor, drafts]` in **one target forward** (captured as a CUDA graph per (batch, window rows,
  attention window) by `MiniMaxModel.windowGraph`, with the taps copied into a static buffer), samples every row with the request's
  own sampler and accepts the matching prefix plus the bonus token (exact for any temperature). Several requests are verified in
  one batched graph, and their drafts are produced in one batched pass too (`MiniMaxDspark.draftBatch`: projections, MLPs, output
  head and the 7 Markov steps run once over all rows, the Markov weights are read once per step; only the attention over each
  request's own context is per request).
- Requests without draft context (multimodal, or a gap) take one plain step. **Prefix reuse keeps speculation**: a finished
  request's draft ring is kept as a snapshot (LRU of `SMILE_MINIMAX_DSPARK_SNAPSHOTS`, default 8; the context of positions
  `[0, n)` depends only on the first `n` tokens) and a new request with a cached prefix adopts the longest matching one.
- The fused MSA decode kernels (`smile_msa_decode_select`, `smile_sparse_decode_attention`) now also handle **8 query heads per KV
  group** (64 heads over 8 tensor-parallel ranks; previously only 16), so TP8 decode and verify windows no longer build masks on
  the host. This alone took plain graph decode from 28.9 to 25.4 ms/token. `SMILE_MINIMAX_SPARSE_KERNEL=0` falls back to the
  masked-SDPA path for comparisons.

Measured on Flash (8x A100 40 GB, TP8, 8 chat prompts, greedy, 128 tokens, warm graphs): plain decode 25.4 ms/token (39 tok/s);
DSpark with 7 drafts 11.7 ms/token (**85 tok/s, 2.2x**; 3 / 4 / 5 / 6 drafts: 71 / 77 / 79 / 80 tok/s); ~1.8-1.95 accepted drafts per
round (vLLM's own implementation: 1.96 on the same prompts), a round = ~6 ms draft + ~32 ms verify for ~2.9 tokens. Aggregate
tok/s for N concurrent requests (median of 3 warm passes), plain -> speculative: 1: 39 -> 123 (a very predictable prompt set), 2: 64 -> 126,
4: 123 -> 160, 8: 210 -> 214, so the serve default speculates at most 4 requests at once (`smile.chat.speculative-max-concurrency`).
Output equals plain greedy
decoding only up to the model's own noise: the same prompt prefilled twice differs by ~10 in max logit, and the top-20 logits of a
1-row and an 8-row forward differ by 1-3, enough to flip an early greedy token (vLLM's DSpark shows the same, 7 of 8 prompts).
Image and video requests speculate too (the draft only needs the target's hidden states, which the multimodal prefill returns for
the image rows as well). Not supported yet: clusters, a CUDA graph for the draft, confidence-based truncation.

**Images and video.** The checkpoint's vision side (`vision_tower.*`, `multi_modal_projector.*`, `patch_merge_mlp.*`, ~1.7 GB,
bf16) loads onto rank 0's device after the text weights (`SMILE_MINIMAX_VISION=0` skips it; a checkpoint without vision
weights answers media requests with HTTP 400). `MiniMaxVisionTower` is a CLIP-style ViT (Conv3d patch embedding applied as
a linear map, pre-LayerNorm, 32 pre-norm blocks with a partial 3D RoPE over frame/row/column, no post-LayerNorm) followed
by the projector MLP and the 2x2 patch-merge MLP; attention is bidirectional inside each image or inside each group of at
most 4 temporal patches of a video. `MiniMaxVisionProcessor` reproduces the reference image/video processors
(`smart_resize` to multiples of 28 under 451,584 px per image or 602,112 px per video frame, bicubic antialiased resize,
normalize, pad the frame count to a multiple of 2, merge-block patch order); `MiniMax.processMultimodal` applies the chat
template and expands each `]<]image[>[` to `start, one placeholder per merged 2x2 patch group, end`, and each
`]<]video[>[` to, per two-frame group, a `]<]t seconds[>[` timestamp followed by the same. `prefillMultimodal` encodes the
media on rank 0, copies the embeddings to the other ranks and prefills in 2048-token windows with the placeholder rows
replaced; sequences with media are never added to the prefix cache (their ids are placeholders). Media come from `data:`,
http(s) or file URLs (images up to 64 MiB / 100 MP); videos are sampled with `ffmpeg`/`ffprobe` (1 fps by default, 4 to 768
frames, so those binaries must exist in the runtime image) or read from a directory of numbered frame images. Verified
on the real vision weights: the vision tower matches a reference built from vLLM's RoPE kernels to bf16 noise (relative
L2 0.017), patches match the HF processors to 5e-7, and a 4-layer model's expanded prompts equal vLLM's token ids exactly
with continuation log-probabilities within 0.011 (one image, two images, a video). A 100k-patch video encodes in ~3 s.
Not done: audio, log-probabilities, tools together with media in one request.

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

**Block-sparse attention (decode and prefill).** On MSA layers the select kernel returns the chosen block ids per query
token and `smile_sparse_decode_attention` attends over just those blocks (one CTA per token, KV group and block: the 16
query heads of the group over 128 keys, fp32 softmax partials, then a merge kernel), reading K/V straight from the pool
by slot. Nothing is quadratic in the window: no `[S, L]` score matrix, no mask, no full-window K/V gather, so long
prompts no longer run out of memory (the reference tensor-op path needed ~15 GB for a 16k-token prompt) and decode cost
follows the 16 selected blocks. The same kernels serve decode (one token per row) and prefill windows (token `t` of a
row sits at `positions[row] + t % seqlen`); the partial-softmax workspace is chunked to ~256 MB. Requires bf16 K/V, head
dim 128 and 16 query heads per KV head; otherwise the masked SDPA path runs. `SMILE_SPARSE_DECODE_ATTENTION=0` forces
that path. Measured (4 layers, 1 GPU, A100): decode at context 6000 / batch 8 6.4 -> 5.8 ms/step; contexts 16000 and
30000 (impossible before) decode at 6.5 and 9.2 ms/step at batch 1.

**Limits.** The three dense layers still use masked SDPA for prefill; the block scorer uses CUDA cores (no tensor
cores) and every query token re-reads its selected K/V blocks, so very long prefills are compute-bound; non-Marlin
weight modes route on the host (no CUDA graphs); no speculative decoding, log-probabilities, tensor-parallel attention
or multi-node execution.
Opt-in tests that need a checkpoint or GPUs read `MINIMAX_*` environment variables (see the test javadoc).

---

## Inkling-2-Mini (`smile.llm.model.inkling`)

`thinkingmachines/Inkling-2-Mini` (checkpoint type `inkling2_loop`): 24 layers applied 48 times (layers 6-17 are looped for three passes), hidden 2304,
24 query / 8 KV heads of 128, no RoPE: attention adds a learned **relative-distance bias** (per-token 16-dim states mix per-head profiles over the last
1024 positions) and a per-query **sink** logit. Layers 5, 11, 17, 23 are global (log-length query scaling, input-dependent short convolutions on K and V),
the others slide over 1024 tokens. Every sublayer output goes through a short causal convolution; the residual stream is read through **AttnRes** (softmax
over block states) and, inside the loop, through message gates from the previous pass. The MoE has 128 experts with top-8 routing and a softcapped SwiGLU;
experts are NVFP4 (layers 0-19) and MXFP8 (20-23) and run on the Marlin weight-only kernels, so the model fits one A100-40GB (about 22 GB, 31 GB with the
fused-path copies and graphs). Vocabulary 200058, EOS 200006, text and images.

| Class | Role |
|---|---|
| `Inkling` | `LanguageModel` + `ModelExecutor`: bind / prefill chunks / decode steps / speculative rounds, prefix snapshots, graphs, vision |
| `InklingModel`, `InklingLayer`, `InklingAttention`, `InklingExperts`, `InklingConv`, `InklingState` | reference forward in torch ops (any chunk size; the oracle for the fused path) |
| `InklingDecoder` + `smile_inkling_ops.cu` | fused decode / verify step: ~1240 kernel nodes, one CUDA graph per batch bucket; `InklingNative` binds the kernel dispatcher |
| `InklingFp8` | MXFP8 copies of the dense projections and the output head (Marlin), 10.8 -> 10.0 ms per step |
| `InklingDraft` | DSpark draft (3 layers + RNN Markov head) from `mtp.safetensors`; eager `draft()` and the graph-safe batched `round()` |
| `InklingVisionTower`, `InklingImageProcessor`, `InklingMultimodal` | ViT (40x40 px tiles, one language-model token per tile) and the embedding splice |
| `InklingChatTemplate`, `InklingResponseParser` | the checkpoint's jinja template (reasoning effort, tools) and the reply parser |

**Decode.** One A100-40GB, batch 1, greedy: plain 100 tok/s (10 ms per step); batch 2 / 4 / 8 aggregate 168 / 230-290 / 362 tok/s (HTTP: 78 / 144 / 233 tok/s for 1 / 2 / 4 concurrent essays). The fused path is verified against
the reference forward (next-token total variation median 0.001-0.002; layer-wise relative rms below the model's own bf16 noise).

**Speculative decoding (DSpark).** A block is the last committed token plus 7 mask rows; the draft reads K/V computed from four target taps (outputs of
applications 4, 22, 40 and the final-norm output) and an RNN head conditions each draft token on the previous one. Verification samples every verify row with
the request's own sampler and accepts the longest prefix that equals the drafts (lossless at any temperature). The verify step is the decode step with 8 rows
per request: the convolution kernels of row j read the previous rows' inputs, the extra state rows of each convolution hold the chunk's inputs, and a commit
kernel shifts the state by the accepted row count (K/V caches are position-addressed, so rejected rows are simply overwritten). One CUDA graph per
(requests, rows) verifies; a second graph commits, absorbs the accepted rows into the draft context and drafts the next block. Measured: verify 14.6 ms,
commit + draft 3.5 ms, round 18 ms; 2.5 of 7 drafts accepted on chat text (74 / 51 / 41 / 30 / 24 / 18 / 13 % by position), so one request runs 1.5x faster
(about 150 tok/s) on chat and code and 1.14x on open prose; at 2-4 concurrent requests the gain is x1.2 / x0.93 (prose), so up to 4 requests speculate (a cap below the batch size makes a mixed cohort slower than either mode: 141 vs 218 tok/s for 4 prose requests). `SMILE_INKLING_SPECULATE=0` turns it off. An MXFP8 draft head saved 0.7 ms but lowered the accept length (`-Dsmile.inkling.draft.fp8=base,fold`
to try).

**Long context.** Global attention in the reference/prefill path runs over key blocks of `smile.inkling.attn.tile` (2048) with an online softmax (the sink
is the initial state, the relative bias reaches 1024 back so only the last blocks gather it), so memory no longer grows with `T x S`. Global K/V caches can be
stored as E4M3 (`-Dsmile.inkling.kv.fp8=true`; keys are L2-clipped and values RMS-normalized, so no scale is needed; next-token total variation 1e-4 against bf16
on a 6000-token prompt). The decode attention kernel scores 32 keys per warp (lane per key) with 8 / 64 splits for local / global layers
(`smile.inkling.local.splits`, `smile.inkling.global.splits`; a short range falls back to a lane-per-dimension path). Prefix snapshots stop at 32768 tokens
(`smile.inkling.prefix.max`) because a snapshot holds the whole history. For more cache than one GPU holds, the caches of the last N global applications can live
on a second GPU (`-Dsmile.inkling.kv.spill.device=1 -Dsmile.inkling.kv.spill.apps=7`): the kernels read them over peer access.
Needle-in-a-haystack (`InklingLongPromptTest`): 8.7k tokens - found, prefill 1900 tok/s, decode 13.6 ms; 35k - 1650 tok/s, 14 ms; 131k - 1030 tok/s, 33 ms
(FP8 KV); 250k - 715 tok/s, 53 ms (FP8 KV, one GPU: the limit is about 300k tokens); 980k tokens (two 40 GB GPUs, FP8 KV, 7 of 8 global caches on the second GPU) - found, prefill 245 tok/s (67 min), decode 214 ms/token.

**Tests** (opt-in, GPU; see the class comments for the environment variables): `InklingDecoderTest` (fused vs reference), `InklingDraftTest` (accept length,
reference speculative loop), `InklingSpeculateTest` (verify/draft graphs vs plain), `InklingKernelTest`, `InklingFp8Test`, `InklingTiledAttentionTest`,
`InklingLongPromptTest`, `InklingVisionTest`, `InklingEngineTest`, `InklingPrefixReuseTest`, `InklingOracleTest` / `InklingGenerateTest` (against the Hugging Face model).

## Qwen3.8 (`smile.llm.model.qwen`)

`Qwen` implements the Qwen3.8 hybrid text stack (Qwen3.5 checkpoints share the architecture and load the same way): a mix of efficient linear
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
        "model/Qwen3.8-27B-FP8",   // HF checkpoint dir (config.json + weights)
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

### Qwen chat prompt

`Qwen3ChatTemplate` renders prompts exactly like the checkpoint's `chat_template.jinja` (goldens: 41 dialogs x the Qwen3.5-0.8B,
Qwen3.5-27B and Qwen3.8 templates rendered by transformers, `Qwen3ChatTemplateGoldenTest`; token ids of the rendered prompts
equal the HF tokenizer's, `QwenTokenizerRealTest`). `Tokenizer.of(dir)` reads the checkpoint's template and
`Qwen3ChatTemplate.Variant.detect` derives which behaviours apply, so each generation renders like its own reference:
Qwen3.8 adds the reasoning-effort system turn and keeps think blocks of earlier turns (`preserve_thinking`), Qwen3.5
splits `<think>` out of assistant content and prints an empty system turn, small Qwen3.5 models think only when asked.
The same template serves plain chat, tool calls and the vision prompt; all added tokens (`<|im_start|>`, `<think>`,
`<tool_call>`, the vision placeholders, ...) stay single tokens inside text (the tool path used to encode them as plain
characters).

Request fields (also accepted inside `chat_template_kwargs`): `thinking` / `thinking_mode` (`enabled|disabled|adaptive`) or
`enable_thinking` (boolean) switch thinking off (`<think>\n\n</think>\n\n` is prefilled, the model answers directly),
`reasoning_effort` (`xhigh` = default, `high` as an alias, `medium` = no instructions, `low`; others are HTTP 400) and
`preserve_thinking`. The generation prompt opens `<think>\n`, so replies carry only the closing `</think>`; the endpoint
returns the text after the last `</think>` (streaming chunks still carry the reasoning followed by `</think>`). Qwen3.8's
default system turn adds ~44 prompt tokens per request (a shared prefix). `reasoning_content` in assistant history messages
is not read (clients that echo it back are rendered with an empty think block).

### Qwen3.x block-FP8 on Ampere (Marlin)

Fine-grained FP8 checkpoints (`weight_block_size=[128,128]`, float32 `weight_scale_inv`, e.g. `Qwen/Qwen3.8-27B-FP8`) use
native `_scaled_mm_v2` on Hopper and the same weight-only Marlin kernel as MiniMax on Ampere/Ada
(`WeightGemmBackend.MARLIN_FP8`, chosen by `auto` for FP8 on sm_80-89; override name `marlin_fp8`).
`Fp8BlockMarlinLinear` repacks the FP8 weights once (`smile_marlin_fp8_repack_weight`) and the 128x128 scales into the
kernel's group-128 layout (`smile_marlin_fp8_repack_block_scales`: expanded over the 128 columns, bf16, Marlin permutation,
FP8 exponent bias 2^120 folded in), so weights stay 1 byte per element in memory. Because the model constructor would
otherwise allocate every projection as a bf16 shell (38.9 GB for the 27B model), the projections the installer replaces are
constructed as 1x1 placeholders (`ParameterInit.placeholderGemms()`, `LinearLayer.gemm`). The FP8 installer also covers the
MTP head (`mtp.layers.N.*`). Verified on an A100-40GB against vLLM's Marlin FP8 on the same checkpoint: teacher-forced
log-probabilities over 8 prompts x 48 steps differ by 0.0037 on average (top-1 agreement 380/384; every disagreement is a
tie in vLLM's own distribution); kernel tests cover every Qwen3.8-27B layer shape. Measured on one A100-40GB (batch 1):
~36 tok/s plain and ~78-80 tok/s with MTP speculation (11 prompts x 256 tokens); the first eager build was ~22 / ~49. See
"Qwen decode performance" below. Decode CUDA graphs need FlashInfer attention (the default backend; `libsmile_torch` must be
built with `USE_FLASHINFER`, which is the CMake default) and are on by default; `SMILE_DECODE_CUDA_GRAPH=0` disables them.

### Qwen decode performance (batch 1, FlashInfer + CUDA graphs)

A plain decode step is ~27 ms of GPU time: Marlin GEMMs 20.9 ms and the lm_head GEMM 1.7 ms (both at the memory-bandwidth
floor, ~1.3 TB/s) plus ~5 ms of everything else, in ~1500 graph nodes. The kernels that bring the rest down (all in
`libsmile_torch`; each has a CUDA test against an independent reference, and an env switch back to the old path):

| Kernel | Replaces | Where |
|---|---|---|
| `gated_delta_recurrent_fast_kernel` | one block per head with the whole state in shared memory: state in registers, grid over `V/32` x `B*H`, one cross-warp reduction per token (122 -> 10 us per layer call); `SMILE_GATED_DELTA_REFERENCE=1` selects the old kernel | `smile_gated_delta.cu` |
| `smile_gated_delta_decode_fused` | DeltaNet decode conv update + q/k/v split + gate math + recurrent rule (~25 launches -> 2); `SMILE_GATED_DELTA_UNFUSED=1` disables it | `GatedDeltaNet.forward`, `Native.gatedDeltaDecodeFused` |
| `smile_gated_delta_verify_window_fused` | the same for MTP verify windows, including the per-position conv and recurrent checkpoints | `GatedDeltaNet.fusedVerifyWindowGates` |
| `smile_gemv2` | the two tiny `in_proj_a` / `in_proj_b` cuBLAS GEMVs (decode only: in verify it changes greedy trajectories for no gain) | `GatedDeltaNet.projectAB` |
| `smile_add_gemma_rms_norm`, vectorized `smile_gemma_rms_norm`, `smile_silu_mul` | residual add + RMSNorm, SiLU * up | `QwenBlock`, `FeedForward` |
| `smile_qk_norm_rope` (strided heads), `smile_sigmoid_gate_mul`, `smile_kv_write` | q/k norm + RoPE reading q in place from the interleaved q|gate projection, `attn * sigmoid(gate)`, the K and V cache writes (decode and verify paths of the 16 attention layers) | `GatedAttention`, `KvCachePool.put` |
| persistent Marlin workspace | a `zeros` fill per GEMM (the kernel leaves its lock array zeroed) | `smile_marlin_fp8.cu` |

`SMILE_FUSED_OPS=0` turns off all the elementwise kernels at once. Two implementation rules that cost time: a graph
captured once reads the addresses it was captured with, so anything a replayed kernel reads (FlashInfer page tables) lives
in fixed-address buffers refreshed before each replay (`KvCachePool.decodeGraphMetadata`); and a `Tensor` cached from
inside a layer's forward must call `detachFromScopes()`, or the layer's `AutoScope` frees it after the first step.
Comparing configurations needs 10+ prompts: 1-ulp changes flip greedy tokens and the MTP accept rate moves with the
generated text. MTP rounds at 3 drafts: draft 8.2 ms (5.2 ms of it the 248k-vocabulary lm_head), verify 28.1 ms.

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
| `Qwen3ChatTemplate` | Qwen3.8 / Qwen3.5 chat template, byte-exact port of `chat_template.jinja` (thinking, reasoning effort, tools, tool results, history) |
| `MiniMaxChatTemplate` | MiniMax-M3 chat template (thinking modes, tools, tool results) |
| `DeepSeekChatTemplate` | DeepSeek-V4.1 chat template |
| `ToolCallParser` | Interface for parsing tool calls from generated text |
| `JsonToolCallParser` / `Qwen3XmlToolCallParser` / `MiniMaxToolCallParser` | Concrete parsers |
| `AssistantTextSanitizer` | Strips tool-call markup from assistant text |

---

*SMILE — Copyright © 2010–2026 Haifeng Li. GNU GPL v3 licensed.*
