/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.swing;

import javax.swing.Action;
import javax.swing.JButton;

/**
 * Action initialized JButton. If the button has an icon, the text label
 * won't show on button.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class Button extends JButton {
    /**
     * Constructor.
     * @param action the Action used to specify the new button.
     */
    public Button(Action action) {
        super(action);
        
        if (getIcon() != null) {
            String desc = (String) action.getValue(Action.SHORT_DESCRIPTION);
            if (desc == null) {
                desc = getText();
            }
            
            setToolTipText(desc);
            setText(null);
        }        
    }
}
