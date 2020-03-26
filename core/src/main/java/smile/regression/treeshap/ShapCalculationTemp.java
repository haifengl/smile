package smile.regression.treeshap;

import java.util.ArrayList;
import java.util.List;

import smile.util.MutableInt;

/** 
 * recyclable class as a register during shap calculation to avoid mass instantiation in memory 

 * <pre>
 * https://github.com/slundberg/shap/blob/master/shap/explainers/pytree.py
 * </pre>
 * 
 * @author ray
 */
public class ShapCalculationTemp {

  public int s;
  public List<MutableInt> feature_indexes;
  public List<MutableDouble> zero_fractions;
  public List<MutableDouble> one_fractions;
  public List<MutableDouble> pweights;

  ShapCalculationTemp(int s) {
    this.s = s;
    this.feature_indexes = new ArrayList<MutableInt>(s);
    this.zero_fractions = new ArrayList<MutableDouble>(s);
    this.one_fractions = new ArrayList<MutableDouble>(s);
    this.pweights = new ArrayList<MutableDouble>(s);

    returnTemp(true);
  }

  void returnTemp(boolean init) {
    if (init) {
      for (int i = 0; i < s; i++) {
        feature_indexes.add(new MutableInt(Integer.MIN_VALUE));
        zero_fractions.add(new MutableDouble(Double.MIN_VALUE));
        one_fractions.add(new MutableDouble(Double.MIN_VALUE));
        pweights.add(new MutableDouble(Double.MIN_VALUE));
      }
    } else {
      for (int i = 0; i < s; i++) {
        feature_indexes.get(i).value = Integer.MIN_VALUE;
        zero_fractions.get(i).value = Double.MIN_VALUE;
        one_fractions.get(i).value = Double.MIN_VALUE;
        pweights.get(i).value = Double.MIN_VALUE;
      }
    }
  }
  
  /**
   * as a convenient method to return a list only containing one register item
   * @param s size
   */
  static List<ShapCalculationTemp> createSingelItemList(int s){
	  List<ShapCalculationTemp> calcTemp = new ArrayList<ShapCalculationTemp>(1);
	  calcTemp.add(new ShapCalculationTemp(s));
	  return calcTemp;
  }
}
