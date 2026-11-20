/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import smile.llm.AudioUrlPart;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;
import smile.llm.VideoUrlPart;
import tools.jackson.databind.ObjectMapper;

/**
 * Java port of the GLM-5.3-Flash {@code chat_template.jinja}, byte for byte (the golden test compares it with
 * transformers' rendering of the real template).
 *
 * <p>The prompt starts with {@code [gMASK]<sop>} and a system line {@code Reasoning Effort: Low|High|Max}
 * ({@code max} unless {@link ChatOptions#reasoningEffort()} is {@code low} or {@code high}), then the tool
 * definitions, then one block per message, and ends with the open generation prompt
 * {@code <|assistant|><think>}. Assistant turns carry {@code <think>reasoning</think>} before the answer; with
 * {@code clear_thinking} (here: {@link ChatOptions#preserveThinking()} is {@code false}) the reasoning of turns
 * before the last user message is replaced by an empty block. Tool results of one assistant turn share one
 * {@code <|observation|>} block and are put in the order of the calls when every id matches.
 *
 * <p>Differences from the Jinja template's inputs: {@link Message} has no {@code reasoning_content} field, so
 * reasoning is only recovered from a {@code <think>..</think>} inside the assistant text; tool results given as
 * lists of outputs or tool references are not representable; the template has no thinking switch, so
 * {@link ChatOptions#thinkingMode()} is ignored.
 *
 * @author Haifeng Li
 */
public final class GlmChatTemplate implements ChatTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TOOLS_HEAD = "<|system|>\n# Tools\n\nYou may call one or more functions to assist "
            + "with the user query.\n\nYou are provided with function signatures within <tools></tools> XML tags:\n"
            + "<tools>\n";
    private static final String TOOLS_TAIL = "</tools>\n\nFor each function call, output the function name and "
            + "arguments within the following XML format:\n<tool_call>{function-name}<arg_key>{arg-key-1}</arg_key>"
            + "<arg_value>{arg-value-1}</arg_value><arg_key>{arg-key-2}</arg_key><arg_value>{arg-value-2}</arg_value>"
            + "...</tool_call>";

    private final boolean addGenerationPrompt;

    /** A template with an open assistant turn. */
    public GlmChatTemplate() {
        this(true);
    }

    /**
     * Creates the template.
     *
     * @param addGenerationPrompt whether to append the open assistant turn ({@code <|assistant|><think>}).
     */
    public GlmChatTemplate(boolean addGenerationPrompt) {
        this.addGenerationPrompt = addGenerationPrompt;
    }

    /** The Jinja {@code visible_text} macro on the ordered parts of a message. */
    private static String visibleText(Message message) {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : message.parts()) {
            switch (part) {
                case TextPart t -> sb.append(t.text());
                case ImageUrlPart ignored -> sb.append("<|begin_of_image|><|image|><|end_of_image|>");
                case VideoUrlPart ignored -> sb.append("<|begin_of_video|><|video|><|end_of_video|>");
                case AudioUrlPart ignored -> sb.append("<|begin_of_audio|><|end_of_audio|>");
            }
        }
        return sb.toString();
    }

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        if (dialog == null) {
            throw new IllegalArgumentException("No messages provided.");
        }
        ChatOptions opts = options == null ? ChatOptions.NONE : options;
        String effort = opts.reasoningEffort();
        String level = "low".equals(effort) ? "Low" : "high".equals(effort) ? "High" : "Max";
        boolean clearThinking = Boolean.FALSE.equals(opts.preserveThinking());

        StringBuilder sb = new StringBuilder("[gMASK]<sop><|system|>Reasoning Effort: ").append(level);
        List<ToolDefinition> tools = opts.toolsForTemplate();
        if (!tools.isEmpty()) {
            sb.append(TOOLS_HEAD);
            for (ToolDefinition tool : tools) {
                sb.append(PyJson.dumps(toolMap(tool))).append('\n');
            }
            sb.append(TOOLS_TAIL);
        }

        int lastUser = -1;
        for (int i = 0; i < dialog.size(); i++) {
            if (dialog.get(i).role() == Role.user) {
                lastUser = i;
            }
        }

        for (int i = 0; i < dialog.size(); i++) {
            Message m = dialog.get(i);
            switch (m.role()) {
                case user -> sb.append("<|user|>").append(visibleText(m));
                case system -> sb.append("<|system|>").append(visibleText(m));
                case assistant -> appendAssistant(sb, m, !clearThinking || i > lastUser);
                case tool -> {
                    if (i == 0 || dialog.get(i - 1).role() != Role.tool) {
                        appendObservations(sb, dialog, i);
                    }
                }
            }
        }
        if (addGenerationPrompt) {
            sb.append("<|assistant|><think>");
        }
        return sb.toString();
    }

    private static void appendAssistant(StringBuilder sb, Message m, boolean keepReasoning) {
        sb.append("<|assistant|>");
        String content = visibleText(m);
        String reasoning = null;
        int close = content.indexOf("</think>");
        if (close >= 0) {
            String head = content.substring(0, close);
            reasoning = head.substring(head.lastIndexOf("<think>") < 0 ? 0 : head.lastIndexOf("<think>") + 7);
            content = content.substring(content.lastIndexOf("</think>") + 8);
        }
        if (keepReasoning && reasoning != null) {
            sb.append("<think>").append(reasoning).append("</think>");
        } else {
            sb.append("<think></think>");
        }
        String stripped = PyJson.strip(content);
        if (!stripped.isEmpty()) {
            sb.append(stripped);
        }
        List<ToolCall> calls = m.toolCalls();
        if (calls != null) {
            for (ToolCall call : calls) {
                sb.append("<tool_call>").append(call.function().name());
                appendArguments(sb, call.function().arguments());
                sb.append("</tool_call>");
            }
        }
    }

    /** The {@code <arg_key>/<arg_value>} pairs of a call; arguments are the JSON object the model produced. */
    private static void appendArguments(StringBuilder sb, String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return;
        }
        Object parsed;
        try {
            parsed = MAPPER.readValue(argumentsJson, Object.class);
        } catch (Exception e) {
            return;   // not a JSON object: leave the call without arguments
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sb.append("<arg_key>").append(e.getKey()).append("</arg_key><arg_value>");
            sb.append(e.getValue() instanceof CharSequence s ? s : PyJson.dumps(e.getValue()));
            sb.append("</arg_value>");
        }
    }

    /** The tool results of the run of consecutive tool messages starting at {@code start}. */
    private static void appendObservations(StringBuilder sb, List<Message> dialog, int start) {
        sb.append("<|observation|>");
        int end = start;
        while (end + 1 < dialog.size() && dialog.get(end + 1).role() == Role.tool) {
            end++;
        }
        List<Message> results = dialog.subList(start, end + 1);
        List<ToolCall> calls = start > 0 && dialog.get(start - 1).role() == Role.assistant
                ? dialog.get(start - 1).toolCalls() : null;

        boolean canSort = calls != null && !calls.isEmpty();
        List<String> resultIds = new ArrayList<>();
        List<String> callIds = new ArrayList<>();
        if (canSort) {
            for (Message r : results) {
                resultIds.add(r.toolCallId() == null ? "" : r.toolCallId());
            }
            for (ToolCall c : calls) {
                callIds.add(c.id() == null ? "" : c.id());
            }
            Set<String> resultSet = new HashSet<>(resultIds);
            Set<String> callSet = new HashSet<>(callIds);
            canSort = !resultSet.contains("") && !callSet.contains("")
                    && resultSet.size() == resultIds.size() && callSet.size() == callIds.size()
                    && callSet.containsAll(resultSet);
        }
        if (canSort) {
            for (String id : callIds) {
                int at = resultIds.indexOf(id);
                if (at >= 0) {
                    sb.append("<tool_response>").append(results.get(at).content()).append("</tool_response>");
                }
            }
        } else {
            for (Message r : results) {
                sb.append("<tool_response>").append(r.content()).append("</tool_response>");
            }
        }
    }

    private static Map<String, Object> toolMap(ToolDefinition tool) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", tool.function().name());
        if (tool.function().description() != null) {
            fn.put("description", tool.function().description());
        }
        if (tool.function().parameters() != null) {
            fn.put("parameters", tool.function().parameters());
        }
        return fn;
    }
}
