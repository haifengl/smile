/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.data;

/**
 * A dataset of fixed number of attributes. All attribute values are stored as
 * double even if the attribute may be nominal, ordinal, string, or date.
 * The dataset is stored row-wise internally, which is fast for frequently
 * accessing instances of dataset.
 *
 * @author Haifeng Li
 */
public class AttributeDataset extends Dataset<double[]> {

    /**
     * The list of attributes.
     */
    private Attribute[] attributes;

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param attributes the list of attributes in this dataset.
     */
    public AttributeDataset(String name, Attribute[] attributes) {
        super(name);
        this.attributes = attributes;
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param attributes the list of attributes in this dataset.
     * @param response the attribute of response variable.
     */
    public AttributeDataset(String name, Attribute[] attributes, Attribute response) {
        super(name, response);
        this.attributes = attributes;
    }

    /**
     * Returns the list of attributes in this dataset.
     */
    public Attribute[] attributes() {
        return attributes;
    }
}
