/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.swing.table;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Integer array renderer in JTable.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class IntegerArrayCellRenderer extends DefaultTableCellRenderer {

    /**
     * Constructor.
     */
    public IntegerArrayCellRenderer() {
    }
    
    @Override
    public void setValue(Object value) {
        if (value == null) {
            setText("[]");
            return;
        }
        
        int[] data = (int[]) value;
        
        StringBuilder builder = new StringBuilder();
        if (data.length > 0) {
            builder.append("[").append(data[0]);
        }
        
        for (int i = 1; i < data.length; i++) {
            builder.append(", ").append(data[i]);
        }
        builder.append("]");
        setText(builder.toString());
    }
}