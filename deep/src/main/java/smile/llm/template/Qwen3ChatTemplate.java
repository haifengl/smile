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
import smile.llm.ChatOptions;
import smile.llm.FunctionCall;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.ToolCall;
import smile.llm.ToolChoice;
import smile.llm.ToolDefinition;

/**
 * Java port of the Qwen3 / Qwen3.5 / Qwen3.8 {@code chat_template.jinja}
 * tool-calling path (XML {@code <tool_call>} format).
 *
 * <p>Reference: Hugging Face {@code Qwen/Qwen3.8-27B} chat template.
 *
 * @author Haifeng Li
 */
public final class Qwen3ChatTemplate implements ChatTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOOL_INSTRUCTIONS = """


            If you choose to call a function ONLY reply in the following format with NO suffix:

            <tool_call>
            <function=example_function_name>
            <parameter=example_parameter_1>
            value_1
            </parameter>
            <parameter=example_parameter_2>
            This is the value for the second parameter
            that can span
            multiple lines
            </parameter>
            </function>
            </tool_call>

            <IMPORTANT>
            Reminder:
            - Function calls MUST follow the specified format: an inner <function=...></function> block must be nested within <tool_call></tool_call> XML tags
            - Required parameters MUST be specified
            - You may provide optional reasoning for your function call in natural language BEFORE the function call, but NOT after
            - If there is no function call available, answer the question like normal with your current knowledge and do not tell the user about function calls
            </IMPORTANT>""".stripIndent();

    private final boolean addGenerationPrompt;
    private final boolean enableThinking;

    /**
     * Default template: open assistant turn, thinking enabled.
     */
    public Qwen3ChatTemplate() {
        this(true, true);
    }

    /**
     * @param addGenerationPrompt when {@code true}, append open assistant header.
     * @param enableThinking      when {@code true}, open with {@code <think>\\n}.
     */
    public Qwen3ChatTemplate(boolean addGenerationPrompt, boolean enableThinking) {
        this.addGenerationPrompt = addGenerationPrompt;
        this.enableThinking = enableThinking;
    }

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        if (dialog == null || dialog.isEmpty()) {
            throw new IllegalArgumentException("No messages provided.");
        }
        ChatOptions opts = options == null ? ChatOptions.NONE : options;
        StringBuilder sb = new StringBuilder();

        List<ToolDefinition> tools = opts.toolsForTemplate();
        boolean injectTools = !tools.isEmpty();
        boolean skipFirstSystem = false;

        if (injectTools) {
            sb.append("<|im_start|>system\n");
            sb.append("# Tools\n\nYou have access to the following functions:\n\n<tools>");
            for (ToolDefinition tool : tools) {
                sb.append('\n');
                sb.append(toToolJson(tool));
            }
            sb.append("\n</tools>");
            if (!(opts.toolChoice() instanceof ToolChoice.None)) {
                sb.append(TOOL_INSTRUCTIONS);
            }
            if (opts.toolChoice() instanceof ToolChoice.Required) {
                sb.append("\n\nYou MUST call at least one function.");
            }
            if (dialog.getFirst().role() == Role.system) {
                String sys = dialog.getFirst().content().trim();
                if (!sys.isEmpty()) {
                    sb.append("\n\n").append(sys);
                }
                skipFirstSystem = true;
            }
            sb.append("<|im_end|>\n");
        } else if (dialog.getFirst().role() == Role.system) {
            String sys = dialog.getFirst().content().trim();
            if (!sys.isEmpty()) {
                sb.append("<|im_start|>system\n").append(sys).append("<|im_end|>\n");
            }
            skipFirstSystem = true;
        }

        boolean inToolGroup = false;
        for (int i = 0; i < dialog.size(); i++) {
            Message message = dialog.get(i);
            if (skipFirstSystem && i == 0 && message.role() == Role.system) {
                continue;
            }
            String content = message.content().trim();
            switch (message.role()) {
                case system -> {
                    if (i != 0) {
                        throw new IllegalArgumentException("System message must be at the beginning.");
                    }
                }
                case user -> {
                    closeToolGroup(sb, inToolGroup);
                    inToolGroup = false;
                    sb.append("<|im_start|>user\n").append(content).append("<|im_end|>\n");
                }
                case assistant -> {
                    closeToolGroup(sb, inToolGroup);
                    inToolGroup = false;
                    sb.append("<|im_start|>assistant\n");
                    if (!content.isEmpty()) {
                        sb.append(content);
                    }
                    if (message.hasToolCalls()) {
                        boolean first = true;
                        for (ToolCall call : message.toolCalls()) {
                            FunctionCall fn = call.function();
                            if (first) {
                                if (!content.isEmpty()) {
                                    sb.append("\n\n<tool_call>\n<function=").append(fn.name()).append(">\n");
                                } else {
                                    sb.append("<tool_call>\n<function=").append(fn.name()).append(">\n");
                                }
                                first = false;
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
                    if (!inToolGroup) {
                        sb.append("<|im_start|>user");
                        inToolGroup = true;
                    }
                    sb.append("\n<tool_response>\n").append(content).append("\n</tool_response>");
                    Message next = i + 1 < dialog.size() ? dialog.get(i + 1) : null;
                    if (next == null || next.role() != Role.tool) {
                        sb.append("<|im_end|>\n");
                        inToolGroup = false;
                    }
                }
            }
        }
        closeToolGroup(sb, inToolGroup);

        if (addGenerationPrompt) {
            sb.append("<|im_start|>assistant\n");
            if (enableThinking) {
                sb.append("<think>\n");
            } else {
                sb.append("<think>\n\n</think>\n\n");
            }
        }
        return sb.toString();
    }

    private static void closeToolGroup(StringBuilder sb, boolean inToolGroup) {
        if (inToolGroup) {
            sb.append("<|im_end|>\n");
        }
    }

    private static String toToolJson(ToolDefinition tool) {
        try {
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
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tool definition", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendParameters(StringBuilder sb, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return;
        }
        try {
            Object parsed = MAPPER.readValue(argumentsJson, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                return;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                String key = String.valueOf(e.getKey());
                Object value = e.getValue();
                sb.append("<parameter=").append(key).append(">\n");
                if (value instanceof String s) {
                    sb.append(s);
                } else {
                    sb.append(MAPPER.writeValueAsString(value));
                }
                sb.append("\n</parameter>\n");
            }
        } catch (Exception ignored) {
            // leave function body empty when arguments are not a JSON object
        }
    }

    /**
     * Encodes dialog to a prompt string (generation prompt open).
     *
     * @param dialog conversation turns.
     * @param options chat options; may be {@code null}.
     * @return prompt text.
     */
    public static String encodeDialog(Message[] dialog, ChatOptions options) {
        List<Message> list = dialog == null ? List.of() : List.of(dialog);
        return new Qwen3ChatTemplate().encode(new ArrayList<>(list), options);
    }
}
