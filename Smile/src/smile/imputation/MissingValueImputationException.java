/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.imputation;

/**
 * Exception of missing value imputation.
 * 
 * @author Haifeng Li
 */
public class MissingValueImputationException extends Exception {
    private static final long serialVersionUID = -4517157687017284522L;
    
    /**
     * Constructor.
     */
    public MissingValueImputationException() {

    }

    /**
     * Constructor.
     */
    public  MissingValueImputationException(String message) {
        super(message);
    }
}
