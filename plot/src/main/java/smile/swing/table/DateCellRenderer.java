/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.swing.table;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.swing.table.DefaultTableCellRenderer;

/**
 * Date cell renderer.
 *
 * @author Haifeng Li
 */
public class DateCellRenderer extends DefaultTableCellRenderer {
    /** Predefined date renderer in YYYY-MM-DD format. */
    public static final DateCellRenderer YYYYMMDD        = new DateCellRenderer("yyyy-MM-dd");
    /** Predefined date renderer in MM/dd/yy format. */
    public static final DateCellRenderer MMDDYY          = new DateCellRenderer("MM/dd/yy");
    /** Predefined datetime renderer in yyyy-MM-dd HH:mm:ss format. */
    public static final DateCellRenderer YYYYMMDD_HHMMSS = new DateCellRenderer("yyyy-MM-dd HH:mm:ss");
    /** Predefined datetime renderer in yyyy-MM-dd HH:mm format. */
    public static final DateCellRenderer YYYYMMDD_HHMM   = new DateCellRenderer("yyyy-MM-dd HH:mm");
    /** Predefined time renderer in HH:mm format. */
    public static final DateCellRenderer HHMM            = new DateCellRenderer("HH:mm");
    /** Predefined time renderer in HH:mm:ss format. */
    public static final DateCellRenderer HHMMSS          = new DateCellRenderer("HH:mm:ss");
    /** Predefined date renderer in ISO-8601 format yyyy-MM-dd'T'HH:mm:ss.SSSXXX. */
    public static final DateCellRenderer ISO8601         = new DateCellRenderer("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    /** The date format. */
    private final DateFormat dateFormat;

    /**
     * Constructor.
     * @param format the date regex format.
     */
    public DateCellRenderer(String format) {
        dateFormat = new SimpleDateFormat(format);
        setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);        
    }

    /**
     * Constructor.
     * @param format the date format.
     */
    public DateCellRenderer(DateFormat format) {
        this.dateFormat = format;
        setHorizontalAlignment(DefaultTableCellRenderer.RIGHT);        
    }

    @Override
    public void setValue(Object value) {
        setText((value == null) ? "" : dateFormat.format(value));
    }
}
