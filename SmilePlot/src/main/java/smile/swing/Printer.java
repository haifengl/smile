/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

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
                System.err.println(ex);
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
