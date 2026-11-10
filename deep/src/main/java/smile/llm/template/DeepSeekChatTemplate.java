/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;
import smile.llm.ChatOptions;
import smile.llm.ContentPart;
import smile.llm.ImageUrlPart;
import smile.llm.Message;
import smile.llm.TextPart;
import smile.llm.ToolCall;
import smile.llm.ToolDefinition;

/**
 * DeepSeek-V4.1 prompt format, ported from the reference {@code encoding/encoding.py} (which has no Jinja template).
 *
 * <p>The core works on JSON-like messages ({@code Map<String, Object>} holding strings, lists, maps), exactly the
 * shape of the reference, so its test vectors replay unchanged: multi-turn chat, thinking mode with a numeric
 * reasoning effort, mid-conversation system messages, tools rendered as DSML (note the leading space of
 * {@code <｜DSML｜ calls>}, {@code invoke} and {@code parameter}), tool results folded into user turns, and image
 * placeholders. {@link #encode(List, ChatOptions)} adapts {@link Message} lists to it.
 *
 * <p>Known gaps of the adapter: {@link Message} has no {@code reasoning_content}, so assistant turns that are
 * rendered with their reasoning (thinking mode, after the last user message) get an empty one.
 *
 * @author Haifeng Li
 */
public class DeepSeekChatTemplate implements ChatTemplate {
    /** Constructor. */
    public DeepSeekChatTemplate() {
    }

    /** Beginning-of-sentence token. */
    public static final String BOS = "<｜begin▁of▁sentence｜>";
    /** End-of-sentence token, ends every assistant turn. */
    public static final String EOS = "<｜end▁of▁sentence｜>";
    /** Opens a reasoning block. */
    public static final String THINK_START = "<think>";
    /** Closes a reasoning block. */
    public static final String THINK_END = "</think>";
    /** The DSML markup token. */
    public static final String DSML = "｜DSML｜";
    /** Placeholder of one image in the prompt. */
    public static final String IMAGE_PLACEHOLDER = "<｜deepseek_image｜>";

    private static final String USER = "<｜User｜>";
    private static final String ASSISTANT = "<｜Assistant｜>";
    private static final String SYSTEM = "<｜System｜>";
    private static final String LATEST_REMINDER = "<｜latest_reminder｜>";
    private static final Map<String, String> TASK_TOKENS = Map.of(
            "action", "<｜action｜>", "query", "<｜query｜>", "authority", "<｜authority｜>",
            "domain", "<｜domain｜>", "title", "<｜title｜>", "read_url", "<｜read_url｜>");
    private static final String CALLS = " calls";
    private static final String INVOKE = " invoke";
    private static final String PARAMETER = " parameter";
    private static final Map<String, Integer> EFFORTS = Map.of("low", 50, "high", 75, "max", 100);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String TOOLS_TEMPLATE = """
            ## Tools

            You have access to a set of tools to help answer the user's question. You can invoke tools by writing a "<{dsml}{calls}>" block like the following:

            <{dsml}{calls}>
            <{dsml}{invoke} name="$TOOL_NAME">
            <{dsml}{parameter} name="$PARAMETER_NAME" string="true|false">$PARAMETER_VALUE</{dsml}{parameter}>
            ...
            </{dsml}{invoke}>
            <{dsml}{invoke} name="$TOOL_NAME2">
            ...
            </{dsml}{invoke}>
            </{dsml}{calls}>

            String parameters should be specified as is and set `string="true"`. For all other types (numbers, booleans, arrays, objects), pass the value in JSON format and set `string="false"`.

            If thinking_mode is enabled (triggered by {think_start}), you MUST output your complete reasoning inside {think_start}...{think_end} BEFORE any tool calls or final response.

            Otherwise, output directly after {think_end} with tool calls or final response.

            ### Available Tool Schemas

            {schemas}

            You MUST strictly follow the above defined tool name and parameter schemas to invoke tool calls.
            """;

    /**
     * An encoded prompt with the images it refers to.
     *
     * @param prompt the prompt text; each image is an {@link #IMAGE_PLACEHOLDER}.
     * @param images the image records ({@code {type: image, url|source|data: ...}}) in prompt order.
     */
    public record Encoded(String prompt, List<Map<String, Object>> images) { }

    /**
     * One tool call parsed from a completion.
     *
     * @param name      the function name, namespace-qualified ({@code ns::name}) when it has one.
     * @param arguments the arguments as a JSON object text.
     */
    public record ParsedToolCall(String name, String arguments) { }

    /**
     * A completion parsed back into a message.
     *
     * @param content          the answer text.
     * @param reasoningContent the reasoning ({@code ""} outside thinking mode).
     * @param toolCalls        the tool calls.
     */
    public record Completion(String content, String reasoningContent, List<ParsedToolCall> toolCalls) { }

    // ------------------------------------------------------------------------------------------- ChatTemplate

    @Override
    public String encode(List<Message> dialog, ChatOptions options) {
        return encodeWithMedia(dialog, options).prompt();
    }

    /**
     * Encodes a dialog and returns the images it refers to.
     *
     * <p>Tools are rendered into the leading system message; a dialog that has none gets an empty one, as the
     * reference ignores tools attached to any other role. Thinking mode: {@code enabled} and {@code adaptive}
     * think, {@code disabled} and {@code null} do not (the reference default). Reasoning effort: an integer in
     * [1, 100], or {@code low}/{@code medium}/{@code high}/{@code xhigh}/{@code max} (50/75/75/100/100).
     *
     * @param dialog  the conversation.
     * @param options chat options, may be {@code null}.
     * @return the prompt and its images.
     */
    public Encoded encodeWithMedia(List<Message> dialog, ChatOptions options) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message m : dialog) {
            messages.add(toMap(m));
        }
        if (options != null && options.hasTools()) {
            List<Object> tools = new ArrayList<>();
            for (ToolDefinition t : options.toolsForTemplate()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", t.function().name());
                if (t.function().description() != null) {
                    fn.put("description", t.function().description());
                }
                if (t.function().parameters() != null) {
                    fn.put("parameters", t.function().parameters());
                }
                tools.add(map("type", "function", "function", fn));
            }
            if (messages.isEmpty() || !"system".equals(messages.getFirst().get("role"))) {
                messages.addFirst(map("role", "system", "content", ""));
            }
            messages.getFirst().put("tools", tools);
        }
        String mode = "chat";
        Object effort = null;
        if (options != null) {
            if (ChatOptions.THINKING_ENABLED.equals(options.thinkingMode())
                    || ChatOptions.THINKING_ADAPTIVE.equals(options.thinkingMode())) {
                mode = "thinking";
            }
            effort = parseEffort(options.reasoningEffort());
        }
        return encodeMessages(messages, mode, null, true, true, effort);
    }

    private static Object parseEffort(String effort) {
        if (effort == null || effort.isBlank()) {
            return null;
        }
        String e = effort.strip().toLowerCase();
        return switch (e) {
            case "low", "high", "max" -> e;
            case "medium" -> "high";
            case "xhigh" -> "max";
            default -> {
                try {
                    yield Integer.valueOf(e);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("invalid reasoning effort: " + effort);
                }
            }
        };
    }

    private static Map<String, Object> toMap(Message m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", m.role().name());
        boolean text = true;
        for (ContentPart p : m.parts()) {
            text &= p instanceof TextPart;
        }
        if (text) {
            out.put("content", m.content());
        } else {
            List<Object> blocks = new ArrayList<>();
            for (ContentPart p : m.parts()) {
                switch (p) {
                    case TextPart t -> blocks.add(map("type", "text", "text", t.text()));
                    case ImageUrlPart i -> blocks.add(map("type", "image_url", "image_url", map("url", i.url())));
                    default -> throw new UnsupportedOperationException(
                            "DeepSeek-V4.1 prompts support text and image parts only");
                }
            }
            out.put("content", blocks);
        }
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            List<Object> calls = new ArrayList<>();
            for (ToolCall c : m.toolCalls()) {
                calls.add(map("id", c.id(), "type", "function",
                        "function", map("name", c.function().name(), "arguments", c.function().arguments())));
            }
            out.put("tool_calls", calls);
        }
        if (m.toolCallId() != null) {
            out.put("tool_call_id", m.toolCallId());
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------- encoding

    /**
     * Encodes JSON-like messages (reference {@code encode_messages}).
     *
     * @param messages          the messages; a system message may carry {@code tools} and {@code response_format}.
     * @param thinkingMode      {@code chat} or {@code thinking}.
     * @param context           earlier messages already in the prompt, or {@code null}.
     * @param dropThinking      drop reasoning of turns before the last user message.
     * @param addDefaultBos     prepend the BOS token when there is no context.
     * @param reasoningEffort   an integer in [1, 100], {@code "low"}, {@code "high"}, {@code "max"} or {@code null}.
     * @return the prompt and images.
     */
    public static Encoded encodeMessages(List<Map<String, Object>> messages, String thinkingMode,
                                         List<Map<String, Object>> context, boolean dropThinking,
                                         boolean addDefaultBos, Object reasoningEffort) {
        List<Map<String, Object>> ctx = context == null ? List.of() : context;
        for (var m : concat(ctx, messages)) {
            validateNoImageToken(m);
        }
        List<Map<String, Object>> mergedCtx = mergeToolMessages(ctx);
        List<Map<String, Object>> merged = mergeToolMessages(messages);
        List<Map<String, Object>> all = concat(mergedCtx, merged);
        sortToolResultsByCallOrder(all);
        merged = new ArrayList<>(all.subList(mergedCtx.size(), all.size()));
        List<Map<String, Object>> processedCtx = processImages(mergedCtx, new ArrayList<>());
        List<Map<String, Object>> images = new ArrayList<>();
        List<Map<String, Object>> processed = processImages(merged, images);
        String prompt = encodeText(processed, thinkingMode, processedCtx, dropThinking, addDefaultBos, reasoningEffort);
        return new Encoded(prompt, images);
    }

    /**
     * Encodes JSON-like messages to the prompt text only, with the reference defaults
     * ({@code drop_thinking}, BOS, default effort).
     *
     * @param messages     the messages.
     * @param thinkingMode {@code chat} or {@code thinking}.
     * @return the prompt.
     */
    public static String encodeMessages(List<Map<String, Object>> messages, String thinkingMode) {
        return encodeMessages(messages, thinkingMode, null, true, true, null).prompt();
    }

    private static String encodeText(List<Map<String, Object>> messages, String thinkingMode,
                                     List<Map<String, Object>> context, boolean dropThinking,
                                     boolean addBos, Object effort) {
        List<Map<String, Object>> full = concat(context, messages);
        StringBuilder prompt = new StringBuilder(addBos && context.isEmpty() ? BOS : "");
        boolean effectiveDrop = dropThinking;
        for (var m : full) {
            if (truthy(m.get("tools"))) {
                effectiveDrop = false;
            }
        }
        int numToRender;
        int contextLen;
        if ("thinking".equals(thinkingMode) && effectiveDrop) {
            full = dropThinkingMessages(full);
            numToRender = full.size() - dropThinkingMessages(context).size();
            contextLen = full.size() - numToRender;
        } else {
            numToRender = messages.size();
            contextLen = context.size();
        }
        for (int i = 0; i < numToRender; i++) {
            prompt.append(renderMessage(i + contextLen, full, thinkingMode, effectiveDrop, effort));
        }
        return prompt.toString();
    }

    private static List<Map<String, Object>> dropThinkingMessages(List<Map<String, Object>> messages) {
        int lastUser = findLastUserIndex(messages);
        Set<String> keep = Set.of("user", "system", "tool", "latest_reminder", "direct_search_results");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            Map<String, Object> m = messages.get(i);
            Object role = m.get("role");
            if (keep.contains(role) || i >= lastUser) {
                result.add(m);
            } else if ("assistant".equals(role)) {
                Map<String, Object> copy = new LinkedHashMap<>(m);
                copy.remove("reasoning_content");
                result.add(copy);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static int findLastUserIndex(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object role = messages.get(i).get("role");
            if ("user".equals(role) || ("system".equals(role) && i > 0)) {
                Object blocks = messages.get(i).get("content_blocks");
                if (truthy(blocks)) {
                    boolean any = false;
                    for (Object b : (List<Object>) blocks) {
                        Object t = ((Map<String, Object>) b).get("type");
                        any |= "text".equals(t) || "image".equals(t) || "image_url".equals(t);
                    }
                    if (!any) {
                        continue;
                    }
                }
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static String renderMessage(int index, List<Map<String, Object>> messages, String thinkingMode,
                                        boolean dropThinking, Object effort) {
        if (!"chat".equals(thinkingMode) && !"thinking".equals(thinkingMode)) {
            throw new IllegalArgumentException("Invalid thinking_mode `" + thinkingMode + "`");
        }
        Map<String, Object> msg = messages.get(index);
        int lastUser = findLastUserIndex(messages);
        String role = (String) msg.get("role");
        Object content = msg.get("content");
        Object tools = msg.get("tools");
        Object responseFormat = msg.get("response_format");
        Object toolCalls = msg.get("tool_calls");
        Object reasoning = msg.get("reasoning_content");
        boolean woEos = truthy(msg.get("wo_eos"));

        List<Map<String, Object>> toolList = truthy(tools) ? toolsFromOpenAi((List<Object>) tools) : null;
        List<Map<String, Object>> callList = truthy(toolCalls) ? toolCallsFromOpenAi((List<Object>) toolCalls) : null;

        String effortPrompt = renderReasoningEffort(index, thinkingMode, effort);
        StringBuilder prompt = new StringBuilder();
        if (index == 0 && (!effortPrompt.isEmpty() || "system".equals(role))) {
            prompt.append(SYSTEM);
        }
        prompt.append(effortPrompt);

        switch (role == null ? "" : role) {
            case "system" -> {
                if (index > 0) {
                    prompt.append(SYSTEM);
                }
                prompt.append(str(or(content, "")));
                if (toolList != null) {
                    prompt.append("\n\n").append(renderTools(toolList));
                }
                if (truthy(responseFormat)) {
                    prompt.append("\n\n## Response Format:\n\nYou MUST strictly adhere to the following schema to reply:\n")
                            .append(PyJson.dumps(responseFormat));
                }
            }
            case "user" -> {
                prompt.append(USER);
                Object blocks = msg.get("content_blocks");
                if (truthy(blocks)) {
                    List<String> parts = new ArrayList<>();
                    for (Object o : (List<Object>) blocks) {
                        Map<String, Object> block = (Map<String, Object>) o;
                        Object type = block.get("type");
                        if ("text".equals(type)) {
                            parts.add(str(block.containsKey("text") ? block.get("text") : ""));
                        } else if ("tool_result".equals(type)) {
                            Object tc = block.containsKey("content") ? block.get("content") : "";
                            if (tc instanceof List<?> l) {
                                List<String> texts = new ArrayList<>();
                                for (Object b : l) {
                                    Map<String, Object> bm = (Map<String, Object>) b;
                                    texts.add("text".equals(bm.get("type"))
                                            ? str(bm.containsKey("text") ? bm.get("text") : "")
                                            : "[Unsupported " + str(bm.get("type")) + "]");
                                }
                                tc = String.join("\n\n", texts);
                            }
                            parts.add("<tool_result>" + str(tc) + "</tool_result>");
                        } else {
                            parts.add("[Unsupported " + str(type) + "]");
                        }
                    }
                    prompt.append(String.join("\n\n", parts));
                } else {
                    prompt.append(str(or(content, "")));
                }
            }
            case "latest_reminder" -> prompt.append(LATEST_REMINDER).append(str(content));
            case "tool" -> throw new UnsupportedOperationException(
                    "deepseek_v41 merges tool messages into user; preprocess with mergeToolMessages()");
            case "assistant" -> {
                String thinkingPart = "";
                String tcContent = "";
                if (callList != null) {
                    List<String> invokes = new ArrayList<>();
                    for (Map<String, Object> tc : callList) {
                        invokes.add("<" + DSML + INVOKE + " name=\"" + toolNameForEncoding(tc) + "\">\n"
                                + encodeArguments(tc) + "\n</" + DSML + INVOKE + ">");
                    }
                    tcContent = "\n\n<" + DSML + CALLS + ">\n" + String.join("\n", invokes) + "\n</" + DSML + CALLS + ">";
                }
                String summary = str(or(content, ""));
                String rc = str(or(reasoning, ""));
                boolean prevHasTask = index - 1 >= 0 && messages.get(index - 1).get("task") != null;
                if ("thinking".equals(thinkingMode) && !prevHasTask) {
                    if (!dropThinking || index > lastUser) {
                        thinkingPart = rc + THINK_END;
                    }
                }
                prompt.append(thinkingPart).append(summary).append(tcContent);
                if (!woEos) {
                    prompt.append(EOS);
                }
            }
            default -> throw new UnsupportedOperationException("Unknown role: " + role);
        }

        if (index + 1 < messages.size()) {
            Object next = messages.get(index + 1).get("role");
            if (!"assistant".equals(next) && !"latest_reminder".equals(next)) {
                return prompt.toString();
            }
        }
        Object task = msg.get("task");
        if (task != null) {
            String token = TASK_TOKENS.get(task);
            if (token == null) {
                throw new IllegalArgumentException("Invalid task: '" + task + "'. Valid tasks are: "
                        + TASK_TOKENS.keySet());
            }
            if (!"action".equals(task)) {
                prompt.append(token);
            } else {
                prompt.append(ASSISTANT).append("thinking".equals(thinkingMode) ? THINK_START : THINK_END).append(token);
            }
        } else if ("user".equals(role) || ("system".equals(role) && index > 0)) {
            prompt.append(ASSISTANT);
            boolean thinking = "thinking".equals(thinkingMode);
            if (!dropThinking && thinking) {
                prompt.append(THINK_START);
            } else if (dropThinking && thinking && index >= lastUser) {
                prompt.append(THINK_START);
            } else {
                prompt.append(THINK_END);
            }
        }
        return prompt.toString();
    }

    private static String renderReasoningEffort(int index, String thinkingMode, Object effort) {
        Object e = effort == null ? "high" : effort;
        int budget;
        if (e instanceof Integer i && i >= 1 && i <= 100) {
            budget = i;
        } else if (e instanceof String s && EFFORTS.containsKey(s)) {
            budget = EFFORTS.get(s);
        } else {
            throw new IllegalArgumentException("Invalid reasoning effort for deepseek_v41: " + e
                    + ", should be int within [1,100] or " + EFFORTS.keySet());
        }
        if (index == 0 && "thinking".equals(thinkingMode)) {
            return "Reasoning Effort: " + budget + " (range 1-100, the higher the value, the more thorough the reasoning)\n\n";
        }
        return "";
    }

    // ------------------------------------------------------------------------------------------- tools

    private static String renderTools(List<Map<String, Object>> tools) {
        List<String> schemas = new ArrayList<>();
        for (var t : tools) {
            schemas.add(PyJson.dumps(t));
        }
        return TOOLS_TEMPLATE
                .replace("{schemas}", String.join("\n", schemas))
                .replace("{dsml}", DSML).replace("{calls}", CALLS).replace("{invoke}", INVOKE)
                .replace("{parameter}", PARAMETER).replace("{think_start}", THINK_START)
                .replace("{think_end}", THINK_END);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolsFromOpenAi(List<Object> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : tools) {
            Map<String, Object> tool = (Map<String, Object>) o;
            Map<String, Object> fn = new LinkedHashMap<>((Map<String, Object>) tool.get("function"));
            if (tool.get("namespace") != null) {
                fn.put("namespace", tool.get("namespace"));
            }
            fn.put("name", toolNameForEncoding(fn));
            Object namespace = fn.remove("namespace");
            if (namespace instanceof Map<?, ?> nm && truthy(nm.get("description"))) {
                fn.put("description", str(nm.get("description")) + "\n" + str(or(fn.get("description"), "")));
            }
            out.add(fn);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolCallsFromOpenAi(List<Object> calls) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : calls) {
            Map<String, Object> tc = (Map<String, Object>) o;
            Map<String, Object> fn = (Map<String, Object>) tc.get("function");
            Object ns = or(tc.get("namespace"), fn.get("namespace"));
            Object[] split = splitToolName(str(fn.get("name")), ns);
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("name", split[1]);
            call.put("arguments", fn.get("arguments"));
            if (split[0] != null) {
                call.put("namespace", split[0]);
            }
            out.add(call);
        }
        return out;
    }

    /** Splits {@code ns::name}; returns {@code {namespace, name}}. */
    private static Object[] splitToolName(String name, Object namespace) {
        int sep = name.indexOf("::");
        String bare = name;
        if (sep >= 0) {
            String prefix = name.substring(0, sep);
            if (namespace != null && !namespace.equals(prefix)) {
                throw new IllegalArgumentException("Conflicting tool namespaces: " + namespace + " != " + prefix);
            }
            namespace = prefix;
            bare = name.substring(sep + 2);
        }
        if (bare.contains("::")) {
            throw new IllegalArgumentException("Tool name must not contain '::': " + bare);
        }
        if (namespace instanceof String s && s.contains("::")) {
            throw new IllegalArgumentException("Tool namespace must not contain '::': " + s);
        }
        return new Object[]{namespace, bare};
    }

    @SuppressWarnings("unchecked")
    private static String toolNameForEncoding(Map<String, Object> tool) {
        Object ns = tool.get("namespace");
        if (ns instanceof Map<?, ?> m) {
            ns = ((Map<String, Object>) m).get("name");
        }
        Object[] split = splitToolName(str(tool.get("name")), ns);
        return split[0] == null ? (String) split[1] : split[0] + "::" + split[1];
    }

    @SuppressWarnings("unchecked")
    private static String encodeArguments(Map<String, Object> toolCall) {
        Object args = toolCall.get("arguments");
        for (int i = 0; i < 2 && args instanceof String s; i++) {
            try {
                args = JSON.readValue(s, Object.class);
            } catch (Exception e) {
                break;
            }
        }
        if (!(args instanceof Map)) {
            args = map("arguments", toolCall.get("arguments"));
        }
        List<String> parts = new ArrayList<>();
        for (var e : ((Map<String, Object>) args).entrySet()) {
            Object v = e.getValue();
            boolean isStr = v instanceof String;
            parts.add("<" + DSML + PARAMETER + " name=\"" + e.getKey() + "\" string=\"" + isStr + "\">"
                    + (isStr ? (String) v : PyJson.dumps(v)) + "</" + DSML + PARAMETER + ">");
        }
        return String.join("\n", parts);
    }

    // ------------------------------------------------------------------------------------------- preprocessing

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mergeToolMessages(List<Map<String, Object>> messages) {
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Map<String, Object> original : messages) {
            Map<String, Object> msg = (Map<String, Object>) deepCopy(original);
            Object role = msg.get("role");
            Map<String, Object> last = merged.isEmpty() ? null : merged.getLast();
            if ("tool".equals(role)) {
                Object content = msg.containsKey("content") ? msg.get("content") : "";
                if (truthy(msg.get("content_blocks")) && !(msg.get("content") instanceof String)) {
                    content = msg.get("content_blocks");
                }
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("type", "tool_result");
                block.put("tool_use_id", msg.containsKey("tool_call_id") ? msg.get("tool_call_id") : "");
                block.put("content", content);
                if (last != null && "user".equals(last.get("role")) && last.containsKey("content_blocks")) {
                    ((List<Object>) last.get("content_blocks")).add(block);
                } else {
                    List<Object> blocks = new ArrayList<>();
                    blocks.add(block);
                    merged.add(map("role", "user", "content_blocks", blocks));
                }
            } else if ("user".equals(role)) {
                Object blocks = msg.get("content_blocks");
                if (blocks == null) {
                    Object content = msg.containsKey("content") ? msg.get("content") : "";
                    if (content instanceof List<?>) {
                        blocks = content;
                    } else {
                        List<Object> single = new ArrayList<>();
                        single.add(map("type", "text", "text", content));
                        blocks = single;
                    }
                }
                if (last != null && "user".equals(last.get("role")) && last.containsKey("content_blocks")
                        && last.get("task") == null) {
                    ((List<Object>) last.get("content_blocks")).addAll((List<Object>) blocks);
                } else {
                    msg.put("content_blocks", blocks);
                    merged.add(msg);
                }
            } else {
                merged.add(msg);
            }
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private static void sortToolResultsByCallOrder(List<Map<String, Object>> messages) {
        Map<String, Integer> order = new LinkedHashMap<>();
        for (Map<String, Object> msg : messages) {
            Object role = msg.get("role");
            if ("assistant".equals(role) && truthy(msg.get("tool_calls"))) {
                order = new LinkedHashMap<>();
                int idx = 0;
                for (Object o : (List<Object>) msg.get("tool_calls")) {
                    Map<String, Object> tc = (Map<String, Object>) o;
                    Object id = tc.get("id");
                    if (!truthy(id) && tc.get("function") instanceof Map<?, ?> f) {
                        id = f.get("id");
                    }
                    if (truthy(id)) {
                        order.put(str(id), idx);
                    }
                    idx++;
                }
            } else if ("user".equals(role) && truthy(msg.get("content_blocks"))) {
                List<Object> blocks = (List<Object>) msg.get("content_blocks");
                List<Map<String, Object>> toolBlocks = new ArrayList<>();
                for (Object b : blocks) {
                    if ("tool_result".equals(((Map<String, Object>) b).get("type"))) {
                        toolBlocks.add((Map<String, Object>) b);
                    }
                }
                if (toolBlocks.size() > 1 && !order.isEmpty()) {
                    final Map<String, Integer> o = order;
                    List<Map<String, Object>> sorted = new ArrayList<>(toolBlocks);
                    sorted.sort(java.util.Comparator.comparingInt(
                            b -> o.getOrDefault(str(b.containsKey("tool_use_id") ? b.get("tool_use_id") : ""), 0)));
                    int next = 0;
                    List<Object> rebuilt = new ArrayList<>();
                    for (Object b : blocks) {
                        rebuilt.add("tool_result".equals(((Map<String, Object>) b).get("type")) ? sorted.get(next++) : b);
                    }
                    msg.put("content_blocks", rebuilt);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> processImages(List<Map<String, Object>> messages,
                                                           List<Map<String, Object>> images) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> original : messages) {
            Map<String, Object> msg = (Map<String, Object>) deepCopy(original);
            validateNoImageToken(msg);
            if (msg.get("content") instanceof List<?> && !msg.containsKey("content_blocks")) {
                msg.put("content_blocks", msg.remove("content"));
            }
            if (truthy(msg.get("content_blocks"))) {
                msg.put("content_blocks", processBlocks((List<Object>) msg.get("content_blocks"), images));
                if (!(msg.get("content") instanceof String)) {
                    List<String> texts = new ArrayList<>();
                    for (Object b : (List<Object>) msg.get("content_blocks")) {
                        if (b instanceof Map<?, ?> m && "text".equals(m.get("type"))) {
                            texts.add(str(m.containsKey("text") ? m.get("text") : ""));
                        }
                    }
                    msg.put("content", String.join("\n\n", texts));
                }
            }
            out.add(msg);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> processBlocks(List<Object> blocks, List<Map<String, Object>> images) {
        List<Object> out = new ArrayList<>();
        for (Object o : blocks) {
            if (!(o instanceof Map<?, ?>)) {
                out.add(o);
                continue;
            }
            Map<String, Object> block = (Map<String, Object>) o;
            Object type = block.get("type");
            if ("image".equals(type) || "image_url".equals(type)) {
                out.add(map("type", "text", "text", IMAGE_PLACEHOLDER));
                images.add(extractImage(block));
            } else if ("tool_result".equals(type) && block.get("content") instanceof List<?> nested) {
                Map<String, Object> copy = new LinkedHashMap<>(block);
                copy.put("content", processBlocks((List<Object>) nested, images));
                out.add(copy);
            } else if ("text".equals(type)) {
                String text = str(or(block.get("text"), ""));
                if (text.contains(IMAGE_PLACEHOLDER)) {
                    throw new IllegalArgumentException("Text block contains image placeholder '" + IMAGE_PLACEHOLDER
                            + "'. Images should be separate content blocks.");
                }
                out.add(block);
            } else {
                out.add(block);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractImage(Map<String, Object> block) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("type", "image");
        if ("image_url".equals(block.get("type"))) {
            Object iu = block.get("image_url");
            record.put("url", iu instanceof String s ? s : (iu instanceof Map<?, ?> m ? or(m.get("url"), "") : ""));
        } else {
            for (String key : new String[]{"source", "url", "data"}) {
                if (block.containsKey(key)) {
                    record.put(key, block.get(key));
                }
            }
        }
        if (!truthy(record.get("source")) && !truthy(record.get("url")) && !truthy(record.get("data"))) {
            throw new IllegalArgumentException("Image block does not contain a valid source");
        }
        return record;
    }

    private static void validateNoImageToken(Map<String, Object> msg) {
        if (msg.get("content") instanceof String s && s.contains(IMAGE_PLACEHOLDER)) {
            throw new IllegalArgumentException("Message content contains image special token '" + IMAGE_PLACEHOLDER
                    + "'. Images should be provided as image content blocks.");
        }
        if (msg.get("reasoning_content") instanceof String s && s.contains(IMAGE_PLACEHOLDER)) {
            throw new IllegalArgumentException("reasoning_content contains image special token '" + IMAGE_PLACEHOLDER + "'");
        }
    }

    // ------------------------------------------------------------------------------------------- parsing

    /**
     * Parses a completion back into a message (reference {@code parse_message_from_completion_text}); only
     * well-formed output is handled.
     *
     * @param text         the completion, ending with the EOS token.
     * @param thinkingMode {@code chat} or {@code thinking}.
     * @return the parsed message.
     * @throws IllegalArgumentException if the text is malformed.
     */
    public static Completion parseCompletion(String text, String thinkingMode) {
        String callsStart = "\n\n<" + DSML + CALLS;
        int[] index = {0};
        String[] stop = {null};
        String reasoning = "";
        if ("thinking".equals(thinkingMode)) {
            reasoning = readUntil(index, text, stop, THINK_END, callsStart);
            check(THINK_END.equals(stop[0]), "Invalid thinking format: missing </think>");
        }
        String summary = readUntil(index, text, stop, EOS, callsStart);
        boolean calling = callsStart.equals(stop[0]);
        if (!calling) {
            check(EOS.equals(stop[0]), "Invalid format: missing EOS token");
        }
        List<ParsedToolCall> calls = new ArrayList<>();
        if (calling) {
            parseToolCalls(index, text, stop, calls);
            String tail = readUntil(index, text, stop, EOS);
            check(tail.isEmpty(), "Unexpected content after tool calls");
        }
        check(index[0] == text.length() && (stop[0] == null || EOS.equals(stop[0])), "Unexpected content at end");
        for (String sp : new String[]{BOS, EOS, THINK_START, THINK_END, DSML}) {
            check(!summary.contains(sp) && !reasoning.contains(sp), "Unexpected special token '" + sp + "' in content");
        }
        return new Completion(summary, reasoning, calls);
    }

    private static final Pattern TOOL_NAME = Pattern.compile("^\\s*name=\"(.*?)\">\n$", Pattern.DOTALL);
    private static final Pattern PARAM = Pattern.compile("^ name=\"(.*?)\" string=\"(true|false)\">(.*?)<$", Pattern.DOTALL);

    private static void parseToolCalls(int[] index, String text, String[] stop, List<ParsedToolCall> out) {
        String callsEnd = "</" + DSML + CALLS + ">";
        String callStart = "<" + DSML + INVOKE;
        String callEnd = "</" + DSML + INVOKE;
        String paramStart = "<" + DSML + PARAMETER;
        String paramEnd = "/" + DSML + PARAMETER;
        while (index[0] < text.length()) {
            String lead = readUntil(index, text, stop, callStart, callsEnd);
            check(">\n".equals(lead), "Tool call format error: expected '>\\n' but got '" + lead + "'");
            if (callsEnd.equals(stop[0])) {
                break;
            }
            check(stop[0] != null, "Missing special token in tool calls");
            String nameContent = readUntil(index, text, stop, paramStart, callEnd);
            Matcher nm = TOOL_NAME.matcher(nameContent);
            check(nm.find(), "Tool name format error: '" + nameContent + "'");
            String toolName = nm.group(1);
            Map<String, String[]> args = new LinkedHashMap<>();
            while (paramStart.equals(stop[0])) {
                String param = readUntil(index, text, stop, paramEnd);
                Matcher pm = PARAM.matcher(param);
                check(pm.find(), "Parameter format error: '" + param + "'");
                check(!args.containsKey(pm.group(1)), "Duplicate parameter name: '" + pm.group(1) + "'");
                args.put(pm.group(1), new String[]{pm.group(3), pm.group(2)});
                String sep = readUntil(index, text, stop, paramStart, callEnd);
                check(">\n".equals(sep), "Parameter format error: expected '>\\n' but got '" + sep + "'");
            }
            List<String> kv = new ArrayList<>();
            for (var e : args.entrySet()) {
                String v = "true".equals(e.getValue()[1]) ? PyJson.dumps(e.getValue()[0]) : e.getValue()[0];
                kv.add(PyJson.dumps(e.getKey()) + ": " + v);
            }
            Object[] split = splitToolName(toolName, null);
            String name = split[0] == null ? (String) split[1] : split[0] + "::" + split[1];
            out.add(new ParsedToolCall(name, "{" + String.join(", ", kv) + "}"));
        }
    }

    /** Reads from {@code index[0]} to the earliest of the stop strings; records which one in {@code stop[0]}. */
    private static String readUntil(int[] index, String text, String[] stop, String... stops) {
        int min = text.length();
        String matched = null;
        for (String s : stops) {
            int pos = text.indexOf(s, index[0]);
            if (pos != -1 && pos < min) {
                min = pos;
                matched = s;
            }
        }
        String content;
        if (matched != null) {
            content = text.substring(index[0], min);
            index[0] = min + matched.length();
        } else {
            content = text.substring(index[0]);
            index[0] = text.length();
        }
        stop[0] = matched;
        return content;
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new IllegalArgumentException(message);
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static List<Map<String, Object>> concat(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        List<Map<String, Object>> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    /** Python truthiness of a JSON-like value. */
    private static boolean truthy(Object v) {
        return switch (v) {
            case null -> false;
            case Boolean b -> b;
            case Number n -> n.doubleValue() != 0;
            case CharSequence s -> !s.isEmpty();
            case Collection<?> c -> !c.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            default -> true;
        };
    }

    /** Python's {@code a or b}. */
    private static Object or(Object a, Object b) {
        return truthy(a) ? a : b;
    }

    /** Python's {@code str(value)} / {@code format} of a JSON-like value. */
    private static String str(Object v) {
        return PyJson.pyStr(v);
    }

    private static Object deepCopy(Object v) {
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (var e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return copy;
        }
        if (v instanceof List<?> l) {
            List<Object> copy = new ArrayList<>(l.size());
            for (Object o : l) {
                copy.add(deepCopy(o));
            }
            return copy;
        }
        return v;
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
