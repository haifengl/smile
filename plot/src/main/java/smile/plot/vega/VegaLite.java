/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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
package smile.plot.vega;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    /**
     * JSON object mapping.
     */
    ObjectMapper mapper = new ObjectMapper();
    /**
     * The specification
     */
    ObjectNode spec = mapper.createObjectNode();
    ;

    /**
     * Constructor with default view width and height.
     */
    public VegaLite() {
        this(400, 400);
    }

    /**
     * Constructor.
     *
     * @param width  the view width.
     * @param height the view height.
     */
    public VegaLite(int width, int height) {
        spec.put("$schema", $schema);
        ObjectNode view = mapper.createObjectNode();
        view.put("continuousWidth", width);
        view.put("continuousHeight", 400);
        ObjectNode config = mapper.createObjectNode();
        config.set("view", view);
        config(config);
    }

    @Override
    public String toString() {
        return spec.toString();
    }

    // ====== Properties of Top-Level Specifications ======

    /**
     * CSS color property to use as the background of the entire view.
     */
    public VegaLite background(String color) {
        spec.put("background", color);
        return this;
    }

    /**
     * Specifies padding for all sides.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     */
    public VegaLite padding(int size) {
        spec.put("padding", size);
        return this;
    }

    /**
     * Specifies padding for each side.
     * The visualization padding, in pixels, is from the edge of the
     * visualization canvas to the data rectangle.
     */
    public VegaLite padding(int left, int top, int right, int bottom) {
        ObjectNode padding = mapper.createObjectNode();
        padding.put("left", left);
        padding.put("top", top);
        padding.put("right", right);
        padding.put("bottom", bottom);
        spec.set("padding", padding);
        return this;
    }

    /**
     * Sets the overall size of the visualization. The total size of
     * a Vega-Lite visualization may be determined by multiple factors:
     * specified width, height, and padding values, as well as content
     * such as axes, legends, and titles.
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
     * @link https://vega.github.io/vega-lite/docs/size.html#autosize
     */
    public VegaLite autosize(String type, boolean resize, String contains) {
        ObjectNode autosize = mapper.createObjectNode();
        autosize.put("type", type);
        autosize.put("resize", resize);
        autosize.put("contains", contains);
        spec.set("autosize", autosize);
        return this;
    }

    /**
     * Sets Vega-Lite configuration object that lists configuration properties
     * of a visualization for creating a consistent theme. This property can
     * only be defined at the top-level of a specification.
     */
    public VegaLite config(ObjectNode properties) {
        spec.set("config", properties);
        return this;
    }

    /**
     * Optional metadata that will be passed to Vega. This object is completely
     * ignored by Vega and Vega-Lite and can be used for custom metadata.
     */
    public VegaLite usermeta(JsonNode data) {
        spec.set("usermeta", data);
        return this;
    }

    /**
     * Optional metadata that will be passed to Vega. This object is completely
     * ignored by Vega and Vega-Lite and can be used for custom metadata.
     */
    public VegaLite usermeta(Object data) {
        spec.set("usermeta", mapper.valueToTree(data));
        return this;
    }

    // ====== Common Properties ======

    /**
     * Sets the name of the visualization for later reference.
     */
    public VegaLite name(String name) {
        spec.put("name", name);
        return this;
    }

    /**
     * Sets the description of this mark for commenting purpose.
     */
    public VegaLite description(String description) {
        spec.put("description", description);
        return this;
    }

    /**
     * Sets a descriptive title to a chart.
     */
    public VegaLite title(String title) {
        spec.put("title", title);
        return this;
    }

    /**
     * Sets an array describing the data source. Set to null to ignore
     * the parent’s data source. If no data is set, it is derived from
     * the parent.
     */
    public <T> VegaLite data(T[] data) {
        if (data == null) {
            spec.remove("data");
        } else {
            ObjectNode node = mapper.createObjectNode();
            node.set("values", mapper.valueToTree(data));
            spec.set("data", node);
        }
        return this;
    }

    /**
     * Sets a list describing the data source. Set to null to ignore
     * the parent’s data source. If no data is set, it is derived from
     * the parent.
     */
    public <T> VegaLite data(List<T> data) {
        if (data == null) {
            spec.remove("data");
        } else {
            ObjectNode node = mapper.createObjectNode();
            node.set("values", mapper.valueToTree(data));
            spec.set("data", node);
        }
        return this;
    }

    /**
     * Sets the url of the data source. The default format type is determined
     * by the extension of the file URL. If no extension is detected, "json"
     * will be used by default.
     *
     * @param url A URL from which to load the data set.
     */
    public VegaLite data(String url) {
        return data(url, (JsonNode) null);
    }

    /**
     * Sets the url of the data source.
     *
     * @param url    A URL from which to load the data set.
     * @param format Type of input data: "json", "csv", "tsv", "dsv".
     *               Default value: The default format type is determined
     *               by the extension of the file URL. If no extension is
     *               detected, "json" will be used by default.
     */
    public VegaLite data(String url, String format) {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", format);
        return data(url, node);
    }

    /**
     * Sets the url of the data source.
     *
     * @param url    A URL from which to load the data set.
     * @param format Type of input data.
     */
    public VegaLite data(String url, JsonNode format) {
        ObjectNode node = mapper.createObjectNode();
        node.put("url", url);
        if (format != null) {
            node.set("format", format);
        }
        spec.set("data", node);
        return this;
    }

    /**
     * An array of data transformations such as filter and new field
     * calculation. Data transformations in Vega-Lite are described
     * via either view-level transforms (the transform property) or
     * field transforms inside encoding (bin, timeUnit, aggregate,
     * sort, and stack).
     * <p>
     * When both types of transforms are specified, the view-level
     * transforms are executed first based on the order in the
     * array. Then the inline transforms are executed in this order:
     * bin, timeUnit, aggregate, sort, and stack.
     */
    public VegaLite transform(ObjectNode... transforms) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode node : transforms) {
            array.add(node);
        }
        spec.set("transform", array);
        return this;
    }

    /**
     * Returns the HTML of plot specification with Vega Embed.
     */
    private String embed() throws JsonProcessingException {
        return """
                   <!DOCTYPE html>
                   <html>
                   <head>
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
                """.format(mapper.writeValueAsString(spec));
    }

    /**
     * Returns the HTML wrapped in an iframe to render in notebooks.
     */
    public String iframe() throws JsonProcessingException {
        return iframe(UUID.randomUUID().toString());
    }

    /**
     * Returns the HTML wrapped in an iframe to render in notebooks.
     *
     * @param id the iframe HTML id.
     */
    public String iframe(String id) throws JsonProcessingException {
        String src = Strings.htmlEscape(embed());
        return """
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
                   </script>
                """.format(id, src, id);
    }

    /**
     * The schema of Vega-Lite.
     */
    private String $schema = "https://vega.github.io/schema/vega-lite/v5.json";
    /**
     * The MIME type of Vega-Lite.
     */
    private String mime = "application/vnd.vegalite.v5+json";

    /**
     * Returns a single view specification with inline data.
     */
    public static <T> View view(T[] data) {
        View view = new View();
        view.data(data);
        return view;
    }

    /**
     * Returns a single view specification with inline data.
     */
    public static <T> View view(List<T> data) {
        View view = new View();
        view.data(data);
        return view;
    }

    /**
     * Returns a single view specification with data from a URL.
     */
    public static View view(String url) {
        return view(url, null);
    }

    /**
     * Returns a single view specification with data from a URL.
     */
    public static View view(String url, String format) {
        View view = new View();
        view.data(url, format);
        return view;
    }

    /**
     * Returns a facet specification with inline data.
     */
    public static <T> Facet facet(T[] data) {
        Facet facet = new Facet();
        facet.data(data);
        return facet;
    }

    /**
     * Returns a facet specification with inline data.
     */
    public static <T> Facet facet(List<T> data) {
        Facet facet = new Facet();
        facet.data(data);
        return facet;
    }

    /**
     * Returns a facet specification with data from a URL.
     */
    public static Facet facet(String url) {
        return facet(url, null);
    }

    /**
     * Returns a facet specification with data from a URL.
     */
    public static Facet facet(String url, String format) {
        Facet facet = new Facet();
        facet.data(url, format);
        return facet;
    }

    /**
     * Returns a layered view specification.
     */
    public static Layer layer(View... views) {
        return new Layer(views);
    }

    /**
     * Returns a layered view specification.
     */
    public static <T> Layer layer(T[] data, View... views) {
        Layer layer = new Layer(views);
        layer.data(data);
        return layer;
    }

    /**
     * Returns a layered view specification.
     */
    public static <T> Layer layer(List<T> data, View... views) {
        Layer layer = new Layer(views);
        layer.data(data);
        return layer;
    }

    /**
     * Returns a layered view specification.
     */
    public static Layer layer(String url, View... views) {
        Layer layer = new Layer(views);
        layer.data(url);
        return layer;
    }

    /**
     * Returns a layered view specification.
     */
    public static Layer layer(String url, String format, View... views) {
        Layer layer = new Layer(views);
        layer.data(url, format);
        return layer;
    }

    /**
     * Horizontal concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition hconcat(VegaLite... views) {
        return new ViewLayoutComposition("hconcat", views);
    }

    /**
     * Horizontal concatenation. Put multiple views into a column.
     */
    public static <T> ViewLayoutComposition hconcat(T[] data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("hconcat", views);
        composition.data(data);
        return composition;
    }

    /**
     * Horizontal concatenation. Put multiple views into a column.
     */
    public static <T> ViewLayoutComposition hconcat(List<T> data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("hconcat", views);
        composition.data(data);
        return composition;
    }

    /**
     * Horizontal concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition hconcat(String url, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("hconcat", views);
        composition.data(url);
        return composition;
    }

    /**
     * Horizontal concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition hconcat(String url, String format, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("hconcat", views);
        composition.data(url, format);
        return composition;
    }

    /**
     * Vertical concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition vconcat(VegaLite... views) {
        return new ViewLayoutComposition("vconcat", views);
    }

    /**
     * Vertical concatenation. Put multiple views into a column.
     */
    public static <T> ViewLayoutComposition vconcat(T[] data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("vconcat", views);
        composition.data(data);
        return composition;
    }

    /**
     * Vertical concatenation. Put multiple views into a column.
     */
    public static <T> ViewLayoutComposition vconcat(List<T> data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("vconcat", views);
        composition.data(data);
        return composition;
    }

    /**
     * Vertical concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition vconcat(String url, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("vconcat", views);
        composition.data(url);
        return composition;
    }

    /**
     * Vertical concatenation. Put multiple views into a column.
     */
    public static ViewLayoutComposition vconcat(String url, String format, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("vconcat", views);
        composition.data(url, format);
        return composition;
    }

    /**
     * General (wrappable) concatenation. Put multiple views into a flexible flow layout.
     */
    public static ViewLayoutComposition concat(int columns, VegaLite... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("concat", views);
        composition.spec.put("columns", columns);
        return composition;
    }

    /**
     * General (wrappable) concatenation. Put multiple views into a flexible flow layout.
     */
    public static <T> ViewLayoutComposition concat(int columns, T[] data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("concat", views);
        composition.spec.put("columns", columns);
        composition.data(data);
        return composition;
    }

    /**
     * General (wrappable) concatenation. Put multiple views into a flexible flow layout.
     */
    public static <T> ViewLayoutComposition concat(int columns, List<T> data, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("concat", views);
        composition.spec.put("columns", columns);
        composition.data(data);
        return composition;
    }

    /**
     * General (wrappable) concatenation. Put multiple views into a flexible flow layout.
     */
    public static ViewLayoutComposition concat(int columns, String url, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("concat", views);
        composition.spec.put("columns", columns);
        composition.data(url);
        return composition;
    }

    /**
     * General (wrappable) concatenation. Put multiple views into a flexible flow layout.
     */
    public static ViewLayoutComposition concat(int columns, String url, String format, View... views) {
        ViewLayoutComposition composition = new ViewLayoutComposition("concat", views);
        composition.spec.put("columns", columns);
        composition.data(url, format);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param fields The fields that should be used for each entry.
     */
    public static <T> ViewLayoutComposition repeat(T[] data, VegaLite view, String... fields) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(data);
        composition.repeat(fields);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param fields The fields that should be used for each entry.
     */
    public static <T> ViewLayoutComposition repeat(List<T> data, VegaLite view, String... fields) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(data);
        composition.repeat(fields);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param fields The fields that should be used for each entry.
     */
    public static <T> ViewLayoutComposition repeat(String url, VegaLite view, String... fields) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(url);
        composition.repeat(fields);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param fields The fields that should be used for each entry.
     */
    public static <T> ViewLayoutComposition repeat(String url, String format, VegaLite view, String... fields) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(url, format);
        composition.repeat(fields);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public static <T> ViewLayoutComposition repeat(T[] data, VegaLite view, String[] row, String[] column) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(data);
        composition.repeat(row, column);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public static <T> ViewLayoutComposition repeat(List<T> data, VegaLite view, String[] row, String[] column) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(data);
        composition.repeat(row, column);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public static <T> ViewLayoutComposition repeat(String url, VegaLite view, String[] row, String[] column) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(url);
        composition.repeat(row, column);
        return composition;
    }

    /**
     * Creates a view for each entry in an array of fields. This operator
     * generates multiple plots like facet. However, unlike facet it allows
     * full replication of a data set in each view.
     *
     * @param row    An array of fields to be repeated vertically.
     * @param column An array of fields to be repeated horizontally.
     */
    public static <T> ViewLayoutComposition repeat(String url, String format, VegaLite view, String[] row, String[] column) {
        ViewLayoutComposition composition = new ViewLayoutComposition(view);
        composition.data(url, format);
        composition.repeat(row, column);
        return composition;
    }
}