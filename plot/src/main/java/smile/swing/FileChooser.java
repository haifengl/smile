/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.swing;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;

/**
 * File chooser for with file/images preview. A simple file filter based on
 * file extensions is also provided.
 * 
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class FileChooser extends JFileChooser {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(FileChooser.class);

    /**
     * Shared file chooser. An application should have only one file chooser
     * so that it always points to the recent directory.
     */
    private static final FileChooser chooser = new FileChooser();

    /**
     * Constructor.
     */
    public FileChooser() {
        // Add the preview pane. 
        setAccessory(new FilePreview(this));
    }

    /**
     * Returns the shared file chooser instance. In general, an application
     * should have only one file chooser so that it always points to the recent
     * directory.
     */
    public static FileChooser getInstance() {
        return chooser;
    }
    
    static class FilePreview extends JComponent implements PropertyChangeListener {
        /**
         * The image of selected file.
         */
        BufferedImage img = null;
        /**
         * The selected file.
         */
        File file = null;
        /**
         * A buffer to hold file header.
         */
        final char[] buf = new char[1024];
        /**
         * Font for header text.
         */
        final Font font = (Font) UIManager.get("Label.font");
        /**
         * Font metrics.
         */
        final FontMetrics fm = getFontMetrics(font);
        /**
         * Color for header text.
         */
        final Color color = (Color) UIManager.get("Label.foreground");
        /**
         * Text file header.
         */
        String header = null;

        /**
         * Constructor.
         */
        public FilePreview(JFileChooser fc) {
            setPreferredSize(new Dimension(160, 160));
            fc.addPropertyChangeListener(this);
        }

        /**
         * Loads the image or the beginning part of a text file.
         */
        public void loadPreview() {
            if (file == null) {
                img = null;
                header = null;
                return;
            }

            if (!file.exists()) return;

            if (SimpleFileFilter.readableImageFilter.accept(file)) {
                try {
                    img = ImageIO.read(file);
                } catch (IOException ex) {
                    logger.error("Failed to read image {}", file, ex);
                }
            } else {
                try {
                    FileReader reader = new FileReader(file);
                    int len = reader.read(buf, 0, buf.length);
                    reader.close();
                    boolean binary = false;
                    for (int i = 0; i < len; i++) {
                        if (buf[i] < 0x1F) {
                            if (buf[i] != 0x09 && // tab
                                buf[i] != 0x0A && // line feed
                                buf[i] != 0x0C && // form feed
                                buf[i] != 0x0D) { // carriage return)
                                binary = true;
                                break;
                            }
                        }
                    }
                    
                    if (!binary) {
                        header = new String(buf, 0, len);
                    }                    
                } catch (IOException ex) {
                    logger.error("Failed to read file {}", file, ex);
                }
            }
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            boolean update = false;
            String prop = e.getPropertyName();
            if (JFileChooser.DIRECTORY_CHANGED_PROPERTY.equals(prop)) {
                // If the directory changed, don't show an image.
                file = null;
                update = true;
            } else if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(prop)) {
                // If a file became selected, find out which one. 
                file = (File) e.getNewValue();
                update = true;
            }

            // Update the preview accordingly. 
            if (update) {
                img = null;
                header = null;
                if (isShowing()) {
                    loadPreview();
                    repaint();
                }
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (img == null || header == null) {
                loadPreview();
            }

            if (img != null) {
                Graphics2D g2d = (Graphics2D) g;
                if (getWidth() < img.getWidth() || getHeight() < img.getHeight()) {
                    int x = (getWidth() - img.getWidth()) / 2;
                    int y = (getHeight() - img.getHeight()) / 2;
                    if (x < 5) x = 5;
                    if (y < 5) y = 5;
                    
                    int width = getWidth() - 2 * x;
                    int height = getHeight() - 2 * y;
                    
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.drawImage(img, x, y, width, height, null);
                } else {
                    int x = (getWidth() - img.getWidth()) / 2;
                    int y = (getHeight() - img.getHeight()) / 2;

                    g2d.drawImage(img, x, y, null);
                }
            }

            if (header != null) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setFont(font);
                g2d.setColor(color);
                
                // Watch the margins 
                Insets insets = getInsets();
                // Set the starting position to draw 
                int x = insets.left;
                int y = insets.top;
                if (x < 5) x = 5;
                if (y < 5) y = 5;

                int begin = 0;
                int end = header.indexOf('\n', begin);
                if (end == -1) end = header.length();
                while (begin >= 0 && begin < header.length()) {
                    // Move down to baseline 
                    y += fm.getAscent();                    
                    if (y >= getHeight()) {
                        break;
                    }
                    
                    g2d.drawString(header.substring(begin, end), x, y);
                    
                    begin = end + 1;
                    end = header.indexOf('\n', begin);
                    if (end == -1) end = header.length();
                    
                    // Move down to top of next line 
                    y += fm.getDescent() + fm.getLeading();
                }
            }
        }
    }

    /**
     * Returns the file name extension in lower case.
     * @param f a file.
     * @return the file name extension in lower case.
     */
    public static String getExtension(File f) {
        String ext = null;

        String s = f.getName();
        int i = s.lastIndexOf('.');
        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }

        return ext;
    }

    /**
     * A simple extension-based file filter. Accept all directories and known
     * file extensions. Extensions are typically found on Windows and Unix,
     * but not on Macinthosh. Case is ignored.
     */
    public static class SimpleFileFilter extends FileFilter {
        /**
         * Filter for readable image formats.
         */
        private static final SimpleFileFilter readableImageFilter = new SimpleFileFilter("Readable Images", ImageIO.getReaderFormatNames());
        /**
         * Filter for writable image formats.
         */
        private static final SimpleFileFilter writableImageFilter = new SimpleFileFilter("Writable Images", ImageIO.getWriterFormatNames());

        /**
         * The file extensions in lower case.
         */
        private final TreeSet<String> filters = new TreeSet<>();
        /**
         * The human-readable description of this filter.
         */
        private String description = null;
        /**
         * The human-readable description of this filter with the list of
         * file extensions.
         */
        private String fullDescription = null;

        /**
         * Creates a file filter that accepts the given file type.
         * <p>
         * Note that the "." before the extension is not needed. If
         * provided, it will be ignored.
         */
        public SimpleFileFilter(String description, String extension) {
            if (description != null) {
                setDescription(description);
            }

            if (extension != null) {
                addExtension(extension);
            }
        }

        /**
         * Creates a file filter from the given string array and description.
         * <p>
         * Note that the "." before the extension is not needed and will be ignored.
         */
        public SimpleFileFilter(String description, String... filters) {
            if (description != null) {
                setDescription(description);
            }

            for (String filter : filters) {
                // add filters one by one
                addExtension(filter);
            }
        }

        /**
         * Creates a file filter from the given string array and description.
         * <p>
         * Note that the "." before the extension is not needed and will be ignored.
         */
        public SimpleFileFilter(String description, Collection<String> filters) {
            if (description != null) {
                setDescription(description);
            }

            for (String extension : filters) {
                // add filters one by one
                addExtension(extension);
            }
        }

        /**
         * Returns the filter for readable images.
         */
        public static SimpleFileFilter getReadableImageFilter() {
            return readableImageFilter;
        }
        
        /**
         * Returns the filter for writable images.
         */
        public static SimpleFileFilter getWritableImageFIlter() {
            return writableImageFilter;            
        }
        
        @Override
        public boolean accept(File f) {
            if (f != null) {
                if (f.isDirectory()) {
                    return true;
                }

                String extension = getExtension(f);
                if (extension != null) {
                    return filters.contains(extension);
                }
            }

            return false;
        }

        /**
         * Adds a file type "dot" extension to filter against.
         * Note that the "." before the extension is not needed and will be ignored.
         */
        public void addExtension(String extension) {
            filters.add(extension.toLowerCase());
            fullDescription = null;
        }

        /**
         * Returns the human-readable description of this filter.
         */
        @Override
        public String getDescription() {
            if (fullDescription == null) {
                StringBuilder sb = new StringBuilder();
                if (description != null) {
                    sb.append(description);
                    sb.append(' ');
                }
                sb.append(Arrays.toString(filters.toArray(new String[0])));
                fullDescription = sb.toString();
            }

            return fullDescription;
        }

        /**
         * Sets the human-readable description of this filter.
         */
        public void setDescription(String description) {
            this.description = description;
            fullDescription = null;
        }
    }
}
