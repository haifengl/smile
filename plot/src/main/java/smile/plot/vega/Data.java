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
package smile.plot.vega;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.node.ObjectNode;

/**
 * The basic data model used by Vega-Lite is tabular data. Individual data sets
 * are assumed to contain a collection of records, which may contain any number
 * of named data fields.
 * <p>
 * A dataset may be either inline data (values) or a URL from which to load the
 * data (url). Or we can create an empty, named data source (name), which can
 * be bound at runtime or populated from top-level datasets.
 *
 * @author Haifeng Li
 */
public class Data {
    /** Data specification object. */
    final ObjectNode spec;

    /**
     * Hides the constructor so that users cannot create the instances directly.
     */
    Data() {
        spec = VegaLite.mapper.createObjectNode();
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
     * Sets a placeholder name and bind data at runtime.
     * @param name the dataset name.
     * @return this object.
     */
    public Data name(String name) {
        spec.put("name", name);
        return this;
    }

    /**
     * Sets the url of the data source.
     *
     * @param url A URL from which to load the data set.
     * @return this object.
     */
    public Data url(String url) {
        spec.put("url", url);
        return this;
    }

    /**
     * Sets the format for parsing the data.
     *
     * @param format "json", "csv", "tsv", or "dsv".
     * @return this object.
     */
    public Data format(String format) {
        spec.putObject("format").put("type", format);
        return this;
    }

    /**
     * Sets the format for parsing the data.
     *
     * @param format "json", "csv", "tsv", or "dsv".
     * @param type Explicit data type. Each key corresponds to a field name,
     *            and the value to the desired data type (one of "number",
     *            "boolean", "date", or null (do not parse the field)).
     *            If set to null, disable type inference based on the spec
     *            and only use type inference based on the data.
     * @return this object.
     */
    public Data format(String format, Map<String, String> type) {
        ObjectNode node = spec.putObject("format");
        node.put("type", format);

        if (type == null) {
            node.putNull("parse");
        } else {
            ObjectNode parse = node.putObject("parse");
            for (Map.Entry<String, String> entry : type.entrySet()) {
                parse.put(entry.getKey(), entry.getValue());
            }
        }

        return this;
    }

    /**
     * Sets an array describing the data source. Set to null to ignore
     * the parent's data source. If no data is set, it is derived from
     * the parent.
     * @param json JSON content to parse.
     * @return this object.
     */
    public Data values(String json) {
        spec.set("values", VegaLite.mapper.valueToTree(json));
        return this;
    }

    /**
     * Sets an array describing the data source. Set to null to ignore
     * the parent's data source. If no data is set, it is derived from
     * the parent.
     * @param data an array of data.
     * @param <T> the data type.
     * @return this object.
     */
    public <T> Data values(T[] data) {
        spec.set("values", VegaLite.mapper.valueToTree(data));
        return this;
    }

    /**
     * Sets a list describing the data source. Set to null to ignore
     * the parent's data source. If no data is set, it is derived from
     * the parent.
     * @param data a list of data.
     * @param <T> the data type.
     * @return this object.
     */
    public <T> Data values(List<T> data) {
        spec.set("values", VegaLite.mapper.valueToTree(data));
        return this;
    }

    /**
     * Loads a JSON file. Assumes row-oriented data, where each row is an
     * object with named attributes.
     *
     * @param url    A URL from which to load the data set.
     * @param property The JSON property containing the desired data. This
     *                parameter can be used when the loaded JSON file may
     *                have surrounding structure or meta-data. For example
     *                "values.features" is equivalent to retrieving
     *                json.values.features from the loaded JSON object.
     * @return this object.
     */
    public Data json(String url, String property) {
        spec.put("url", url);
        spec.putObject("format")
                .put("type", "json")
                .put("property", property);
        return this;
    }

    /**
     * Loads a JSON file using the TopoJSON format. The input file must
     * contain valid TopoJSON data. The TopoJSON input is then converted
     * into a GeoJSON format. There are two mutually exclusive properties
     * that can be used to specify the conversion process: "feature" or
     * "mesh".
     *
     * @param url    A URL from which to load the data set.
     * @param conversion "feature" or "mesh".
     * @param name The name of the TopoJSON object set to convert to a
     *            GeoJSON feature collection (or mesh).
     * @return this object.
     */
    public Data topojson(String url, String conversion, String name) {
        spec.put("url", url);
        spec.putObject("format")
                .put("type", "topojson")
                .put(conversion, name);
        return this;
    }

    /**
     * Loads a comma-separated values (CSV) file
     *
     * @param url  A URL from which to load the data set.
     * @param type Explicit data type. Each key corresponds to a field name,
     *            and the value to the desired data type (one of "number",
     *            "boolean", "date", or null (do not parse the field)).
     *            If set to null, disable type inference based on the spec
     *            and only use type inference based on the data.
     * @return this object.
     */
    public Data csv(String url, Map<String, String> type) {
        return this.url(url).format("csv", type);
    }

    /**
     * Loads a tab-separated values (TSV) file
     *
     * @param url  A URL from which to load the data set.
     * @param type Explicit data type. Each key corresponds to a field name,
     *            and the value to the desired data type (one of "number",
     *            "boolean", "date", or null (do not parse the field)).
     *            If set to null, disable type inference based on the spec
     *            and only use type inference based on the data.
     * @return this object.
     */
    public Data tsv(String url, Map<String, String> type) {
        return this.url(url).format("tsv", type);
    }

    /**
     * Loads a delimited text file with a custom delimiter.
     * This is a general version of CSV and TSV.
     *
     * @param url    A URL from which to load the data set.
     * @param delimiter The delimiter between records. The delimiter must be
     *                 a single character (i.e., a single 16-bit code unit);
     *                 so, ASCII delimiters are fine, but emoji delimiters
     *                 are not.
     * @return this object.
     */
    public Data dsv(String url, String delimiter) {
        this.url(url).format("dsv");
        ((ObjectNode) spec.get("format")).put("delimiter", delimiter);
        return this;
    }

    /**
     * Loads a delimited text file with a custom delimiter.
     * This is a general version of CSV and TSV.
     *
     * @param url    A URL from which to load the data set.
     * @param delimiter The delimiter between records. The delimiter must be
     *                 a single character (i.e., a single 16-bit code unit);
     *                 so, ASCII delimiters are fine, but emoji delimiters
     *                 are not.
     * @param type Explicit data type. Each key corresponds to a field name,
     *            and the value to the desired data type (one of "number",
     *            "boolean", "date", or null (do not parse the field)).
     *            If set to null, disable type inference based on the spec
     *            and only use type inference based on the data.
     * @return this object.
     */
    public Data dsv(String url, String delimiter, Map<String, String> type) {
        this.url(url).format("dsv", type);
        ((ObjectNode) spec.get("format")).put("delimiter", delimiter);
        return this;
    }
}
