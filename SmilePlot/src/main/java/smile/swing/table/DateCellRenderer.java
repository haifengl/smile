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

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.swing.table.DefaultTableCellRenderer;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class DateCellRenderer extends DefaultTableCellRenderer {
    public static final DateCellRenderer YYYYMMDD        = new DateCellRenderer("yyyy-MM-dd");
    public static final DateCellRenderer MMDDYY          = new DateCellRenderer("MM/dd/yy");
    public static final DateCellRenderer YYYYMMDD_HHMMSS = new DateCellRenderer("yyyy-MM-dd HH:mm:ss");
    public static final DateCellRenderer YYYYMMDD_HHMM   = new DateCellRenderer("yyyy-MM-dd HH:mm");
    public static final DateCellRenderer HHMM            = new DateCellRenderer("HH:mm");
    public static final DateCellRenderer HHMMSS          = new DateCellRenderer("HH:mm:ss");
    public static final DateCellRenderer ISO8601         = new DateCellRenderer("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private DateFormat dateFormat;

    public DateCellRenderer(String format) {
        dateFormat = new SimpleDateFormat(format);
        setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);        
    }

    public DateCellRenderer(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
        setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);        
    }

    @Override
    public void setValue(Object value) {
        setText((value == null) ? "" : dateFormat.format(value));
    }
}
