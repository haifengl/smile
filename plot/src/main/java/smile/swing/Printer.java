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

package smile.swing;

import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A printer controller object.
 * 
 * @author Haifeng Li
 */
public class Printer {
    private static final Logger logger = LoggerFactory.getLogger(Printer.class);

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
