/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.plot.vega

import java.util.concurrent.{Callable, FutureTask}
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.web.WebView
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import com.sun.javafx.webkit.WebConsoleListener
import com.typesafe.scalalogging.LazyLogging
import smile.json.{JsNull, JsObject, JsUndefined}

/** A JavaFX WebView with plot canvas. */
case class Window(stage: Stage) {
  /** Closes the window programmatically. */
  def close: Unit = {
    Window.onUIThread { stage.close }
  }

  Platform.runLater { () => stage.showAndWait }
}

object Window extends LazyLogging {
  /** The number of created windows, as the default window title. */
  private val windowCount = new java.util.concurrent.atomic.AtomicInteger
  /** Creates a JFXPanel to avoid the error of 'Internal graphics not initialized yet' */
  private val panel = new JFXPanel

  // If this attribute is true, the JavaFX runtime will implicitly shutdown
  // when the last window is closed.
  Platform.setImplicitExit(false)

  // log error
  WebConsoleListener.setDefaultListener((webView: javafx.scene.web.WebView, message: String, lineNumber: Int, sourceId: String) => {
      if (message.contains("Error")) logger.error(s"${sourceId} (line ${lineNumber}): ${message}")
      else logger.info(s"${sourceId} (line ${lineNumber}): ${message}")
  })

  /** Creates a plot window/stage. */
  def apply(plot: JsObject): Window = onUIThread {
    if (plot.width == JsUndefined) plot.width = 800
    if (plot.height == JsUndefined) plot.height = 600

    val scene = sceneOf(plot)
    val stage = new Stage
    stage.setWidth(1200)
    stage.setHeight(800)
    stage.setScene(scene)

    val title = plot.title match {
      case JsUndefined | JsNull => "Smile Plot " + windowCount.addAndGet(1)
      case title => title.toString
    }
    stage.setTitle(title)

    Window(stage)
  }

  /** Returns the WebView scene of plot. */
  def sceneOf(plot: JsObject): Scene = onUIThread {
    val webView = new WebView
    val webEngine = webView.getEngine
    webEngine.loadContent(embed(plot))

    val root = new StackPane
    // Very large size so that StackPane tries to resize its children/WebView
    // to fit the content area.
    root.setPrefSize(5000, 5000)
    root.getChildren.add(webView)

    new Scene(root)
  }

  /** Runs a JavaFX operation on the UI thread. */
  private[vega] def onUIThread[T](op: => T): T = if (Platform.isFxApplicationThread) {
    op
  } else {
    val task = new FutureTask(new Callable[T] {
      override def call: T = op
    })

    Platform.runLater(task)
    task.get()
  }
}