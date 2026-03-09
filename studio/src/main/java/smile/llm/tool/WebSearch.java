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

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import serpapi.SerpApi;
import smile.llm.Conversation;

@JsonClassDescription("""
- Allows AI to search the web and use the results to inform responses
- Provides up-to-date information for current events and recent data
- Returns search result information formatted as search result blocks
- Use this tool for accessing information beyond AI's knowledge cutoff
- Searches are performed automatically within a single API call

Usage notes:
  - Web search is only available in the US
  - Account for "Today's date" in <env>. For example, if <env> says "Today's date: 2026-07-01", and the user wants the latest docs, use 2026 in the search query.
""")
public class WebSearch implements Tool {
    @JsonProperty(required = true)
    @JsonPropertyDescription("The search query to use")
    public String query;

    @Override
    public String run(Conversation conversation, Consumer<String> statusUpdate) {
        statusUpdate.accept("Searching web with keywords " + query);
        return webSearch(query);
    }

    /** Static helper method to search the web. */
    public static String webSearch(String query) {
        String apiKey = System.getenv("SERPAPI_KEY");
        if (apiKey == null) {
            return "Error: SERPAPI_API_KEY environment variable not set. Web search is not available. You may get a free API Key at https://serpapi.com/dashboard";
        }

        try {
            Map<String, String> parameter = Map.of("q", query, "api_key", apiKey);
            var data = serpapi.search(parameter);
            var results = data.getAsJsonArray("organic_results");

            if (results == null) {
                var metadata = data.getAsJsonObject("search_metadata");
                String status = metadata.getAsJsonPrimitive("status").getAsString();
                return "No results found for query: " + status;
            } else {
                StringBuilder sb = new StringBuilder("# Search Results\n");
                for (int i = 0; i < results.size(); i++) {
                    var item = results.get(i).getAsJsonObject();
                    String title = item.getAsJsonPrimitive("title").getAsString();
                    String link = item.getAsJsonPrimitive("link").getAsString();
                    String snippet = item.getAsJsonPrimitive("snippet").getAsString();

                    sb.append("\n## ").append(title).append("\n");
                    sb.append("Link: ").append(link).append("\n\n");
                    sb.append(snippet).append("\n");
                }
                return sb.toString();
            }
        } catch (Throwable t) {
            return "Error searching web: " + t.getMessage();
        }
    }

    /** SerpApi scrapes Google and other search engines. */
    private static final SerpApi serpapi = new SerpApi();

    /**
     * The specification for WebSearch tool.
     * @return the tool specification.
     */
    public static Tool.Spec spec() {
        try {
            return new Tool.Spec(WebSearch.class,
                    List.of(WebSearch.class.getMethod("webSearch", String.class)));
        } catch (Exception e) {
            System.err.println("Failed to load ToolSpec: " + e.getMessage());
        }
        return new Tool.Spec(WebSearch.class, List.of());
    }
}
