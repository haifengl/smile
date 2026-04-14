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

import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@code smile.plot.vega} that assert the generated
 * Vega-Lite JSON specification is structurally correct.
 * <p>
 * Every test works purely with the in-memory JSON tree — no network
 * requests and no browser are required.
 */
public class VegaLiteSpecTest {

    // ── VegaLite top-level properties ─────────────────────────────────────────

    @Test
    public void testSchemaIsPresent() {
        VegaLite vl = new View();
        assertTrue(vl.spec().has("$schema"), "Spec must contain $schema");
        assertTrue(vl.spec().get("$schema").asString().contains("vega-lite"),
                "$schema must reference vega-lite");
    }

    @Test
    public void testDefaultConfigViewDimensions() {
        VegaLite vl = new View();
        JsonNode view = vl.spec().path("config").path("view");
        assertEquals(400, view.path("continuousWidth").asInt());
        assertEquals(400, view.path("continuousHeight").asInt());
    }

    @Test
    public void testSetTitle() {
        VegaLite vl = new View().title("My Chart");
        assertEquals("My Chart", vl.spec().path("title").asString());
    }

    @Test
    public void testSetName() {
        VegaLite vl = new View().name("chart1");
        assertEquals("chart1", vl.spec().path("name").asString());
    }

    @Test
    public void testSetDescription() {
        VegaLite vl = new View().description("A test chart");
        assertEquals("A test chart", vl.spec().path("description").asString());
    }

    @Test
    public void testSetBackground() {
        VegaLite vl = new View().background("white");
        assertEquals("white", vl.spec().path("background").asString());
    }

    @Test
    public void testPaddingScalar() {
        VegaLite vl = new View().padding(10);
        assertEquals(10, vl.spec().path("padding").asInt());
    }

    @Test
    public void testPaddingPerSide() {
        VegaLite vl = new View().padding(5, 10, 15, 20);
        JsonNode p = vl.spec().path("padding");
        assertEquals(5,  p.path("left").asInt());
        assertEquals(10, p.path("top").asInt());
        assertEquals(15, p.path("right").asInt());
        assertEquals(20, p.path("bottom").asInt());
    }

    @Test
    public void testAutosize() {
        VegaLite vl = new View().autosize();
        JsonNode a = vl.spec().path("autosize");
        assertEquals("pad",     a.path("type").asString());
        assertEquals("content", a.path("contains").asString());
        assertFalse(a.path("resize").asBoolean());
    }

    @Test
    public void testAutosizeCustom() {
        VegaLite vl = new View().autosize("fit", true, "padding");
        JsonNode a = vl.spec().path("autosize");
        assertEquals("fit",     a.path("type").asString());
        assertTrue(a.path("resize").asBoolean());
        assertEquals("padding", a.path("contains").asString());
    }

    // ── html() output ─────────────────────────────────────────────────────────

    @Test
    public void testHtmlContainsQuotedLangAttribute() {
        View v = new View().title("Test");
        String html = v.html();
        assertTrue(html.contains("lang=\"en\""),
                "lang attribute must be quoted: lang=\"en\"");
    }

    @Test
    public void testHtmlContainsTitle() {
        View v = new View().title("My Title");
        String html = v.html();
        assertTrue(html.contains("<title>My Title</title>"));
    }

    @Test
    public void testHtmlDefaultTitleWhenMissing() {
        View v = new View();
        String html = v.html();
        assertTrue(html.contains("<title>Smile Plot</title>"));
    }

    @Test
    public void testHtmlContainsVegaEmbedScript() {
        String html = new View().html();
        assertTrue(html.contains("vega-embed"));
        assertTrue(html.contains("vegaEmbed"));
    }

    @Test
    public void testIframeContainsSrcdoc() {
        View v = new View().title("Iframe Test");
        String iframe = v.iframe("test-id");
        assertTrue(iframe.contains("id=\"test-id\""));
        assertTrue(iframe.contains("srcdoc="));
    }

    @Test
    public void testIframeGeneratesUniqueIds() {
        View v = new View();
        String a = v.iframe();
        String b = v.iframe();
        // Extract ids — they should differ
        assertNotEquals(a, b);
    }

    // ── View dimensions ────────────────────────────────────────────────────────

    @Test
    public void testViewWidthInt() {
        View v = new View().width(800);
        assertEquals(800, v.spec().path("width").asInt());
    }

    @Test
    public void testViewHeightInt() {
        View v = new View().height(600);
        assertEquals(600, v.spec().path("height").asInt());
    }

    @Test
    public void testViewWidthContainer() {
        View v = new View().width("container");
        assertEquals("container", v.spec().path("width").asString());
    }

    @Test
    public void testViewHeightContainer() {
        View v = new View().height("container");
        assertEquals("container", v.spec().path("height").asString());
    }

    @Test
    public void testViewWidthInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> new View().width("invalid"));
    }

    @Test
    public void testViewHeightInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> new View().height("invalid"));
    }

    @Test
    public void testViewWidthStep() {
        View v = new View().widthStep(20);
        assertEquals(20, v.spec().path("width").path("step").asInt());
    }

    @Test
    public void testViewHeightStep() {
        View v = new View().heightStep(15);
        assertEquals(15, v.spec().path("height").path("step").asInt());
    }

    // ── Mark ──────────────────────────────────────────────────────────────────

    @Test
    public void testMarkType() {
        View v = new View();
        v.mark("bar");
        assertEquals("bar", v.spec().path("mark").path("type").asString());
    }

    @Test
    public void testMarkPoint() {
        View v = new View();
        v.mark("line").point(true);
        assertTrue(v.spec().path("mark").path("point").asBoolean());
    }

    @Test
    public void testMarkOpacity() {
        View v = new View();
        v.mark("circle").opacity(0.5);
        assertEquals(0.5, v.spec().path("mark").path("opacity").asDouble(), 1e-9);
    }

    @Test
    public void testMarkColor() {
        View v = new View();
        v.mark("point").color("red");
        assertEquals("red", v.spec().path("mark").path("color").asString());
    }

    @Test
    public void testMarkTooltipString() {
        // tooltip("encoding") → {"tooltip": {"content": "encoding"}}
        View v = new View();
        v.mark("bar").tooltip("encoding");
        JsonNode tooltip = v.spec().path("mark").path("tooltip");
        assertTrue(tooltip.isObject(), "tooltip('encoding') must produce an object node");
        assertEquals("encoding", tooltip.path("content").asString());
    }

    @Test
    public void testMarkTooltipArbitraryText() {
        // tooltip("My text") → {"tooltip": "My text"} (plain string)
        View v = new View();
        v.mark("bar").tooltip("My hover text");
        assertEquals("My hover text", v.spec().path("mark").path("tooltip").asString());
    }

    @Test
    public void testMarkTooltipBoolean() {
        View v = new View();
        v.mark("bar").tooltip(true);
        assertTrue(v.spec().path("mark").path("tooltip").asBoolean());
    }

    // ── Encoding / Field ──────────────────────────────────────────────────────

    @Test
    public void testEncodeFieldType() {
        View v = new View();
        v.mark("point");
        v.encode("x", "Horsepower").type("quantitative");
        JsonNode x = v.spec().path("encoding").path("x");
        assertEquals("Horsepower",  x.path("field").asString());
        assertEquals("quantitative", x.path("type").asString());
    }

    @Test
    public void testEncodeFieldAggregate() {
        View v = new View();
        v.encode("y", "people").type("quantitative").aggregate("sum");
        assertEquals("sum", v.spec().path("encoding").path("y").path("aggregate").asString());
    }

    @Test
    public void testEncodeFieldTitle() {
        View v = new View();
        v.encode("x", "age").type("ordinal").title("Age Group");
        assertEquals("Age Group", v.spec().path("encoding").path("x").path("title").asString());
    }

    @Test
    public void testEncodeNullTitleBecomesNullNode() {
        View v = new View();
        v.encode("x", "age").type("ordinal").title(null);
        assertTrue(v.spec().path("encoding").path("x").path("title").isNull());
    }

    @Test
    public void testEncodeValueInt() {
        View v = new View();
        v.encodeValue("strokeWidth", 2);
        assertEquals(2, v.spec().path("encoding").path("strokeWidth").path("value").asInt());
    }

    @Test
    public void testEncodeValueDouble() {
        View v = new View();
        v.encodeValue("opacity", 0.7);
        assertEquals(0.7, v.spec().path("encoding").path("opacity").path("value").asDouble(), 1e-9);
    }

    @Test
    public void testEncodeValueString() {
        View v = new View();
        v.encodeValue("color", "steelblue");
        assertEquals("steelblue", v.spec().path("encoding").path("color").path("value").asString());
    }

    @Test
    public void testEncodeDatumInt() {
        View v = new View();
        v.encodeDatum("x", 42);
        assertEquals(42, v.spec().path("encoding").path("x").path("datum").asInt());
    }

    @Test
    public void testEncodeDatumDouble() {
        View v = new View();
        v.encodeDatum("y", 3.14);
        assertEquals(3.14, v.spec().path("encoding").path("y").path("datum").asDouble(), 1e-9);
    }

    @Test
    public void testEncodeDatumString() {
        View v = new View();
        v.encodeDatum("x", "2020-01-01");
        assertEquals("2020-01-01", v.spec().path("encoding").path("x").path("datum").asString());
    }

    @Test
    public void testEncodeTimeUnit() {
        View v = new View();
        v.encode("x", "date").type("temporal").timeUnit("month");
        assertEquals("month", v.spec().path("encoding").path("x").path("timeUnit").asString());
    }

    @Test
    public void testEncodeBinTrue() {
        View v = new View();
        v.encode("x", "rating").type("quantitative").bin(true);
        assertTrue(v.spec().path("encoding").path("x").path("bin").asBoolean());
    }

    @Test
    public void testEncodeBinned() {
        View v = new View();
        v.encode("x", "bin_hp").type("quantitative").bin("binned");
        assertEquals("binned", v.spec().path("encoding").path("x").path("bin").asString());
    }

    @Test
    public void testEncodeSort() {
        View v = new View();
        v.encode("y", "age").type("ordinal").sort("-x");
        assertEquals("-x", v.spec().path("encoding").path("y").path("sort").asString());
    }

    @Test
    public void testEncodeZeroFalse() {
        View v = new View();
        v.encode("x", "value").type("quantitative").zero(false);
        assertFalse(v.spec().path("encoding").path("x").path("scale").path("zero").asBoolean());
    }

    @Test
    public void testEncodeRange() {
        View v = new View();
        v.encode("size", "Deaths").type("quantitative").range(0, 5000);
        JsonNode range = v.spec().path("encoding").path("size").path("scale").path("range");
        assertTrue(range.isArray());
        assertEquals(0,    range.get(0).asInt());
        assertEquals(5000, range.get(1).asInt());
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    @Test
    public void testDataUrl() {
        View v = new View();
        v.data().url("https://example.com/data.json");
        assertEquals("https://example.com/data.json", v.spec().path("data").path("url").asString());
    }

    @Test
    public void testDataName() {
        View v = new View();
        v.data().name("myDataset");
        assertEquals("myDataset", v.spec().path("data").path("name").asString());
    }

    @Test
    public void testDataValuesJsonArray() {
        View v = new View();
        assertDoesNotThrow(() -> v.data().values("[{\"a\":1},{\"a\":2}]"));
        JsonNode values = v.spec().path("data").path("values");
        assertTrue(values.isArray(), "values must be a JSON array, not a string");
        assertEquals(2, values.size());
        assertEquals(1, values.get(0).path("a").asInt());
    }

    @Test
    public void testDataValuesJsonObject() {
        View v = new View();
        assertDoesNotThrow(() -> v.data().values("{\"field\":42}"));
        JsonNode values = v.spec().path("data").path("values");
        assertTrue(values.isObject(), "values must be a JSON object, not a string");
        assertEquals(42, values.path("field").asInt());
    }

    @Test
    public void testDataValuesObjectArray() {
        record Point(double x, double y) {}
        View v = new View();
        v.data().values(new Point[]{new Point(1, 2), new Point(3, 4)});
        JsonNode values = v.spec().path("data").path("values");
        assertTrue(values.isArray());
        assertEquals(1.0, values.get(0).path("x").asDouble(), 1e-9);
    }

    @Test
    public void testDataFormatCsv() {
        View v = new View();
        v.data().url("data.csv").format("csv");
        assertEquals("csv", v.spec().path("data").path("format").path("type").asString());
    }

    @Test
    public void testDataJson() {
        View v = new View();
        v.data().json("https://example.com/data.json", "values.features");
        assertEquals("json", v.spec().path("data").path("format").path("type").asString());
        assertEquals("values.features", v.spec().path("data").path("format").path("property").asString());
    }

    @Test
    public void testDataTopojson() {
        View v = new View();
        v.data().topojson("us.json", "feature", "counties");
        assertEquals("topojson", v.spec().path("data").path("format").path("type").asString());
        assertEquals("counties", v.spec().path("data").path("format").path("feature").asString());
    }

    @Test
    public void testDataDsv() {
        View v = new View();
        v.data().dsv("data.dsv", "|");
        assertEquals("dsv", v.spec().path("data").path("format").path("type").asString());
        assertEquals("|", v.spec().path("data").path("format").path("delimiter").asString());
    }

    // ── Transform ─────────────────────────────────────────────────────────────

    @Test
    public void testTransformFilter() {
        View v = new View();
        v.transform().filter("datum.year == 2000");
        JsonNode t = v.spec().path("transform").get(0);
        assertEquals("datum.year == 2000", t.path("filter").asString());
    }

    @Test
    public void testTransformCalculate() {
        View v = new View();
        v.transform().calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");
        JsonNode t = v.spec().path("transform").get(0);
        assertEquals("gender", t.path("as").asString());
        assertTrue(t.path("calculate").asString().contains("Female"));
    }

    @Test
    public void testTransformBin() {
        View v = new View();
        v.transform().bin("Horsepower", "bin_hp");
        JsonNode t = v.spec().path("transform").get(0);
        assertEquals("Horsepower", t.path("field").asString());
        assertEquals("bin_hp",     t.path("as").asString());
        assertTrue(t.path("bin").asBoolean());
    }

    @Test
    public void testTransformAggregate() {
        View v = new View();
        v.transform().aggregate("count", "*", "count", "IMDB Rating");
        JsonNode t = v.spec().path("transform").get(0);
        assertTrue(t.has("aggregate"));
        assertEquals("count", t.path("aggregate").get(0).path("op").asString());
        assertEquals("IMDB Rating", t.path("groupby").get(0).asString());
    }

    @Test
    public void testTransformJoinAggregate() {
        View v = new View();
        v.transform().joinAggregate("sum", "Count", "TotalCount");
        JsonNode t = v.spec().path("transform").get(0);
        assertTrue(t.has("joinaggregate"));
        assertEquals("TotalCount", t.path("joinaggregate").get(0).path("as").asString());
    }

    @Test
    public void testTransformSample() {
        View v = new View();
        v.transform().sample(1000);
        JsonNode t = v.spec().path("transform").get(0);
        assertEquals(1000, t.path("sample").asInt());
    }

    @Test
    public void testTransformTimeUnit() {
        View v = new View();
        v.transform().timeUnit("year", "date", "year");
        JsonNode t = v.spec().path("transform").get(0);
        assertEquals("year", t.path("timeUnit").asString());
        assertEquals("date", t.path("field").asString());
    }

    @Test
    public void testTransformFold() {
        View v = new View();
        v.transform().fold(new String[]{"A", "B"}, new String[]{"key", "value"});
        JsonNode t = v.spec().path("transform").get(0);
        assertTrue(t.has("fold"));
        assertEquals("A", t.path("fold").get(0).asString());
        assertEquals("key", t.path("as").get(0).asString());
    }

    @Test
    public void testTransformFlatten() {
        View v = new View();
        v.transform().flatten(new String[]{"tags"}, new String[]{"tag"});
        JsonNode t = v.spec().path("transform").get(0);
        assertTrue(t.has("flatten"));
    }

    @Test
    public void testTransformWindowOmitsNullFieldAndParam() {
        View v = new View();
        v.transform().window(new WindowTransformField("sum", "count", 0, "CumCount"));
        JsonNode win = v.spec().path("transform").get(0).path("window").get(0);
        assertEquals("sum",      win.path("op").asString());
        assertEquals("CumCount", win.path("as").asString());
        assertEquals("count",    win.path("field").asString());
        // param was 0, so it must be omitted
        assertFalse(win.has("param"), "param=0 must be omitted from window spec");
    }

    @Test
    public void testTransformWindowNoFieldOmitted() {
        View v = new View();
        // "rank" has no field and no param
        v.transform().window(new WindowTransformField("rank", null, 0, "Rank"));
        JsonNode win = v.spec().path("transform").get(0).path("window").get(0);
        assertEquals("rank", win.path("op").asString());
        assertFalse(win.has("field"), "null field must be omitted from window spec");
    }

    // ── Multiple chained transforms ──────────────────────────────────────────

    @Test
    public void testTransformChaining() {
        View v = new View();
        v.transform()
                .filter("datum.year == 2000")
                .calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");
        assertEquals(2, v.spec().path("transform").size(),
                "Two chained transforms must produce two entries");
    }

    @Test
    public void testTransformIdempotentAccess() {
        // Calling .transform() twice must append to the SAME array
        View v = new View();
        v.transform().filter("datum.a > 10");
        v.transform().filter("datum.b < 5");
        assertEquals(2, v.spec().path("transform").size());
    }

    // ── Predicate ─────────────────────────────────────────────────────────────

    @Test
    public void testPredicateValid() {
        // valid(field) produces {"field": "rating", "valid": true}
        Predicate p = Predicate.valid("rating");
        assertNotNull(p.spec);
        assertEquals("rating", p.spec.path("field").asString());
        assertTrue(p.spec.path("valid").asBoolean());
    }

    @Test
    public void testPredicateEqual() {
        // Use Predicate.of(field, "equal", value) for equality predicates
        Predicate p = Predicate.of("year", "equal", 2000.0);
        assertNotNull(p.spec);
        assertEquals("year", p.spec.path("field").asString());
        assertEquals(2000,   p.spec.path("equal").asInt());
    }

    @Test
    public void testPredicateRange() {
        Predicate p = Predicate.range("score", 0, 100);
        assertNotNull(p.spec);
        JsonNode range = p.spec.path("range");
        assertTrue(range.isArray());
        assertEquals(0,   range.get(0).asInt());
        assertEquals(100, range.get(1).asInt());
    }

    @Test
    public void testPredicateOneOf() {
        Predicate p = Predicate.oneOf("country", "USA", "UK");
        assertNotNull(p.spec);
        JsonNode oneOf = p.spec.path("oneOf");
        assertTrue(oneOf.isArray());
        assertEquals("USA", oneOf.get(0).asString());
    }

    @Test
    public void testPredicateAnd() {
        Predicate a = Predicate.valid("x");
        Predicate b = Predicate.valid("y");
        Predicate and = Predicate.and(a, b);
        assertNotNull(and.spec);
        assertTrue(and.spec.has("and"));
        assertEquals(2, and.spec.path("and").size());
    }

    @Test
    public void testPredicateOr() {
        Predicate a = Predicate.valid("x");
        Predicate b = Predicate.valid("y");
        Predicate or = Predicate.or(a, b);
        assertNotNull(or.spec);
        assertTrue(or.spec.has("or"));
        assertEquals(2, or.spec.path("or").size());
    }

    @Test
    public void testPredicateNot() {
        Predicate p = Predicate.not(Predicate.valid("x"));
        assertNotNull(p.spec);
        assertTrue(p.spec.has("not"));
    }

    @Test
    public void testPredicateFilterInTransform() {
        View v = new View();
        v.transform().filter(Predicate.and(Predicate.valid("x"), Predicate.valid("y")));
        JsonNode filter = v.spec().path("transform").get(0).path("filter");
        assertTrue(filter.has("and"));
    }

    // ── Layer ─────────────────────────────────────────────────────────────────

    @Test
    public void testLayerContainsViews() {
        View line = new View();
        line.mark("line");
        View band = new View();
        band.mark("errorband");

        Layer layer = new Layer(line, band);
        JsonNode layerNode = layer.spec().path("layer");
        assertTrue(layerNode.isArray());
        assertEquals(2, layerNode.size());
    }

    @Test
    public void testLayerCanSetSharedData() {
        View a = new View();
        a.mark("line");
        View b = new View();
        b.mark("point");

        Layer layer = new Layer(a, b);
        layer.data().url("https://example.com/stocks.csv");

        assertEquals("https://example.com/stocks.csv",
                layer.spec().path("data").path("url").asString());
    }

    @Test
    public void testLayerCanSetSharedEncoding() {
        View a = new View();
        View b = new View();
        Layer layer = new Layer(a, b);
        layer.encode("x", "date").type("temporal");

        assertEquals("date", layer.spec().path("encoding").path("x").path("field").asString());
    }

    // ── Concat ────────────────────────────────────────────────────────────────

    @Test
    public void testHorizontalConcat() {
        View v1 = new View();
        View v2 = new View();
        Concat c = Concat.horizontal(v1, v2);
        assertTrue(c.spec().has("hconcat"));
        assertEquals(2, c.spec().path("hconcat").size());
    }

    @Test
    public void testVerticalConcat() {
        View v1 = new View();
        View v2 = new View();
        Concat c = Concat.vertical(v1, v2);
        assertTrue(c.spec().has("vconcat"));
        assertEquals(2, c.spec().path("vconcat").size());
    }

    @Test
    public void testFlowConcat() {
        View v1 = new View();
        View v2 = new View();
        View v3 = new View();
        Concat c = new Concat(2, v1, v2, v3);
        assertTrue(c.spec().has("concat"));
        assertEquals(3, c.spec().path("concat").size());
        assertEquals(2, c.spec().path("columns").asInt());
    }

    // ── ViewComposition resolve ───────────────────────────────────────────────

    @Test
    public void testResolveScale() {
        Concat c = Concat.horizontal(new View(), new View());
        c.resolveScale("color", "independent");
        assertEquals("independent",
                c.spec().path("resolve").path("scale").path("color").asString());
    }

    @Test
    public void testResolveAxis() {
        Concat c = Concat.vertical(new View(), new View());
        c.resolveAxis("y", "independent");
        assertEquals("independent",
                c.spec().path("resolve").path("axis").path("y").asString());
    }

    @Test
    public void testResolveLegend() {
        Concat c = Concat.horizontal(new View(), new View());
        c.resolveLegend("color", "shared");
        assertEquals("shared",
                c.spec().path("resolve").path("legend").path("color").asString());
    }

    // ── toString / toPrettyString ─────────────────────────────────────────────

    @Test
    public void testToStringIsValidJson() {
        View v = new View().title("JSON test");
        v.mark("bar");
        v.encode("x", "category").type("ordinal");
        v.encode("y", "value").type("quantitative");

        String json = v.toString();
        // Must be parseable without exception
        JsonNode node = VegaLite.mapper.readTree(json);
        assertNotNull(node);
        assertEquals("JSON test", node.path("title").asString());
    }

    @Test
    public void testToPrettyStringContainsNewlines() {
        View v = new View().title("Pretty");
        String pretty = v.toPrettyString();
        assertTrue(pretty.contains("\n"), "toPrettyString must contain newlines");
    }
}





