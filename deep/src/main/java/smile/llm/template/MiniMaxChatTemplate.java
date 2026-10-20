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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.Role;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;
import smile.llm.VideoUrlPart;

/**
 * Java port of the MiniMax-M3 {@code chat_template.jinja}: a system block with thinking instructions, a
 * developer block (the user's system prompt and the tool list), then one block per turn, each introduced by
 * {@code ]~b]} and closed by {@code [e~[}. Output is verified byte for byte against the Jinja template
 * rendered by Hugging Face transformers.
 *
 * <p>Differences from the Jinja template, all because {@link Message} is not an arbitrary JSON object:
 * the {@code root} role does not exist (the system prompt is always the developer block), assistant
 * {@code reasoning_content} does not exist (reasoning is taken from {@code <mm:think>...</mm:think>} in the
 * content), and {@code <think>}/{@code </think>} in assistant content, which is what this server returns to
 * clients, are read as the MiniMax tags. As in the template, a {@code system} message that is not the first
 * message is dropped.
 *
 * @author Haifeng Li
 */
public final class MiniMaxChatTemplate implements ChatTemplate {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Namespace prefix of tool-call markup. */
    public static final String NS = "]<]minimax[>[";
    /** Beginning-of-document marker that starts the system block. */
    static final String BOD = "]~!b[";
    /** Beginning of a turn. */
    static final String BOS = "]~b]";
    /** End of a turn. */
    static final String EOS = "[e~[";
    /** Start of a tool-call block. */
    public static final String TOOLCALL_BEGIN = NS + "<tool_call>";
    /** End of a tool-call block. */
    public static final String TOOLCALL_END = NS + "</tool_call>";
    /** Start of a reasoning span. */
    public static final String THINK_BEGIN = "<mm:think>";
    /** End of a reasoning span. */
    public static final String THINK_END = "</mm:think>";
    /** Image placeholder. */
    static final String IMAGE = "]<]image[>[";
    /** Video placeholder. */
    static final String VIDEO = "]<]video[>[";

    private final boolean addGenerationPrompt;
    private final String thinkingMode;

    /**
     * Default template: open assistant turn, thinking mode unspecified (the model decides).
     */
    public MiniMaxChatTemplate() {
        this(true, null);
    }

    /**
     * Creates a template.
     *
     * @param addGenerationPrompt append the open assistant turn.
     * @param thinkingMode        {@code enabled}, {@code disabled}, {@code adaptive} or {@code null} (undefined).
     */
    public MiniMaxChatTemplate(boolean addGenerationPrompt, String thinkingMode) {
        this.addGenerationPrompt = addGenerationPrompt;
        this.thinkingMode = thinkingMode;
    }

    /**
     * Creates the template for a request.
     *
     * @param options chat options; may be {@code null}.
     * @return a template with an open assistant turn and the options' thinking mode.
     */
    public static MiniMaxChatTemplate forOptions(ChatOptions options) {
        return new MiniMaxChatTemplate(true, options == null ? null : options.thinkingMode());
    }

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        if (dialog == null || dialog.isEmpty()) {
            throw new IllegalArgumentException("No messages provided.");
        }
        ChatOptions opts = options == null ? ChatOptions.NONE : options;
        List<ToolDefinition> tools = opts.toolsForTemplate();

        Message developer = null;
        List<Message> conversation = dialog;
        if (dialog.getFirst().role() == Role.system) {
            developer = dialog.getFirst();
            conversation = dialog.subList(1, dialog.size());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(BOD).append(BOS).append("system\n");
        sb.append("Your model version is MiniMax-M3, developed by MiniMax. Knowledge cutoff: January 2026. "
                + "Founded in early 2022, MiniMax is a global AI foundation model company committed to "
                + "advancing the frontiers of AI towards AGI.");
        appendThinkingInstructions(sb);
        sb.append(EOS).append('\n');

        sb.append(BOS).append("developer\n");
        String developerText = developer == null ? "" : visibleText(developer);
        sb.append(developerText.isEmpty() ? "You are a helpful assistant." : developerText);
        if (!tools.isEmpty()) {
            appendTools(sb, tools);
        }
        sb.append(EOS).append('\n');

        String lastToolCall = null;
        for (int i = 0; i < conversation.size(); i++) {
            Message message = conversation.get(i);
            switch (message.role()) {
                case assistant -> {
                    lastToolCall = appendAssistant(sb, message);
                }
                case tool -> {
                    if (lastToolCall == null) {
                        throw new IllegalArgumentException(
                                "Message has tool role, but there was no previous assistant message with a tool call!");
                    }
                    boolean first = i == 0 || conversation.get(i - 1).role() != Role.tool;
                    boolean last = i == conversation.size() - 1 || conversation.get(i + 1).role() != Role.tool;
                    if (first) {
                        sb.append(BOS).append("tool");
                    }
                    sb.append("\n<response>").append(visibleText(message)).append("</response>");
                    if (last) {
                        sb.append(EOS).append('\n');
                    }
                }
                case user -> sb.append(BOS).append("user\n").append(visibleText(message)).append(EOS).append('\n');
                default -> { /* a system message after the first one is not rendered */ }
            }
        }

        if (addGenerationPrompt) {
            sb.append(BOS).append("ai\n");
            if ("disabled".equals(thinkingMode)) {
                sb.append(THINK_END);
            } else if ("enabled".equals(thinkingMode)) {
                sb.append(THINK_BEGIN);
            }
        }
        return sb.toString();
    }

    private void appendThinkingInstructions(StringBuilder sb) {
        sb.append("\n\n<thinking_instructions>\n");
        sb.append("You have a thinking capability that allows you to reason step by step before responding. "
                + "When thinking is enabled, wrap your reasoning in ").append(THINK_BEGIN).append(THINK_END)
                .append(" tags before your response. When thinking is disabled, begin your response directly after the ")
                .append(THINK_END)
                .append(" prefix. When thinking is adaptive, decide on your own whether to think for the current turn.\n");
        if ("enabled".equals(thinkingMode)) {
            sb.append("Current thinking mode: enabled. You MUST think step by step before every response, "
                    + "including after receiving function/tool results.\n");
        } else if ("disabled".equals(thinkingMode)) {
            sb.append("Current thinking mode: disabled. Do not output any thinking process.\n");
        } else {
            // "adaptive" and undefined render the same instruction.
            sb.append("Current thinking mode: adaptive. You are encouraged to think for complex decision-making, "
                    + "multi-step reasoning, or when analyzing function/tool results.\n");
        }
        sb.append("</thinking_instructions>");
    }

    private static void appendTools(StringBuilder sb, List<ToolDefinition> tools) {
        sb.append("\n\n# Tools\nYou may call one or more tools to assist with the user query.\n"
                + "Here are the tools available in JSONSchema format:\n");
        sb.append("\n<tools>\n");
        for (ToolDefinition tool : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", tool.function().name());
            if (tool.function().description() != null) {
                fn.put("description", tool.function().description());
            }
            if (tool.function().parameters() != null) {
                fn.put("parameters", tool.function().parameters());
            }
            sb.append("<tool>").append(PyJson.dumps(fn)).append("</tool>\n");
        }
        sb.append("</tools>\n\n");
        sb.append("To call tools, wrap all invocations in a single ").append(TOOLCALL_BEGIN).append(TOOLCALL_END)
                .append(" block. Parameter values containing nested objects or arrays are recursively expanded "
                        + "into XML elements. Example:\n");
        sb.append('\n').append(TOOLCALL_BEGIN).append('\n');
        sb.append(NS).append("<invoke name=\"tool-name-1\">");
        sb.append(NS).append("<param-1>value-1").append(NS).append("</param-1>");
        sb.append(NS).append("<param-2>");
        sb.append(NS).append("<item>");
        sb.append(NS).append("<key-a>val-a").append(NS).append("</key-a>");
        sb.append(NS).append("<key-b>val-b").append(NS).append("</key-b>");
        sb.append(NS).append("</item>");
        sb.append(NS).append("</param-2>");
        sb.append(NS).append("</invoke>\n");
        sb.append(NS).append("<invoke name=\"tool-name-2\">");
        sb.append(NS).append("<param-1>value-1").append(NS).append("</param-1>");
        sb.append(NS).append("</invoke>\n");
        sb.append(TOOLCALL_END);
    }

    /** Appends an assistant turn and returns the name of its last tool call, or {@code null} if it has none. */
    private static String appendAssistant(StringBuilder sb, Message message) {
        sb.append(BOS).append("ai\n");
        String content = visibleText(message)
                .replace("</think>", THINK_END).replace("<think>", THINK_BEGIN);
        String reasoning = "";
        if (content.contains(THINK_END)) {
            String[] parts = content.split(java.util.regex.Pattern.quote(THINK_END), -1);
            String before = stripNewlines(parts[0]);
            String[] opens = before.split(java.util.regex.Pattern.quote(THINK_BEGIN), -1);
            reasoning = stripNewlines(opens[opens.length - 1]);
            content = stripNewlines(parts[parts.length - 1]);
        }
        if (!reasoning.isEmpty()) {
            sb.append(THINK_BEGIN).append(reasoning).append(THINK_END);
        } else {
            sb.append(THINK_END);
        }
        if (!content.isEmpty()) {
            sb.append(content);
        }
        List<ToolCall> calls = message.toolCalls();
        String last = null;
        if (calls != null && !calls.isEmpty()) {
            sb.append(TOOLCALL_BEGIN).append('\n');
            for (ToolCall call : calls) {
                sb.append(NS).append("<invoke name=\"").append(call.function().name()).append("\">");
                JsonNode args = parseArguments(call.function().arguments());
                if (args.isObject()) {
                    for (Map.Entry<String, JsonNode> e : args.properties()) {
                        if (e.getValue().isNull()) {
                            continue;
                        }
                        sb.append(NS).append('<').append(e.getKey()).append('>');
                        toXml(sb, e.getValue());
                        sb.append(NS).append("</").append(e.getKey()).append('>');
                    }
                }
                sb.append(NS).append("</invoke>\n");
                last = call.function().name();
            }
            sb.append(TOOLCALL_END);
        }
        sb.append(EOS).append('\n');
        return last;
    }

    private static JsonNode parseArguments(String json) {
        try {
            return MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (RuntimeException e) {
            return MAPPER.createObjectNode();
        }
    }

    /** The Jinja macro {@code to_xml}: nested objects and arrays become namespaced XML elements. */
    private static void toXml(StringBuilder sb, JsonNode val) {
        if (val.isObject()) {
            for (Map.Entry<String, JsonNode> e : val.properties()) {
                if (e.getValue().isNull()) {
                    continue;
                }
                sb.append(NS).append('<').append(e.getKey()).append('>');
                toXml(sb, e.getValue());
                sb.append(NS).append("</").append(e.getKey()).append('>');
            }
        } else if (val.isArray()) {
            for (JsonNode item : val) {
                sb.append(NS).append("<item>");
                toXml(sb, item);
                sb.append(NS).append("</item>");
            }
        } else if (val.isNull()) {
            // Skipped, as in the template.
        } else if (val.isBoolean()) {
            sb.append(val.booleanValue() ? "true" : "false");
        } else if (val.isFloatingPointNumber()) {
            sb.append(PyJson.floatRepr(val.doubleValue()));
        } else if (val.isNumber()) {
            sb.append(val.numberValue());
        } else {
            sb.append(val.asString());
        }
    }

    /** The Jinja macro {@code visible_text}: text parts verbatim, images and videos as placeholders. */
    static String visibleText(Message message) {
        StringBuilder sb = new StringBuilder();
        for (ContentPart part : message.parts()) {
            switch (part) {
                case TextPart t -> sb.append(t.text());
                case ImageUrlPart ignored -> sb.append(IMAGE);
                case VideoUrlPart ignored -> sb.append(VIDEO);
                default -> { /* audio has no placeholder in this template */ }
            }
        }
        return sb.toString();
    }

    /** Python {@code str.strip('\n')}. */
    private static String stripNewlines(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '\n') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(start, end);
    }
}
