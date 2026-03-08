/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.tool;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.furstenheim.CopyDown;
import org.jsoup.Jsoup;
import smile.llm.Conversation;

@JsonClassDescription("""
- Fetches content from a specified URL
- Takes a URL and a prompt as input
- Fetches the URL content, converts HTML to markdown
- Returns the content in markdown
- Use this tool when you need to retrieve and analyze web content

Usage notes:
  - IMPORTANT: If an MCP-provided web fetch tool is available, prefer using that tool instead of this one, as it may have fewer restrictions. All MCP-provided tools start with "mcp__".
  - The URL must be a fully-formed valid URL
  - HTTP URLs will be automatically upgraded to HTTPS
  - This tool is read-only and does not modify any files
  - Results may be truncated if the content is very large
  - Includes a self-cleaning 15-minute cache for faster responses when repeatedly accessing the same URL
  - When a URL redirects to a different host, the tool will inform you and provide the redirect URL in a special format. You should then make a new WebFetch request with the redirect URL to fetch the content.
""")
public class WebFetch implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The URL to fetch content from")
    public String url;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        statusUpdate.accept("Fetching " + url);
        return webFetch(url);
    }

    /** Static helper method to fetch a webpage. */
    public static String webFetch(String url) {
        // Upgrade HTTP to HTTPS
        if (url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        }

        // Check cache
        String cached = CACHE.get(url);
        if (cached != null) {
            return cached;
        }

        try {
            var body = Jsoup.connect(url).execute().body();
            var markdown = copydown.convert(body);
            // Trim to a reasonable size (~50k chars)
            if (markdown.length() > 50_000) {
                markdown = markdown.substring(0, 50_000) + "\n\n[Content truncated due to size]";
            }

            CACHE.put(url, markdown);
            return markdown;
        } catch (Throwable t) {
            return "Error fetching URL: " + t.getMessage();
        }
    }

    /** 15-minute TTL cache for fetched URLs. */
    private static final Map<String, String> CACHE = new LinkedHashMap<>() {
        private static final int MAX_SIZE = 100;
        private static final Duration TTL = Duration.ofMinutes(15);
        private final Map<String, Instant> timestamps = new LinkedHashMap<>();

        @Override
        public String put(String key, String value) {
            evict();
            timestamps.put(key, Instant.now());
            return super.put(key, value);
        }

        @Override
        public String get(Object key) {
            evict();
            Instant ts = timestamps.get(key);
            if (ts == null || Duration.between(ts, Instant.now()).compareTo(TTL) > 0) {
                super.remove(key);
                if (key instanceof String k) timestamps.remove(k);
                return null;
            }
            return super.get(key);
        }

        private void evict() {
            Instant now = Instant.now();
            timestamps.entrySet().removeIf(e -> {
                if (Duration.between(e.getValue(), now).compareTo(TTL) > 0) {
                    super.remove(e.getKey());
                    return true;
                }
                return false;
            });
            while (size() > MAX_SIZE) {
                String oldest = keySet().iterator().next();
                super.remove(oldest);
                timestamps.remove(oldest);
            }
        }
    };

    /** HTML to Markdown converter. */
    private static final CopyDown copydown = new CopyDown();

    /**
     * The specification for WebFetch tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(WebFetch.class,
                    List.of(WebFetch.class.getMethod("webFetch", String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(WebFetch.class, List.of());
    }
}
