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
