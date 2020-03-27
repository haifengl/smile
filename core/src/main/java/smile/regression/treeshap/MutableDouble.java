package smile.regression.treeshap;

/**
 * A mutable double wrapper. It is efficient if you need save resource for auto-boxing.
 *
 * @author ray
 */
public class MutableDouble {
  public double value = Double.MIN_VALUE;

  public MutableDouble(double value) {
    this.value = value;
  }
}
