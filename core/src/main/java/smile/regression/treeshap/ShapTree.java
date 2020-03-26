package smile.regression.treeshap;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import smile.base.cart.InternalNode;
import smile.base.cart.LeafNode;
import smile.base.cart.Node;
import smile.base.cart.RegressionNode;
import smile.regression.RegressionTree;

/** 
 * structure for shap value calculation. implementation reference:
 * 
 * <pre>
 * https://github.com/slundberg/shap/blob/master/shap/explainers/pytree.py
 * </pre>
 * 
 * @author ray
 */
public class ShapTree {
	
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ShapTree.class);

  public boolean normalize = false;
  public int[] children_left;
  public int[] children_right;
  public int[] children_default;
  public int[] features;
  public Node[] thresholds;
  public double[] values;
  public double[] node_sample_weight; // equals number of samples for a node divided by total number of samples
  public int max_depth;

  public ShapTree(RegressionTree tree) {
    this(tree, false);
  }

  public ShapTree(RegressionTree tree, boolean normalize) {
    this.normalize = normalize;

    List<Integer> childrenLefts = new ArrayList<Integer>();
    List<Integer> childrenRights = new ArrayList<Integer>();
    List<Integer> splitFeatures = new ArrayList<Integer>();
    List<Node> thresholds = new ArrayList<Node>();
    List<Double> values = new ArrayList<Double>();
    List<Double> sampleWeights = new ArrayList<Double>();
    int totalSamples = tree.root().size();
    //
    // we convert smile tree structure to sth favored by shap calculation
    //
    Queue<Node> q = new ConcurrentLinkedQueue<Node>();
    int idx = 0;
    int nodeNum = 0;
    q.add(tree.root());
    Node n = q.poll();

    while (n != null) {
      sampleWeights.add(idx, (double) n.size() / (double) totalSamples);
      values.add(idx, subtreeValues(n));
      thresholds.add(n);

      if (n instanceof InternalNode) {
        splitFeatures.add(idx, ((InternalNode) n).feature());
        q.add(((InternalNode) n).trueChild());
        childrenLefts.add(idx, (++nodeNum));
        q.add(((InternalNode) n).falseChild());
        childrenRights.add(idx, (++nodeNum));
      } else if (n instanceof LeafNode) {
        splitFeatures.add(idx, -1);
        childrenLefts.add(idx, -1);
        childrenRights.add(idx, -1);
      } else {
    	String err = "error node type for tree structure conversion: " + n.getClass() + ":" + n.toString();
    	logger.error(err);
        throw new RuntimeException(err);
      }

      n = q.poll();
      idx++;
    }

    this.children_left = childrenLefts.stream().mapToInt(Integer::intValue).toArray();
    this.children_right = childrenRights.stream().mapToInt(Integer::intValue).toArray();
    this.children_default = this.children_left; // missing values...
    this.features = splitFeatures.stream().mapToInt(Integer::intValue).toArray();
    this.thresholds = thresholds.toArray(new Node[thresholds.size()]);
    this.node_sample_weight = sampleWeights.stream().mapToDouble(Double::doubleValue).toArray();

    if (this.normalize) {
      double valueSum = values.stream().mapToDouble(Double::doubleValue).sum();
      this.values = values.stream().mapToDouble(num -> num / valueSum).toArray();
    } else {
      this.values = values.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // we recompute the expectations to make sure they follow the SHAP logic
    this.max_depth = computeExpectations(this.children_left, this.children_right, this.node_sample_weight, this.values, 0, 0);
    // tracking progress for shap tree construction 
    System.out.print(".");
  }

  /** calculate the constant prediction value of each node */
  private double subtreeValues(Node n) {
    double v = 0;
    if (n instanceof RegressionNode) {
      v = ((RegressionNode) n).output();
      assert (v != Double.NaN);
    } else if (n instanceof InternalNode) {
      Node lc = ((InternalNode) n).trueChild();
      Node rc = ((InternalNode) n).falseChild();
      v = (double) (lc.size() * subtreeValues(lc) + rc.size() * subtreeValues(rc)) / (double) (lc.size() + rc.size());
      assert (v != Double.NaN);
    } else {
      String err = "error node type for node value calculation: " + n.getClass() + ":" + n.toString();
      logger.error(err);
      throw new RuntimeException(err);
    }
    return v;
  }

  private int computeExpectations(
      int[] children_left,
      int[] children_right,
      double[] node_sample_weight,
      double[] values,
      int i,
      int depth) {
	  
    if (depth < 0) {
      depth = 0;
    }

    if (children_right[i] == -1) {
      return 0;
    } else {
      int li = children_left[i];
      int ri = children_right[i];
      int depth_left = computeExpectations(children_left, children_right, node_sample_weight, values, li, depth + 1);
      int depth_right =
          computeExpectations(children_left, children_right, node_sample_weight, values, ri, depth + 1);
      double left_weight = node_sample_weight[li];
      double right_weight = node_sample_weight[ri];
      double v = (left_weight * values[li] + right_weight * values[ri]) / (left_weight + right_weight);
      values[i] = v;
      return Math.max(depth_left, depth_right) + 1;
    }
  }
  
}
