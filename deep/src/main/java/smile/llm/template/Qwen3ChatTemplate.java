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
package smile.llm.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import smile.llm.AudioUrlPart;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.FunctionCall;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;
import smile.llm.VideoUrlPart;

/**
 * Java port of the Qwen3.5 / Qwen3.8 {@code chat_template.jinja}, byte for byte (the golden tests compare it with
 * transformers' rendering of the real templates).
 *
 * <p>The released templates differ in a few independent behaviours, which {@link Variant#detect} reads from the
 * checkpoint's own template text so every checkpoint renders like its reference:
 * <ul>
 *   <li>Qwen3.8 adds a reasoning-effort system turn ({@code reasoning_effort}: {@code xhigh} by default, {@code low},
 *       {@code medium} = none) whenever thinking is on, and keeps the {@code <think>} block of earlier assistant
 *       turns unless {@code preserve_thinking} is false;</li>
 *   <li>Qwen3.5 splits a {@code <think>...</think>} found in an assistant message's content, and prints an empty
 *       system turn for an empty system message;</li>
 *   <li>small Qwen3.5 models think only when {@code enable_thinking} is true, the larger ones unless it is false.</li>
 * </ul>
 * The generation prompt opens the thinking block ({@code <think>\n}) or closes an empty one
 * ({@code <think>\n\n</think>\n\n}); the model then continues inside the block, so replies carry no opening tag.
 *
 * @author Haifeng Li
 */
public final class Qwen3ChatTemplate implements ChatTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String XHIGH = "Reasoning effort is set to xhigh. Please think carefully through the task, "
            + "validate key assumptions, consider plausible alternatives, and prioritize correctness, consistency, "
            + "and clarity in the final answer.";
    private static final String LOW = "Reasoning effort is set to low. Keep your thinking brief and focused, moving "
            + "directly to the conclusion without unnecessary elaboration.";

    private static final String TOOL_INSTRUCTIONS = "\n\nIf you choose to call a function ONLY reply in the following "
            + "format with NO suffix:\n\n<tool_call>\n<function=example_function_name>\n<parameter=example_parameter_1>"
            + "\nvalue_1\n</parameter>\n<parameter=example_parameter_2>\nThis is the value for the second parameter\n"
            + "that can span\nmultiple lines\n</parameter>\n</function>\n</tool_call>\n\n<IMPORTANT>\nReminder:\n"
            + "- Function calls MUST follow the specified format: an inner <function=...></function> block must be "
            + "nested within <tool_call></tool_call> XML tags\n- Required parameters MUST be specified\n- You may "
            + "provide optional reasoning for your function call in natural language BEFORE the function call, but NOT "
            + "after\n- If there is no function call available, answer the question like normal with your current "
            + "knowledge and do not tell the user about function calls\n</IMPORTANT>";

    /**
     * The behaviours in which the released Qwen3.x templates differ.
     *
     * @param reasoningEffort       renders the reasoning-effort system instructions while thinking is on.
     * @param thinkingDefaultOn     thinking is on unless {@code enable_thinking} is false (otherwise: only when true).
     * @param honorPreserveThinking assistant turns before the last user query keep their think block unless
     *                              {@code preserve_thinking} is false (otherwise: only turns after that query do).
     * @param splitThinkFromContent a {@code </think>} inside assistant content is split into reasoning and answer.
     * @param tojsonScalars         non-string tool arguments are rendered as JSON (otherwise only mappings and lists;
     *                              scalars as Python {@code str()}).
     */
    public record Variant(boolean reasoningEffort, boolean thinkingDefaultOn, boolean honorPreserveThinking,
                          boolean splitThinkFromContent, boolean tojsonScalars) {
        /** Qwen3.8. */
        public static final Variant QWEN3_8 = new Variant(true, true, true, false, true);
        /** Qwen3.5 (thinking by default). */
        public static final Variant QWEN3_5 = new Variant(false, true, false, true, false);
        /** Small Qwen3.5 models (thinking only when requested). */
        public static final Variant QWEN3_5_SMALL = new Variant(false, false, false, true, false);

        /**
         * Reads the behaviours from the text of a {@code chat_template.jinja}.
         *
         * @param jinja the template source.
         * @return the variant.
         */
        public static Variant detect(String jinja) {
            return new Variant(
                    jinja.contains("reasoning_effort"),
                    !jinja.contains("enable_thinking is defined and enable_thinking is true"),
                    jinja.contains("preserve_thinking is undefined"),
                    jinja.contains("content.split('</think>')"),
                    !jinja.contains("args_value is mapping or"));
        }
    }

    private final Variant variant;
    private final boolean addGenerationPrompt;

    /** Qwen3.8 behaviour with an open assistant turn. */
    public Qwen3ChatTemplate() {
        this(Variant.QWEN3_8, true);
    }

    /**
     * Creates the template of a checkpoint.
     *
     * @param variant             the checkpoint's behaviours.
     * @param addGenerationPrompt whether to append the open assistant turn.
     */
    public Qwen3ChatTemplate(Variant variant, boolean addGenerationPrompt) {
        this.variant = variant;
        this.addGenerationPrompt = addGenerationPrompt;
    }

    /** Whether thinking is on for these options: {@code enabled}/{@code disabled} are explicit, anything else is the default. */
    private boolean thinking(ChatOptions options) {
        String mode = options.thinkingMode();
        if (ChatOptions.THINKING_ENABLED.equals(mode)) {
            return true;
        }
        if (ChatOptions.THINKING_DISABLED.equals(mode)) {
            return false;
        }
        return variant.thinkingDefaultOn();
    }

    /** The Jinja {@code render_content} macro on the ordered parts of a message. */
    private static String renderContent(Message message, boolean system) {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : message.parts()) {
            switch (part) {
                case TextPart t -> sb.append(t.text());
                case ImageUrlPart ignored -> {
                    if (system) {
                        throw new IllegalArgumentException("System message cannot contain images.");
                    }
                    sb.append("<|vision_start|><|image_pad|><|vision_end|>");
                }
                case VideoUrlPart ignored -> {
                    if (system) {
                        throw new IllegalArgumentException("System message cannot contain videos.");
                    }
                    sb.append("<|vision_start|><|video_pad|><|vision_end|>");
                }
                case AudioUrlPart ignored -> throw new IllegalArgumentException("Unexpected item type in content.");
            }
        }
        return sb.toString();
    }

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        if (dialog == null || dialog.isEmpty()) {
            throw new IllegalArgumentException("No messages provided.");
        }
        ChatOptions opts = options == null ? ChatOptions.NONE : options;
        boolean thinking = thinking(opts);

        String instructions = "";
        if (variant.reasoningEffort() && thinking) {
            String effort = opts.reasoningEffort() == null ? "xhigh" : opts.reasoningEffort();
            instructions = switch (effort) {
                case "xhigh" -> XHIGH;
                case "low" -> LOW;
                case "medium" -> "";
                default -> throw new IllegalArgumentException("Unexpected reasoning effort " + effort
                        + ". Supported types are xhigh (default), medium, and low.");
            };
        }

        StringBuilder sb = new StringBuilder();
        List<ToolDefinition> tools = opts.toolsForTemplate();
        Message first = dialog.getFirst();
        boolean firstIsSystem = first.role() == Role.system;
        String systemContent = firstIsSystem ? PyJson.strip(renderContent(first, true)) : "";
        if (!tools.isEmpty()) {
            sb.append("<|im_start|>system\n");
            if (!instructions.isEmpty()) {
                sb.append(instructions).append("\n\n");
            }
            sb.append("# Tools\n\nYou have access to the following functions:\n\n<tools>");
            for (ToolDefinition tool : tools) {
                sb.append('\n').append(PyJson.dumps(toolMap(tool)));
            }
            sb.append("\n</tools>");
            if (!(opts.toolChoice() instanceof ToolChoice.None)) {
                sb.append(TOOL_INSTRUCTIONS);
            }
            if (opts.toolChoice() instanceof ToolChoice.Required) {
                sb.append("\n\nYou MUST call at least one function.");
            }
            if (!systemContent.isEmpty()) {
                sb.append("\n\n").append(systemContent);
            }
            sb.append("<|im_end|>\n");
        } else if (variant.reasoningEffort()) {
            if (firstIsSystem && !systemContent.isEmpty()) {
                sb.append("<|im_start|>system\n").append(instructions.isEmpty() ? "" : instructions + "\n\n")
                        .append(systemContent).append("<|im_end|>\n");
            } else if (!instructions.isEmpty()) {
                sb.append("<|im_start|>system\n").append(instructions).append("<|im_end|>\n");
            }
        } else if (firstIsSystem) {
            sb.append("<|im_start|>system\n").append(systemContent).append("<|im_end|>\n");
        }

        // ns.last_query_index: the last user message that is not just wrapped tool output
        int lastQuery = dialog.size() - 1;
        boolean found = false;
        for (int i = dialog.size() - 1; i >= 0; i--) {
            Message m = dialog.get(i);
            if (m.role() == Role.user) {
                String content = PyJson.strip(renderContent(m, false));
                if (!(content.startsWith("<tool_response>") && content.endsWith("</tool_response>"))) {
                    lastQuery = i;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            throw new IllegalArgumentException("No user query found in messages.");
        }

        for (int i = 0; i < dialog.size(); i++) {
            Message message = dialog.get(i);
            String content = PyJson.strip(renderContent(message, message.role() == Role.system));
            switch (message.role()) {
                case system -> {
                    if (i != 0) {
                        throw new IllegalArgumentException("System message must be at the beginning.");
                    }
                }
                case user -> sb.append("<|im_start|>user\n").append(content).append("<|im_end|>\n");
                case assistant -> {
                    String reasoning = "";
                    if (variant.splitThinkFromContent() && content.contains("</think>")) {
                        String[] segments = content.split("</think>", -1);
                        String head = PyJson.rstripNewlines(segments[0]);
                        String[] opened = head.split("<think>", -1);
                        reasoning = PyJson.lstripNewlines(opened[opened.length - 1]);
                        content = PyJson.lstripNewlines(segments[segments.length - 1]);
                    }
                    reasoning = PyJson.strip(reasoning);
                    boolean keepThink = i > lastQuery
                            || (variant.honorPreserveThinking()
                                && (opts.preserveThinking() == null || opts.preserveThinking()));
                    if (keepThink) {
                        sb.append("<|im_start|>assistant\n<think>\n").append(reasoning).append("\n</think>\n\n")
                                .append(content);
                    } else {
                        sb.append("<|im_start|>assistant\n").append(content);
                    }
                    List<ToolCall> calls = message.toolCalls();
                    if (calls != null) {
                        boolean firstCall = true;
                        for (ToolCall call : calls) {
                            FunctionCall fn = call.function();
                            if (firstCall) {
                                sb.append(PyJson.strip(content).isEmpty() ? "" : "\n\n");
                                sb.append("<tool_call>\n<function=").append(fn.name()).append(">\n");
                                firstCall = false;
                            } else {
                                sb.append("\n<tool_call>\n<function=").append(fn.name()).append(">\n");
                            }
                            appendParameters(sb, fn.arguments());
                            sb.append("</function>\n</tool_call>");
                        }
                    }
                    sb.append("<|im_end|>\n");
                }
                case tool -> {
                    Message prev = i > 0 ? dialog.get(i - 1) : null;
                    if (prev != null && prev.role() != Role.tool) {
                        sb.append("<|im_start|>user");
                    }
                    sb.append("\n<tool_response>\n").append(content).append("\n</tool_response>");
                    boolean last = i == dialog.size() - 1;
                    if (last || dialog.get(i + 1).role() != Role.tool) {
                        sb.append("<|im_end|>\n");
                    }
                }
                default -> throw new IllegalArgumentException("Unexpected message role.");
            }
        }

        if (addGenerationPrompt) {
            sb.append("<|im_start|>assistant\n");
            sb.append(thinking ? "<think>\n" : "<think>\n\n</think>\n\n");
        }
        return sb.toString();
    }

    private static Map<String, Object> toolMap(ToolDefinition tool) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", tool.type());
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", tool.function().name());
        if (tool.function().description() != null) {
            fn.put("description", tool.function().description());
        }
        if (tool.function().parameters() != null) {
            fn.put("parameters", tool.function().parameters());
        }
        root.put("function", fn);
        return root;
    }

    /** The {@code <parameter=name>} blocks of a call; arguments are the JSON object the model produced. */
    private void appendParameters(StringBuilder sb, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return;
        }
        Object parsed;
        try {
            parsed = MAPPER.readValue(argumentsJson, Object.class);
        } catch (Exception e) {
            return;   // not a JSON object: leave the function body empty
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object value = e.getValue();
            sb.append("<parameter=").append(e.getKey()).append(">\n");
            if (value instanceof CharSequence s) {
                sb.append(s);
            } else if (variant.tojsonScalars() || value instanceof Map || value instanceof List) {
                sb.append(PyJson.dumps(value));
            } else {
                sb.append(PyJson.pyStr(value));
            }
            sb.append("\n</parameter>\n");
        }
    }

    /**
     * Encodes dialog to a prompt string (generation prompt open) with the Qwen3.8 behaviours.
     *
     * @param dialog  conversation turns.
     * @param options chat options; may be {@code null}.
     * @return prompt text.
     */
    public static String encodeDialog(Message[] dialog, ChatOptions options) {
        List<Message> list = dialog == null ? List.of() : List.of(dialog);
        return new Qwen3ChatTemplate().encode(new ArrayList<>(list), options);
    }
}
