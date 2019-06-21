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

import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.JOptionPane;

/**
 * A printer controller object.
 * 
 * @author Haifeng Li
 */
public class Printer {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Printer.class);

    /**
     * Printer attributes.
     */
    private PrintRequestAttributeSet printAttributes = new HashPrintRequestAttributeSet();
    /**
     * Printer job.
     */
    private PrinterJob printer = null;
    /**
     * Singleton instance. An application should have only one printer instance
     * so that printer settings can be shared by swing components.
     */
    private static Printer singleton = null;
    
    /**
     * Private constructor.
     */
    private Printer() {
        printer = PrinterJob.getPrinterJob();
    }
    
    /**
     * Returns the printer controller object.
     * @return the printer controller object 
     */
    public static Printer getPrinter() {
        if (singleton == null) {
            singleton = new Printer();
        }
        
        return singleton;
    }
    
    /**
     * Prints a document that implements Printable interface.
     * @param painter the Printable that renders each page of the document.
     */
    public void print(Printable painter) {
        printer.setPrintable(painter);
        if (printer.printDialog(printAttributes)) {
            try {
                printer.print(printAttributes);
            } catch (PrinterException ex) {
                logger.error("Failed to print", ex);
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
