/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model

import java.util.Properties
import smile.data.Tuple
import smile.data.formula.Formula
import smile.data.`type`.StructType
import spray.json._

/**
  * The machine learning model for inference.
  */
trait Model {
  /**
    * Applies the model.
    * @param x the input tuple.
    * @param options the inference options.
    * @return the prediction.
    */
  def apply(x: Option[Tuple], options: Properties): JsValue = {
    x.map(predict(_, options)).getOrElse(JsString("Invalid instance"))
  }

  /**
    * Applies the model.
    * @param x the input tuple.
    * @param options the inference options.
    * @return the prediction.
    */
  def predict(x: Tuple, options: Properties): JsValue
}

/**
  * The machine learning model applicable on a data frame.
  */
trait SmileModel extends Model {
  /** The algorithm name. */
  val algorithm: String
  /** The schema of input data (without response variable). */
  val schema: StructType
  /** The model formula. */
  val formula: Formula
}
