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
 ******************************************************************************/

package smile.util;

/**
 * A mutable double wrapper. It is efficient if you need save resource for auto-boxing.
 *
 * @author ray
 */
public class MutableDouble {
  public double value = Double.MIN_VALUE;

  /** constructor with given initialization amount*/
  public MutableDouble(double value) {
    this.value = value;
  }
  
  /** simple constructor with default initialization amount {@link Double#MIN_VALUE} */
  public MutableDouble() {
	  
  }
  
  /** Increment by given amount. */
  public double increment(double x) {
      return (value += x);
  }

  /** Decrement by given amount. */
  public double decrement(double x) {
      return (value -= x);
  }
}
