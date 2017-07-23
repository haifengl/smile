package smile.classification;

import smile.data.Attribute;

/**
 * Abstract soft classifier trainer.
 * 
 * @param <T> the type of input object.
 * 
 * @author Noam Segev
 */
public abstract class SoftClassifierTrainer <T> extends ClassifierTrainer<T> {
	
	/**
     * Constructor.
     */
    public SoftClassifierTrainer() {
        
    }
	
	/**
     * Constructor.
     * @param attributes the attributes of independent variable.
     */
    public SoftClassifierTrainer(Attribute[] attributes) {
		super(attributes);
	}

	/**
     * Learns a soft classifier with given training data.
     * 
     * @param x the training instances.
     * @param y the training labels.
     * @return a trained classifier.
     */
    public abstract SoftClassifier<T> train(T[] x, int[] y);

}
