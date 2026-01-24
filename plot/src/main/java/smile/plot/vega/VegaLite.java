/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.plot.vega;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import smile.util.Strings;

/**
 * Vega-Lite specifications are JSON objects that describe a diverse range
 * of interactive visualizations. Besides using a single view specification
 * as a standalone visualization, Vega-Lite also provides operators for
 * composing multiple view specifications into a layered or multi-view
 * specification. These operators include layer, facet, concat, and repeat.
 *
 * @author Haifeng Li
 */
public class VegaLite {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(VegaLite.class);
    /**
     * The schema of Vega-Lite.
     */
    private static final String schema = "https://vega.github.io/schema/vega-lite/v5.json";
    /**
     * The MIME type of Vega-Lite.
     */
    private static final String mime = "application/vnd.vegalite.v5+json";
    /**
     * ISO 8601 format.
     */
    static final DateTimeFormatter ISO8601 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    /**
     * JSON object mapping.
     */
    static final ObjectMapper mapper = new ObjectMapper();
    /**
     * The Vega-Lite specification.
     */
    final ObjectNode spec = mapper.createObjectNode().put("$schema", schema);
    /**
     * The configuration object lists configuration properties
     * of a visualization for creating a consistent theme.
     */
    final ObjectNode config = spec.putObject("config");
    /**
     * Default properties for single view plots.
     */
    final ObjectNode view = config.putObject("view");

    /**
     * Constructor.
     */
    public VegaLite() {
        view.put("continuousWidth", 400);
        view.put("continuousHeight", 400);
    }

    @Override
    public String toString() {
        return spec.toString();
    }

    /**
     * Returns the specification in pretty print.
     * @return the specification in pretty print.
     */
    public String toPrettyString() {
        return spec.toPrettyString();
    }

    /**
     * Returns the Vega-Lite specification.
     * @return the Vega-Lite specification.
     */
    public ObjectNode spec() {
        return spec;
    }

    // ====== Properties of Top-Level Specifications ======
    /**
     * Returns the configuration object that lists properties of
     * a visualization for creating a consistent theme. This property
     * can only be defined at the top-level of a specification.
     * @return the configuration object.
     */
    public Config config() {
        return new Config(config);
    }

    /**
     * Returns the configuration object defining the style of
     * a single view visualization.
     * @return the view configuration object.
     */
    public ViewConfig viewConfig() {
        return new ViewConfig(view);
    }

    /**
     * Optional metadata that will be passed to Vega. This object is completely
     * ignored by Vega and Vega-Lite and can be used for custom metadata.
     * @param metadata the metadata.
     * @return this object.
     */
    public VegaLite usermeta(JsonNode metadata) {
        spec.set("usermeta", metadata);
        return this;
    }

    /**
     * Optional metadata that will be passed to Vega. This object is completely
     * ignored by Vega and Vega-Lite and can be used for custom metadata.
     * @param metadata the metadata.
     * @return this object.
     */
    public VegaLite usermeta(Object metadata) {
        spec.putPOJO("usermeta", metadata);
        return this;
    }

    /**
     * Sets the background of the entire view with CSS color property.
     * @param color the background color.
     * @return this object.
     */
    public VegaLite background(String color) {
        spec.put("background", color);
        return this;
    }

    /**
     * Specifies padding for all sides.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     * @param size the padding size.
     * @return this object.
     */
    public VegaLite padding(int size) {
        spec.put("padding", size);
        return this;
    }

    /**
     * Specifies padding for each side.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     * @param left the left padding.
     * @param top the top padding.
     * @param right the right padding.
     * @param bottom the bottom padding.
     * @return this object.
     */
    public VegaLite padding(int left, int top, int right, int bottom) {
        ObjectNode padding = spec.putObject("padding");
        padding.put("left", left);
        padding.put("top", top);
        padding.put("right", right);
        padding.put("bottom", bottom);
        return this;
    }

    /**
     * Sets the overall size of the visualization. The total size of
     * a Vega-Lite visualization may be determined by multiple factors:
     * specified width, height, and padding values, as well as content
     * such as axes, legends, and titles.
     * @return this object.
     */
    public VegaLite autosize() {
        return autosize("pad", false, "content");
    }

    /**
     * Sets the overall size of the visualization. The total size of
     * a Vega-Lite visualization may be determined by multiple factors:
     * specified width, height, and padding values, as well as content
     * such as axes, legends, and titles.
     *
     * @param type     The sizing format type. One of "pad", "fit", "fit-x",
     *                 "fit-y", or "none". See Vega-Lite documentation for
     *                 descriptions of each.
     * @param resize   A boolean flag indicating if autosize layout should
     *                 be re-calculated on every view update.
     * @param contains Determines how size calculation should be performed,
     *                 one of "content" or "padding". The default setting
     *                 ("content") interprets the width and height settings
     *                 as the data rectangle (plotting) dimensions, to which
     *                 padding is then added. In contrast, the "padding"
     *                 setting includes the padding within the view size
     *                 calculations, such that the width and height settings
     *                 indicate the total intended size of the view.
     * @see <a href="https://vega.github.io/vega-lite/docs/size.html#autosize">autosize</a>
     * @return this object.
     */
    public VegaLite autosize(String type, boolean resize, String contains) {
        ObjectNode autosize = spec.putObject("autosize");
        autosize.put("type", type);
        autosize.put("resize", resize);
        autosize.put("contains", contains);
        return this;
    }

    // ====== Common Properties ======

    /**
     * Sets the name of the visualization for later reference.
     * @param name the name of the visualization.
     * @return this object.
     */
    public VegaLite name(String name) {
        spec.put("name", name);
        return this;
    }

    /**
     * Sets the description of this mark for commenting purpose.
     * @param description description of this mark.
     * @return this object.
     */
    public VegaLite description(String description) {
        spec.put("description", description);
        return this;
    }

    /**
     * Sets a descriptive title to a chart.
     * @param title a descriptive title.
     * @return this object.
     */
    public VegaLite title(String title) {
        spec.put("title", title);
        return this;
    }

    /**
     * Returns the data specification object.
     * @return the data specification object.
     */
    public Data data() {
        Data data = new Data();
        spec.set("data", data.spec);
        return data;
    }

    /**
     * Returns the data transformation object.
     * such as filter and new field
     * calculation. Data transformations in Vega-Lite are described
     * via either view-level transforms (the transform property) or
     * field transforms inside encoding (bin, timeUnit, aggregate,
     * sort, and stack).
     * <p>
     * When both types of transforms are specified, the view-level
     * transforms are executed first based on the order in the
     * array. Then the inline transforms are executed in this order:
     * bin, timeUnit, aggregate, sort, and stack.
     * @return the data transformation object.
     */
    public Transform transform() {
        ArrayNode node = spec.has("transform") ? (ArrayNode) spec.get("transform") : spec.putArray("transform");
        return new Transform(node);
    }

    /**
     * Displays the plot with the default browser.
     * @throws IOException if fails to create html file of plot.
     * @throws HeadlessException if the VM runs in headless mode.
     */
    public void show() throws IOException, HeadlessException {
        show(false);
    }

    /**
     * Displays the plot with the default browser.
     * @param silent If true, silently swallow any exception.
     * @throws IOException if fails to create html file of plot.
     * @throws HeadlessException if the VM runs in headless mode.
     */
    public void show(boolean silent) throws IOException, HeadlessException {
        try {
            Path path = Files.createTempFile("smile-plot-", ".html");
            path.toFile().deleteOnExit();
            Files.writeString(path, html());
            Desktop.getDesktop().browse(path.toUri());
        } catch (Exception ex) {
            if (silent) {
                logger.warn("Failed to show {}", this.getClass().getSimpleName(), ex);
            } else {
                throw ex;
            }
        }
    }

    /**
     * Returns the HTML of plot specification with Vega Embed.
     * @return the HTML of plot specification with Vega Embed.
     */
    public String html() {
        return html("en");
    }

    /**
     * Returns the HTML of plot specification with Vega Embed.
     * @param lang the primary language of document.
     * @return the HTML of plot specification with Vega Embed.
     */
    public String html(String lang) {
        String title = spec.has("title") ? spec.get("title").asString() : "Smile Plot";
        return String.format("""
                   <!DOCTYPE html>
                   <html lang=%s>
                   <head>
                     <title>%s</title>
                     <meta name="viewport" content="width=device-width, initial-scale=1">
                     <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega@5"></script>
                     <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega-lite@5"></script>
                     <script type="text/javascript" src="https://cdn.jsdelivr.net/npm/vega-embed@6"></script>
                   </head>
                   <body>
                
                   <div id="vega-lite"></div>
                
                   <script type="text/javascript">
                     var spec = %s;
                     var opt = {
                       "mode": "vega-lite",
                       "renderer": "canvas",
                       "actions": {"editor": true, "source": true, "export": true}
                     };
                     vegaEmbed('#vega-lite', spec, opt).catch(console.error);
                   </script>
                   </body>
                   </html>
                """, lang, title, spec.toPrettyString());
    }

    /**
     * Returns the HTML wrapped in an iframe to render in notebooks.
     * @return the HTML wrapped in an iframe to render in notebooks.
     */
    public String iframe() {
        return iframe(UUID.randomUUID().toString());
    }

    /**
     * Returns the HTML wrapped in an iframe to render in notebooks.
     *
     * @param id the iframe HTML id.
     * @return the HTML wrapped in an iframe to render in notebooks.
     */
    public String iframe(String id) {
        String src = Strings.htmlEscape(html());
        String html = """
            <iframe id="%s" sandbox="allow-scripts allow-same-origin" style="border: none; width: 100%" srcdoc="%s"></iframe>
            <script>
              (function() {
                function resizeIFrame(el, k) {
                  var height = el.contentWindow.document.body.scrollHeight || '600'; // Fallback in case of no scroll height
                  el.style.height = height + 'px';
                  if (k <= 10) { setTimeout(function() { resizeIFrame(el, k+1) }, 100 + (k * 250)) };
                }
                resizeIFrame(document.getElementById("%s"), 1);
              })(); // IIFE
            </script>""";
        return String.format(html, id, src, id);
    }

    /**
     * Returns the encoding object.
     * @return the encoding object. */
    ObjectNode encoding() {
        return spec.has("encoding") ? (ObjectNode) spec.get("encoding") : spec.putObject("encoding");
    }
}