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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
    public String run(Conversation conversation) {
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
            URI uri = URI.create(url);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            // Detect cross-host redirects
            URI finalUri = response.uri();
            if (finalUri != null && !finalUri.getHost().equalsIgnoreCase(uri.getHost())) {
                return "The URL redirected to a different host: " + finalUri +
                        "\nPlease make a new WebFetch request with the redirect URL: " + finalUri;
            }

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                return "Error fetching URL: HTTP " + status;
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String body = response.body();
            String markdown;
            if (contentType.contains("text/html")) {
                markdown = htmlToMarkdown(body);
            } else {
                markdown = body;
            }

            // Trim to a reasonable size (~50k chars)
            if (markdown.length() > 50_000) {
                markdown = markdown.substring(0, 50_000) + "\n\n[Content truncated due to size]";
            }

            CACHE.put(url, markdown);
            return markdown;
        } catch (Exception e) {
            return "Error fetching URL: " + e.getMessage();
        }
    }

    /** Converts HTML content to Markdown-like plain text. */
    static String htmlToMarkdown(String html) {
        // Remove <head> section
        html = PATTERN_HEAD.matcher(html).replaceAll("");
        // Remove script and style blocks
        html = PATTERN_SCRIPT.matcher(html).replaceAll("");
        html = PATTERN_STYLE.matcher(html).replaceAll("");
        // Convert headings
        html = PATTERN_H1.matcher(html).replaceAll("\n# $1\n");
        html = PATTERN_H2.matcher(html).replaceAll("\n## $1\n");
        html = PATTERN_H3.matcher(html).replaceAll("\n### $1\n");
        html = PATTERN_H4.matcher(html).replaceAll("\n#### $1\n");
        html = PATTERN_H5.matcher(html).replaceAll("\n##### $1\n");
        html = PATTERN_H6.matcher(html).replaceAll("\n###### $1\n");
        // Convert links
        html = PATTERN_LINK.matcher(html).replaceAll("[$2]($1)");
        // Convert bold/italic
        html = PATTERN_BOLD.matcher(html).replaceAll("**$1**");
        html = PATTERN_ITALIC.matcher(html).replaceAll("*$1*");
        // Convert code
        html = PATTERN_CODE.matcher(html).replaceAll("`$1`");
        html = PATTERN_PRE.matcher(html).replaceAll("\n```\n$1\n```\n");
        // Convert list items
        html = PATTERN_LI.matcher(html).replaceAll("\n- $1");
        // Convert paragraphs and line breaks
        html = PATTERN_P.matcher(html).replaceAll("\n\n");
        html = PATTERN_BR.matcher(html).replaceAll("\n");
        html = PATTERN_HR.matcher(html).replaceAll("\n---\n");
        // Remove all remaining tags
        html = PATTERN_TAG.matcher(html).replaceAll("");
        // Decode common HTML entities
        html = html.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'")
                   .replace("&nbsp;", " ")
                   .replace("&#39;", "'")
                   .replace("&mdash;", "—")
                   .replace("&ndash;", "–")
                   .replace("&hellip;", "…");
        // Collapse multiple blank lines
        html = PATTERN_BLANK_LINES.matcher(html).replaceAll("\n\n");
        return html.strip();
    }

    // ---- Constants ----

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

    /** Shared HTTP client that does NOT follow redirects automatically. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    // Regex patterns for HTML-to-Markdown conversion (CASE_INSENSITIVE | DOTALL)
    private static final Pattern PATTERN_HEAD    = Pattern.compile("<head[^>]*>.*?</head>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_SCRIPT  = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_STYLE   = Pattern.compile("<style[^>]*>.*?</style>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H1      = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H2      = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H3      = Pattern.compile("<h3[^>]*>(.*?)</h3>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H4      = Pattern.compile("<h4[^>]*>(.*?)</h4>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H5      = Pattern.compile("<h5[^>]*>(.*?)</h5>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_H6      = Pattern.compile("<h6[^>]*>(.*?)</h6>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_LINK    = Pattern.compile("<a[^>]*\\shref=[\"']([^\"']*)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_BOLD    = Pattern.compile("<(?:b|strong)[^>]*>(.*?)</(?:b|strong)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_ITALIC  = Pattern.compile("<(?:i|em)[^>]*>(.*?)</(?:i|em)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_CODE    = Pattern.compile("<code[^>]*>(.*?)</code>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_PRE     = Pattern.compile("<pre[^>]*>(.*?)</pre>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_LI      = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PATTERN_P       = Pattern.compile("</?p[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_BR      = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_HR      = Pattern.compile("<hr\\s*/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_TAG     = Pattern.compile("<[^>]+>", Pattern.DOTALL);
    private static final Pattern PATTERN_BLANK_LINES = Pattern.compile("\n{3,}");

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
