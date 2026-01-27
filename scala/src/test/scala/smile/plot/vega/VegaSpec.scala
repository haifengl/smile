/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.plot.vega

import scala.language.existentials
import smile.json.*
import org.specs2.mutable.*

class VegaSpec extends Specification {

  "VegaLite" should {
    "Simple Bar Plot" in {
      val bar = VegaLite.view("Simple Bar Plot")
        .description("A simple bar chart with embedded data.")
        .mark("bar")
        .heightStep(17)
        .x(field = "a", `type` = "ordinal", axis = JsObject("labelAngel" -> JsInt(0)))
        .y(field = "b", `type` = "quantitative")
        .data(jsan"""
      [
        {"a": "A", "b": 28}, {"a": "B", "b": 55}, {"a": "C", "b": 43},
        {"a": "D", "b": 91}, {"a": "E", "b": 81}, {"a": "F", "b": 53},
        {"a": "G", "b": 19}, {"a": "H", "b": 87}, {"a": "I", "b": 52}
      ]"""
        )
      1 mustEqual 1
    }
    "Aggregate Bar Chart" in {
      val aggregateBar = VegaLite.view("Aggregate Bar Chart")
        .description("A bar chart showing the US population distribution of age groups in 2000.")
        .mark("bar")
        .heightStep(17)
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "people", `type` = "quantitative", aggregate = "sum", title = "population")
        .y(field = "age", `type` = "ordinal")
        .transform(json"""{"filter": "datum.year == 2000"}""")
      1 mustEqual 1
    }
    "Aggregate Bar Chart (Sorted)" in {
      val sortedAggregateBar = VegaLite.view("Aggregate Bar Chart (Sorted)")
        .description("A bar chart that sorts the y-values by the x-values.")
        .mark("bar")
        .heightStep(17)
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "people", `type` = "quantitative", aggregate = "sum", title = "population")
        .y(field = "age", `type` = "ordinal", sort = "-x")
        .transform(json"""{"filter": "datum.year == 2000"}""")
      1 mustEqual 1
    }
    "Grouped Bar Chart" in {
      val groupedBar = VegaLite.facet("Grouped Bar Chart")
        .column(field = "age", `type` = "ordinal", spacing = 10)
        .mark("bar")
        .widthStep(12)
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .config(json"""{"view": {"stroke": "transparent"}, "axis": {"domainWidth": 1}}""")
        .x(field = "gender", `type` = "nominal", title = null)
        .y(field = "people", `type` = "quantitative", aggregate = "sum", axis = json"""{"title": "population", "grid": false}""")
        .color(field = "gender", `type` = "nominal", scale = json"""{"range": ["#675193", "#ca8861"]}""")
        .transform(
          json"""{"filter": "datum.year == 2000"}""",
          json"""{"calculate": "datum.sex == 2 ? 'Female' : 'Male'", "as": "gender"}"""
        )
      1 mustEqual 1
    }
    "Stacked Bar Chart" in {
      val stackedBar = VegaLite.view("Stacked Bar Chart")
        .mark("bar")
        .data("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv")
        .x(field = "date", `type` = "ordinal", timeUnit = "month", title = "Month of the year")
        .y(field = null, aggregate = "count", `type` = "quantitative")
        .color(field = "weather", `type` = "nominal",
          scale = json"""{
            "domain": ["sun", "fog", "drizzle", "rain", "snow"],
            "range": ["#e7ba52", "#c7c7c7", "#aec7e8", "#1f77b4", "#9467bd"]
          }""",
          legend = JsObject("title" -> JsString("Weather type"))
        )
      1 mustEqual 1
    }
    "Stacked Bar Chart with Rounded Corners" in {
      val stackedRoundedBar = VegaLite.view("Stacked Bar Chart with Rounded Corners")
        .mark(JsObject("type" -> "bar", "cornerRadiusTopLeft" -> 3, "cornerRadiusTopRight" -> 3))
        .data("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv")
        .x(field = "date", `type` = "ordinal", timeUnit = "month")
        .y(field = null, aggregate = "count", `type` = "quantitative")
        .color(field = "weather", `type` = "nominal")
      1 mustEqual 1
    }
    "Horizontal Stacked Bar Chart" in {
      val horizontalStackedBar = VegaLite.view("Horizontal Stacked Bar Chart")
        .mark("bar")
        .data("https://vega.github.io/vega-lite/examples/data/barley.json")
        .x(field = "yield", `type` = "quantitative", aggregate = "sum")
        .y(field = "variety", `type` = "nominal")
        .color(field = "site", `type` = "nominal")
      1 mustEqual 1
    }
    "Layered Bar Chart" in {
      val layeredBar = VegaLite.view("Layered Bar Chart")
        .mark("bar")
        .widthStep(17)
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "age", `type` = "ordinal")
        .y(field = "people", `type` = "quantitative", aggregate = "sum", title = "population", stack = JsNull)
        .color(field = "gender", `type` = "nominal", scale = json"""{"range": ["#675193", "#ca8861"]}""")
        .opacity(0.7)
        .transform(
          json"""{"filter": "datum.year == 2000"}""",
          json"""{"calculate": "datum.sex == 2 ? 'Female' : 'Male'", "as": "gender"}"""
        )
      1 mustEqual 1
    }
    "Normalized (Percentage) Stacked Bar Chart" in {
      val normalizedStackedBar = VegaLite.view("Normalized (Percentage) Stacked Bar Chart")
        .mark("bar")
        .widthStep(17)
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "age", `type` = "ordinal")
        .y(field = "people", `type` = "quantitative", aggregate = "sum", title = "population", stack = "normalize")
        .color(field = "gender", `type` = "nominal", scale = json"""{"range": ["#675193", "#ca8861"]}""")
        .transform(
          json"""{"filter": "datum.year == 2000"}""",
          json"""{"calculate": "datum.sex == 2 ? 'Female' : 'Male'", "as": "gender"}"""
        )
      1 mustEqual 1
    }
    "Gantt Chart" in {
      val gantt = VegaLite.view("Gantt Chart")
        .mark("bar")
        .y(field = "task", `type` = "ordinal")
        .x(field = "start", `type` = "quantitative")
        .x2(field = "end")
        .data(jsan"""
          [
            {"task": "A", "start": 1, "end": 3},
            {"task": "B", "start": 3, "end": 8},
            {"task": "C", "start": 8, "end": 10}
          ]"""
        )
      1 mustEqual 1
    }
    "A Bar Chart Encoding Color Names in the Data" in {
      val colorBar = VegaLite.view("A Bar Chart Encoding Color Names in the Data")
        .mark("bar")
        .x(field = "color", `type` = "nominal")
        .y(field = "b", `type` = "quantitative")
        .color(field = "color", `type` = "nominal", scale = JsNull)
        .data(jsan"""
          [
            {"color": "red", "b": 28},
            {"color": "green", "b": 55},
            {"color": "blue", "b": 43}
          ]"""
        )
      1 mustEqual 1
    }
    "Histogram" in {
      val histogram = VegaLite.view("Histogram")
        .mark("bar")
        .data("https://vega.github.io/vega-lite/examples/data/movies.json")
        .x(field = "IMDB Rating", `type` = "quantitative", bin = true)
        .y(field = null, `type` = "quantitative", aggregate = "count")
      1 mustEqual 1
    }
    "Relative Frequency Histogram" in {
      val freqHistogram = VegaLite.view("Relative Frequency Histogram")
        .mark(JsObject("type" -> "bar", "tooltip" -> true))
        .data("https://vega.github.io/vega-lite/examples/data/cars.json")
        .x(field = "bin_Horsepwoer", `type` = "quantitative", bin = "binned", title = "Horsepower")
        .x2(field = "bin_Horsepwoer_end")
        .y(field = "PercentOfTotal", `type` = "quantitative", title = "Relative Frequency", axis = json"""{"format": ".1~%"}""")
        .transform(jsan"""
          [
            {
              "bin": true, "field": "Horsepower", "as": "bin_Horsepwoer"
            },
            {
              "aggregate": [{"op": "count", "as": "Count"}],
              "groupby": ["bin_Horsepwoer", "bin_Horsepwoer_end"]
            },
            {
              "joinaggregate": [{"op": "sum", "field": "Count", "as": "TotalCount"}]
            },
            {
              "calculate": "datum.Count/datum.TotalCount", "as": "PercentOfTotal"
            }
          ]""")
      1 mustEqual 1
    }
    "2D Histogram Heatmap" in {
      val histHeatmap = VegaLite.view()
        .mark("rect")
        .width(300)
        .height(200)
        .config(JsObject("view" -> json"""{"stroke": "transparent"}"""))
        .data("https://vega.github.io/vega-lite/examples/data/movies.json")
        .x(field = "IMDB Rating", `type` = "quantitative", bin = json"""{"maxbins":60}""", title = "IMDB Rating")
        .y(field = "Rotten Tomatoes Rating", `type` = "quantitative", bin = json"""{"maxbins":40}""")
        .color(field = null, `type` = "quantitative", aggregate = "count")
        .transform(json"""{
          "filter": {
            "and": [
              {"field": "IMDB Rating", "valid": true},
              {"field": "Rotten Tomatoes Rating", "valid": true}
            ]
          }}"""
        )
      1 mustEqual 1
    }
    "Density Plot" in {
      val density = VegaLite.view("Density Plot")
        .mark("area")
        .width(400)
        .height(100)
        .data("https://vega.github.io/vega-lite/examples/data/movies.json")
        .x(field = "value", `type` = "quantitative", title = "IMDB Rating")
        .y(field = "density", `type` = "quantitative")
        .transform(json"""{
            "density": "IMDB Rating",
            "bandwidth": 0.3
          }"""
        )
      1 mustEqual 1
    }
    "Cumulative Frequency Distribution" in {
      val cdf = VegaLite.view("Cumulative Frequency Distribution")
        .mark("area")
        .data("https://vega.github.io/vega-lite/examples/data/movies.json")
        .x(field = "IMDB Rating", `type` = "quantitative")
        .y(field = "Cumulative Count", `type` = "quantitative")
        .transform(jsan"""[
          {
            "aggregate": [{"op":  "count", "field": "*", "as": "count"}],
            "groupby": ["IMDB Rating"]
          },
          {
            "sort": [{"field": "IMDB Rating"}],
            "window": [{"op": "sum", "field": "count", "as": "Cumulative Count"}],
            "frame": [null, 0]
          }]"""
        )
      1 mustEqual 1
    }
    "Scatter Plot" in {
      val scatter = VegaLite.view("Scatter Plot")
        .mark("point")
        .data("https://vega.github.io/vega-lite/examples/data/cars.json")
        .x(field = "Horsepower", `type` = "quantitative")
        .y(field = "Miles_per_Gallon", `type` = "quantitative")
        .color(field = "Origin", `type` = "nominal")
        .shape(field = "Origin", `type` = "nominal")
      1 mustEqual 1
    }
    "Bubble Plot" in {
      val bubble = VegaLite.view("Bubble Plot")
        .data("https://vega.github.io/vega-lite/examples/data/cars.json")
        .mark("point")
        .x(field = "Horsepower", `type` = "quantitative")
        .y(field = "Miles_per_Gallon", `type` = "quantitative")
        .size(field = "Acceleration", `type` = "quantitative")
      1 mustEqual 1
    }
    "Natural Disasters" in {
      val disaster = VegaLite.view("Natural Disasters")
        .mark(JsObject(
          "type" -> "circle",
          "opacity" -> 0.8,
          "stroke" -> "black",
          "strokeWidth" -> 1
        ))
        .width(600)
        .height(400)
        .data("https://vega.github.io/vega-lite/examples/data/disasters.csv")
        .x(field = "Year", `type` = "ordinal", axis = json"""{"labelAngle": 90, "labelOverlap": "greedy"}""")
        .y(field = "Entity", `type` = "nominal", title = null)
        .size(field = "Deaths", `type` = "quantitative",
          legend = json"""{"title": "Annual Global Deaths", "clipHeight": 30}""",
          scale = json"""{"range": [0, 5000]}"""
        )
        .color(field = "Entity", `type` = "nominal", legend = JsNull)
        .transform(json"""{"filter": "datum.Entity !== 'All natural disasters'"}""")
      1 mustEqual 1
    }
    "Text Plot" in {
      val textPlot = VegaLite.view("Text Plot")
        .mark("text")
        .data("https://vega.github.io/vega-lite/examples/data/cars.json")
        .x(field = "Horsepower", `type` = "quantitative")
        .y(field = "Miles_per_Gallon", `type` = "quantitative")
        .color(field = "Brand", `type` = "nominal")
        .text(field = "Brand", `type` = "nominal")
        .transform(json"""{
            "calculate": "split(datum.Name, ' ')[0]",
            "as": "Brand"
          }"""
        )
      1 mustEqual 1
    }
    "Line Chart" in {
      val line = VegaLite.view("Line Chart")
        .mark("line")
        .data("https://vega.github.io/vega-lite/examples/data/stocks.csv")
        .x(field = "date", `type` = "temporal")
        .y(field = "price", `type` = "quantitative")
        .transform(json"""{"filter": "datum.symbol==='GOOG'"}""")
      1 mustEqual 1
    }
    "Line Chart with Point Markers" in {
      val pointLine = VegaLite.view("Line Chart with Point Markers")
        .mark(JsObject("type" -> "line", "point" -> true))
        .data("https://vega.github.io/vega-lite/examples/data/stocks.csv")
        .x(field = "date", `type` = "temporal", timeUnit = "year")
        .y(field = "price", `type` = "quantitative", aggregate = "mean")
        .color(field = "symbol", `type` = "nominal")
      1 mustEqual 1
    }
    "Line Chart with Confidence Interval Band" in {
      val confidenceInterval = VegaLite.layer(
          VegaLite.view()
            .mark(JsObject("type" -> "errorband", "extent" -> "ci"))
            .y(field = "Miles_per_Gallon", `type` = "quantitative", title = "Mean of Miles per Gallon (95% CIs)"),
          VegaLite.view()
            .mark("line")
            .y(field = "Miles_per_Gallon", `type` = "quantitative", aggregate = "mean")
        )
        .title("Line Chart with Confidence Interval Band")
        .data("https://vega.github.io/vega-lite/examples/data/cars.json")
        .x(field = "Year", `type` = "temporal", timeUnit = "year")

      1 mustEqual 1
    }
    "Rolling Averages over Raw Values" in {
      val rollingAverages = VegaLite.layer(
          VegaLite.view()
            .mark(JsObject("type" -> "point", "opacity" -> 0.3))
            .x(field = "date", `type` = "temporal", title = "Date")
            .y(field = "temp_max", `type` = "quantitative", title = "Max Temperature"),
          VegaLite.view()
            .mark(JsObject("color" -> "red", "size" -> 3, "type" -> "line"))
            .x(field = "date", `type` = "temporal")
            .y(field = "rolling_mean", `type` = "quantitative")
        )
        .title("Rolling Averages over Raw Values")
        .data("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv")
        .width(400)
        .height(300)
        .transform(json"""{
            "frame": [-15, 15],
            "window": [
              {
                "field": "temp_max",
                "op": "mean",
                "as": "rolling_mean"
              }
            ]
          }"""
        )
      1 mustEqual 1
    }
    "Area Chart with Overlaying Lines and Point Markers" in {
      val area = VegaLite.view("Area Chart with Overlaying Lines and Point Markers")
        .mark(JsObject("type" -> "area", "line" -> true, "point" -> true))
        .data("https://vega.github.io/vega-lite/examples/data/stocks.csv")
        .x(field = "date", `type` = "temporal")
        .y(field = "price", `type` = "quantitative")
        .transform(json"""{"filter": "datum.symbol==='GOOG'"}""")
      area.show(true)
      1 mustEqual 1
    }
    "Annual Weather Heatmap" in {
      val heatmap = VegaLite.view("2010 Daily Max Temperature (F) in Seattle, WA")
        .mark("rect")
        .data("https://vega.github.io/vega-lite/examples/data/seattle-weather.csv")
        .x(field = "date", `type` = "ordinal", timeUnit = "date", title = "Day", axis = json"""{"labelAngle": 0, "format": "%e"}""")
        .y(field = "date", `type` = "ordinal", timeUnit = "month", title = "Month")
        .color(field = "temp_max", `type` = "quantitative", aggregate = "max", legend = json"""{"title": null}""")
        .config(json"""{
          "view": {
            "strokeWidth": 0,
              "step": 13
            },
            "axis": {
              "domain": false
            }
          }"""
        )
      1 mustEqual 1
    }
    "Donut Chart" in {
      val donut = VegaLite.view("Donut Chart")
        .mark(JsObject("type" -> "arc", "innerRadius" -> 50))
        .view(JsObject("stroke" -> JsNull))
        .theta(field = "value", `type` = "quantitative")
        .color(field = "category", `type` = "nominal")
        .data(jsan"""[
          {"category": 1, "value": 4},
          {"category": 2, "value": 6},
          {"category": 3, "value": 10},
          {"category": 4, "value": 3},
          {"category": 5, "value": 7},
          {"category": 6, "value": 8}
        ]"""
      )
      1 mustEqual 1
    }
    "Radial Chart" in {
      val radial = VegaLite.layer(
          VegaLite.view().
            mark(JsObject("type" -> "arc", "innerRadius" -> 20, "stroke" -> "#fff")),

          VegaLite.view().mark(JsObject("type" -> "text", "radiusOffset" -> 10)).
            text(field = "data", `type` = "quantitative")
        )
        .title("Radial Chart")
        .mark(JsObject("type" -> "arc", "innerRadius" -> 50))
        .data(jsan"""[12, 23, 47, 6, 52, 19]""")
        .view(JsObject("stroke" -> JsNull))
        .theta(field = "data", `type` = "quantitative", stack = true)
        .radius(field = "data", `type` = "quantitative", scale = json"""{"type": "sqrt", "zero": true, "range": [20, 100]}""")
        .color(field = "data", `type` = "nominal", legend = JsNull)
      1 mustEqual 1
    }
    "Box Plot" in {
      val boxplot = VegaLite.view("Box Plot")
        .mark(JsObject("type" -> "boxplot", "extent" -> "min-max"))
        .view(JsObject("stroke" -> JsNull))
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "age", `type` = "ordinal")
        .y(field = "people", `type` = "quantitative", title = "population")
      1 mustEqual 1
    }
    "Vertical Concatenation" in {
      val donut = VegaLite.view("Donut Chart")
        .mark(JsObject("type" -> "arc", "innerRadius" -> 50))
        .view(JsObject("stroke" -> JsNull))
        .theta(field = "value", `type` = "quantitative")
        .color(field = "category", `type` = "nominal")
        .data(jsan"""[
          {"category": 1, "value": 4},
          {"category": 2, "value": 6},
          {"category": 3, "value": 10},
          {"category": 4, "value": 3},
          {"category": 5, "value": 7},
          {"category": 6, "value": 8}
        ]"""
        )

      val boxplot = VegaLite.view("Box Plot")
        .mark(JsObject("type" -> "boxplot", "extent" -> "min-max"))
        .view(JsObject("stroke" -> JsNull))
        .data("https://vega.github.io/vega-lite/examples/data/population.json")
        .x(field = "age", `type` = "ordinal")
        .y(field = "people", `type` = "quantitative", title = "population")

      val concat = VegaLite.vconcat(donut, boxplot).title("Vertical Concatenation")
      concat.show(true)
      1 mustEqual 1
    }
    "Scatter Plot Matrix (SPLOM)" in {
      val iris = smile.read.arff(smile.io.Paths.getTestData("weka/iris.arff"))
      val splom = VegaLite.splom(iris, "class").title("Scatter Plot Matrix")
      splom.show(true)
      1 mustEqual 1
    }
    "Choropleth of Unemployment Rate per County" in {
      val geo = VegaLite.view("Choropleth of Unemployment Rate per County")
        .mark("geoshape")
        .width(500)
        .height(300)
        .data(
          "https://vega.github.io/vega-lite/examples/data/us-10m.json",
          JsObject("type" -> "topojson", "feature" -> "counties")
        )
        .color(field = "rate", `type` = "quantitative")
        .projection(json"""{"type": "albersUsa"}""")
        .transform(json"""{
          "lookup": "id",
          "from": {
            "data": {
              "url": "https://vega.github.io/vega-lite/examples/data/unemployment.tsv"
            },
            "key": "id",
            "fields": ["rate"]
          }}"""
        )
      geo.show(true)
      1 mustEqual 1
    }
  }
}
