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
import static smile.plot.vega.Predicate.*;

/**
 *
 * @author Haifeng Li
 */
public class VegaTest {

    public VegaTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testBar() throws Exception {
        System.out.println("Bar");

        var bar = new View("Simple Bar Plot")
                .description("A simple bar chart with embedded data.")
                .widthStep(30);

        bar.data().values("""
                [
                  {"a": "A", "b": 28}, {"a": "B", "b": 55}, {"a": "C", "b": 43},
                  {"a": "D", "b": 91}, {"a": "E", "b": 81}, {"a": "F", "b": 53},
                  {"a": "G", "b": 19}, {"a": "H", "b": 87}, {"a": "I", "b": 52}
                ]""");

        bar.mark("bar");
        Field x = bar.encode("x", "a").type("ordinal");
        x.axis().labelAngle(0);
        bar.encode("y", "b").type("quantitative");
    }

    @Test
    public void testAggregateBar() throws Exception {
        System.out.println("Aggregate Bar");

        var bar = new View("Aggregate Bar Plot")
                .description("A bar chart showing the US population distribution of age groups in 2000.")
                .heightStep(20);

        bar.mark("bar");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        bar.transform().filter("datum.year == 2000");
        bar.encode("x", "people").type("quantitative").aggregate("sum").title("population");
        bar.encode("y", "age").type("ordinal");
        Assertions.assertEquals("[{\"filter\":\"datum.year == 2000\"}]", bar.transform().toString());
    }

    @Test
    public void testSortedAggregateBar() throws Exception {
        System.out.println("Sorted Aggregate Bar");

        var bar = new View("Sorted Aggregate Bar Plot")
                .description("A bar chart that sorts the y-values by the x-values.")
                .heightStep(20);

        bar.mark("bar");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        bar.transform().filter("datum.year == 2000");
        bar.encode("x", "people").type("quantitative").aggregate("sum").title("population");
        bar.encode("y", "age").type("ordinal").sort("-x");
    }

    @Test
    public void testGroupBar() throws Exception {
        System.out.println("Group Bar");

        var bar = new View("Group Bar Plot").widthStep(12);

        bar.mark("bar");
        bar.viewConfig().stroke("transparent").axis().domainWidth(1);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        bar.transform()
                .filter("datum.year == 2000")
                .calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");

        bar.encode("x", "gender").type("nominal").title(null);
        bar.encode("y", "people").type("quantitative").aggregate("sum").axis().title("population").grid(false);
        bar.encode("color", "gender").type("nominal").range("#675193", "#ca8861");
        bar.encode("column", "age").type("ordinal").spacing(10);
    }

    @Test
    public void testStackedBar() throws Exception {
        System.out.println("Stacked Bar");

        var bar = new View("Stacked Bar Plot");
        bar.mark("bar");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv");
        bar.encode("x", "date").type("ordinal").timeUnit("month").title("Month of the year");
        bar.encode("y", null).type("quantitative").aggregate("count");
        bar.encode("color", "weather").type("nominal")
                .domain("sun", "fog", "drizzle", "rain", "snow")
                .range("#e7ba52", "#c7c7c7", "#aec7e8", "#1f77b4", "#9467bd")
                .legend().title("Weather type");
    }

    @Test
    public void testStackedBarWithRoundedCorner() throws Exception {
        System.out.println("Stacked Bar with Rounded Corner");

        var bar = new View("Stacked Bar with Rounded Corner");
        bar.mark("bar").cornerRadiusTopLeft(3).cornerRadiusTopRight(3);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv");
        bar.encode("x", "date").type("ordinal").timeUnit("month").title("Month of the year");
        bar.encode("y", null).type("quantitative").aggregate("count");
        bar.encode("color", "weather").type("nominal");
    }

    @Test
    public void testHorizontalStackedBar() throws Exception {
        System.out.println("Horizontal Stacked Bar");

        var bar = new View("Horizontal Stacked Bar");
        bar.mark("bar");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/barley.json");
        bar.encode("x", "yield").type("quantitative").aggregate("sum");
        bar.encode("y", "variety").type("nominal");
        bar.encode("color", "site").type("nominal");
    }

    @Test
    public void testLayeredBar() throws Exception {
        System.out.println("Layered Bar");

        var bar = new View("Layered Bar").widthStep(17);

        bar.mark("bar");
        bar.background().opacity(0.7);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        bar.transform()
                .filter("datum.year == 2000")
                .calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");

        bar.encode("x", "age").type("ordinal");
        bar.encode("y", "people").type("quantitative").aggregate("sum").title("population").stack(null);
        bar.encode("color", "gender").type("nominal").range("#675193", "#ca8861");
    }

    @Test
    public void testNormalizedStackedBar() throws Exception {
        System.out.println("Normalized (Percentage) Stacked Bar");

        var bar = new View("Normalized (Percentage) Stacked Bar").widthStep(17);

        bar.mark("bar");
        bar.background().opacity(0.7);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        bar.transform()
                .filter("datum.year == 2000")
                .calculate("datum.sex == 2 ? 'Female' : 'Male'", "gender");

        bar.encode("x", "age").type("ordinal");
        bar.encode("y", "people").type("quantitative").aggregate("sum").title("population").stack("normalize");
        bar.encode("color", "gender").type("nominal").range("#675193", "#ca8861");
    }

    @Test
    public void testGanttChart() throws Exception {
        System.out.println("Gantt Chart");

        var gantt = new View("Gantt Chart");
        gantt.data().values("""
                [
                  {"task": "A", "start": 1, "end": 3},
                  {"task": "B", "start": 3, "end": 8},
                  {"task": "C", "start": 8, "end": 10}
                ]""");

        gantt.mark("bar");
        gantt.encode("x", "start").type("quantitative");
        gantt.encode("x2", "end").type("quantitative");
        gantt.encode("y", "task").type("ordinal");
    }

    @Test
    public void testColorBarChart() throws Exception {
        System.out.println("Color Bar Chart");

        var bar = new View("Bar Chart Encoding Color Names in the Data");
        bar.data().values("""
                [
                  {"color": "red", "b": 28},
                  {"color": "green", "b": 55},
                  {"color": "blue", "b": 43}
                ]""");

        bar.mark("bar");
        bar.encode("x", "color").type("nominal");
        bar.encode("y", "b").type("quantitative");
        bar.encode("color", "color").type("nominal").scale(null);
    }

    @Test
    public void testHistogram() throws Exception {
        System.out.println("Histogram");

        var bar = new View("Histogram");
        bar.mark("bar");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/movies.json");
        bar.encode("x", "IMDB Rating").type("quantitative").bin(true);
        bar.encode("y", null).type("quantitative").aggregate("count");
        bar.encode("color", "gender").type("nominal").range("#675193", "#ca8861");
    }

    @Test
    public void testRelativeFrequencyHistogram() throws Exception {
        System.out.println("Relative Frequency Histogram");

        var bar = new View("Relative Frequency Histogram");

        bar.mark("bar").tooltip(true);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
        bar.encode("x", "bin_Horsepwoer").type("quantitative").bin("binned").title("Horsepower");
        bar.encode("x2", "bin_Horsepwoer_end").type("quantitative");
        bar.encode("y", "PercentOfTotal").type("quantitative").title("Relative Frequency").axis().format(".1~%");

        bar.transform()
                .bin("Horsepower", "bin_Horsepwoer")
                .aggregate("count", null, "Count", "bin_Horsepwoer", "bin_Horsepwoer_end")
                .joinAggregate("sum", "Count", "TotalCount")
                .calculate("datum.Count/datum.TotalCount", "PercentOfTotal");
    }

    @Test
    public void test2DHistogramHeatmap() throws Exception {
        System.out.println("2D Histogram Heatmap");

        var bar = new View("2D Histogram Heatmap").width(300).height(200);
        bar.mark("rect");
        bar.viewConfig().stroke("transparent");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/movies.json");
        bar.encode("x", "IMDB Rating").type("quantitative").title("IMDB Rating").bin(new BinParams().maxBins(60));
        bar.encode("y", "Rotten Tomatoes Rating").type("quantitative").bin(new BinParams().maxBins(40));
        bar.encode("color", null).type("quantitative").aggregate("count");
        bar.transform().filter(and(valid("IMDB Rating"), valid("Rotten Tomatoes Rating")));
        bar.show(true);
    }

    @Test
    public void testDensityPlot() throws Exception {
        System.out.println("Density Plot");

        var plot = new View("Density Plot").width(400).height(100);
        plot.mark("area");
        plot.data().url("https://vega.github.io/vega-lite/examples/data/movies.json");
        plot.encode("x", "value").type("quantitative").title("IMDB Rating");
        plot.encode("y", "density").type("quantitative");
        plot.transform().density("IMDB Rating").bandwidth(0.3);
    }

    @Test
    public void testCumulativeFrequencyDistribution() throws Exception {
        System.out.println("Cumulative Frequency Distribution");

        var plot = new View("Cumulative Frequency Distribution");
        plot.mark("area");
        plot.data().url("https://vega.github.io/vega-lite/examples/data/movies.json");
        plot.encode("x", "IMDB Rating").type("quantitative");
        plot.encode("y", "Cumulative Count").type("quantitative");
        plot.transform().aggregate("count", "*", "count", "IMDB Rating")
            .window(new WindowTransformField("sum", "count", 0, "Cumulative Count"))
            .sort("IMDB Rating").frame(null, 0);
    }

    @Test
    public void testScatterPlot() throws Exception {
        System.out.println("Scatter Plot");

        var bar = new View("Scatter Plot");
        bar.mark("point");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
        bar.encode("x", "Horsepower").type("quantitative");
        bar.encode("y", "Miles_per_Gallon").type("quantitative");
        bar.encode("color", "Origin").type("nominal");
        bar.encode("shape", "Origin").type("nominal");
    }

    @Test
    public void testBubblePlot() throws Exception {
        System.out.println("Bubble Plot");

        var bar = new View("Bubble Plot");
        bar.mark("point");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
        bar.encode("x", "Horsepower").type("quantitative");
        bar.encode("y", "Miles_per_Gallon").type("quantitative");
        bar.encode("size", "Acceleration").type("quantitative");
    }

    @Test
    public void testNaturalDisasters() throws Exception {
        System.out.println("Natural Disasters");

        var bar = new View("Natural Disasters").width(600).height(400);
        bar.mark("circle").opacity(0.8).stroke("black").strokeWidth(1);
        bar.data().url("https://vega.github.io/vega-lite/examples/data/disasters.csv");
        bar.transform().filter("datum.Entity !== 'All natural disasters'");
        bar.encode("x", "Year").type("ordinal").axis().labelAngle(90).labelOverlap("greedy");
        bar.encode("y", "Entity").type("nominal").title(null);
        bar.encode("color", "Entity").type("nominal").removeLegend();
        bar.encode("size", "Deaths").type("quantitative")
                .range(0, 5000)
                .legend().title("Annual Global Deaths").clipHeight(30);
    }

    @Test
    public void testTextPlot() throws Exception {
        System.out.println("Text Plot");

        var bar = new View("Text Plot");
        bar.mark("text");
        bar.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
        bar.transform().calculate("split(datum.Name, ' ')[0]", "Brand");
        bar.encode("x", "Horsepower").type("quantitative");
        bar.encode("y", "Miles_per_Gallon").type("quantitative");
        bar.encode("color", "Brand").type("nominal");
        bar.encode("text", "Brand").type("nominal");
    }

    @Test
    public void testLineChart() throws Exception {
        System.out.println("Line Chart");

        var line = new View("Line Chart");
        line.mark("line");
        line.data().url("https://vega.github.io/vega-lite/examples/data/stocks.csv");
        line.transform().filter("datum.symbol==='GOOG'");
        line.encode("x", "date").type("temporal");
        line.encode("y", "price").type("quantitative");
    }

    @Test
    public void testLineChartWithPointMark() throws Exception {
        System.out.println("Line Chart with Point Mark");

        var line = new View("Line Chart with Point Mark");
        line.mark("line").point(true);
        line.data().url("https://vega.github.io/vega-lite/examples/data/stocks.csv");
        line.encode("x", "date").type("temporal").timeUnit("year");
        line.encode("y", "price").type("quantitative").aggregate("mean");
        line.encode("color", "symbol").type("nominal");
    }

    @Test
    public void testLineChartWithConfidenceIntervalBand() throws Exception {
        System.out.println("Line Chart with Confidence Interval Band");

        var line = new View();
        line.mark("line");
        line.encode("x", "Year").type("temporal").timeUnit("year");
        line.encode("y", "Miles_per_Gallon").type("quantitative").aggregate("mean");

        var band = new View();
        band.mark("errorband").extent("ci");
        band.encode("x", "Year").type("temporal").timeUnit("year");
        band.encode("y", "Miles_per_Gallon").type("quantitative").title("Mean of Miles per Gallon (95% CIs)");

        var layer = new Layer(line, band).title("Line Chart with Confidence Interval Band");
        layer.data().url("https://vega.github.io/vega-lite/examples/data/cars.json");
    }

    @Test
    public void testRollingAveragesOverRawValues() throws Exception {
        System.out.println("Rolling Averages over Raw Values");

        var line = new View();
        line.mark("line").color("red").size(3);
        line.encode("x", "date").type("temporal");
        line.encode("y", "rolling_mean").type("quantitative");

        var point = new View();
        point.mark("point").opacity(0.3);
        point.encode("x", "date").type("temporal").title("Date");
        point.encode("y", "temp_max").type("quantitative").title("Max Temperature");

        var layer = new Layer(line, point).title("Rolling Averages over Raw Values").width(400).height(300);
        layer.data().format("csv").url("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv");
        layer.transform().window(new WindowTransformField("mean", "temp_max", 0, "rolling_mean")).frame(-15, 15);
        layer.show(true);
    }

    @Test
    public void testAreaChart() throws Exception {
        System.out.println("Area Chart with Overlaying Lines and Point Markers");

        var area = new View("Area Chart with Overlaying Lines and Point Markers");
        area.data().url("https://vega.github.io/vega-lite/examples/data/stocks.csv");
        area.transform().filter("datum.symbol==='GOOG'");
        area.mark("area").line(true).point(true);
        area.encode("x", "date").type("temporal");
        area.encode("y", "price").type("quantitative");
    }

    @Test
    public void testHeatmap() throws Exception {
        System.out.println("Annual Weather Heatmap");

        var heatmap = new View("2010 Daily Max Temperature (F) in Seattle, WA");

        heatmap.mark("rect");
        heatmap.data().url("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv");
        heatmap.encode("x", "date").type("ordinal").timeUnit("date").title("Day").axis().labelAngle(0).format("%e");
        heatmap.encode("y", "date").type("ordinal").timeUnit("month");
        heatmap.encode("color", "temp_max").type("quantitative").aggregate("max").legend().title(null);

        heatmap.config().axis().domain(false);
        heatmap.viewConfig().strokeWidth(0).step(13);
    }

    @Test
    public void testDonutChart() throws Exception {
        System.out.println("Donut Chart");

        var donut = new View("Donut Chart");
        donut.data().values("""
                [
                  {"category": 1, "value": 4},
                  {"category": 2, "value": 6},
                  {"category": 3, "value": 10},
                  {"category": 4, "value": 3},
                  {"category": 5, "value": 7},
                  {"category": 6, "value": 8}
                ]""");

        donut.mark("arc").innerRadius(50);
        donut.encode("theta", "value").type("quantitative");
        donut.encode("color", "category").type("nominal");
        donut.viewConfig().stroke(null);
    }

    @Test
    public void testRadialChart() throws Exception {
        System.out.println("Radial Chart");

        var arc = new View();
        arc.mark("arc").innerRadius(20).stroke("#fff");

        var text = new View();
        text.mark("text").radiusOffset(10);
        text.encode("text", "data").type("quantitative");

        var layer = new Layer(arc, text).title("Radial Chart");
        layer.background().stroke(null);
        layer.data().values("[12, 23, 47, 6, 52, 19]");
        layer.encode("theta", "data").type("quantitative").stack("zero");
        layer.encode("radius", "data").type("quantitative").scale("sqrt").zero(true).range(20, 100);
        layer.encode("color", "data").type("nominal").removeLegend();
    }

    @Test
    public void testBoxPlot() throws Exception {
        System.out.println("Box Plot");

        var boxplot = new View("Box Plot");
        boxplot.background().stroke(null);
        boxplot.mark("boxplot").extent("min-max");
        boxplot.viewConfig().stroke(null);
        boxplot.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        boxplot.encode("x", "age").type("ordinal");
        boxplot.encode("y", "people").type("quantitative").title("population");
    }

    @Test
    public void testConcat() throws Exception {
        System.out.println("Concat");

        var donut = new View("Donut Chart");
        donut.data().values("""
                [
                  {"category": 1, "value": 4},
                  {"category": 2, "value": 6},
                  {"category": 3, "value": 10},
                  {"category": 4, "value": 3},
                  {"category": 5, "value": 7},
                  {"category": 6, "value": 8}
                ]""");

        donut.mark("arc").innerRadius(50);
        donut.encode("theta", "value").type("quantitative");
        donut.encode("color", "category").type("nominal");
        donut.viewConfig().stroke(null);

        var boxplot = new View("Box Plot");
        boxplot.background().stroke(null);
        boxplot.mark("boxplot").extent("min-max");
        boxplot.viewConfig().stroke(null);
        boxplot.data().url("https://vega.github.io/vega-lite/examples/data/population.json");
        boxplot.encode("x", "age").type("ordinal");
        boxplot.encode("y", "people").type("quantitative").title("population");

        var concat = Concat.vertical(donut, boxplot).title("Vertical Concatenation");
        concat.show(true);
    }

    @Test
    public void testSPLOM() throws Exception {
        System.out.println("Scatter Plot Matrix");

        var plot = new View().width(150).height(150);
        plot.mark("point");
        plot.encode("x", "repeat:column").type("quantitative").zero(false);
        plot.encode("y", "repeat:row").type("quantitative").zero(false);
        plot.encode("color", "species").type("nominal");

        String[] row = {"petalWidth", "petalLength", "sepalWidth", "sepalLength"};
        String[] column = {"sepalLength", "sepalWidth", "petalLength", "petalWidth"};
        var splom = new Repeat(plot, row, column).title("Scatter Plot Matrix");
        splom.data().url("https://raw.githubusercontent.com/domoritz/maps/master/data/iris.json");
        splom.show(true);
    }

    @Test
    public void testGeo() throws Exception {
        System.out.println("Choropleth of Unemployment Rate per County");

        var geo = new View("Choropleth of Unemployment Rate per County").width(500).height(300);
        geo.mark("geoshape").extent("min-max");
        geo.data().topojson("https://vega.github.io/vega-lite/examples/data/us-10m.json", "feature", "counties");
        geo.encode("color", "rate").type("quantitative");
        geo.projection("albersUsa");

        var transform = geo.transform();
        var lookupData = transform.lookupData("id").fields("rate");
        lookupData.data().url("https://vega.github.io/vega-lite/examples/data/unemployment.tsv");
        geo.transform().lookup("id", lookupData);
        geo.show(true);
    }
}
