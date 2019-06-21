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
