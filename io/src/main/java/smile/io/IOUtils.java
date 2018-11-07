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
package smile.data.parser;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple IO stream manipulation utilities.
 * 
 * @author Haifeng Li
 */
public class IOUtils {
    /** Utility classes should not have public constructors. */
    private IOUtils() {

    }

    private static String home = System.getProperty("smile.home", "shell/src/universal/");

    /** Get the file path of sample dataset. */
    public static String getTestDataPath(String path) {
        return home + "/data/" + path;
    }

    /** Get the file object of sample dataset. */
    public static File getTestDataFile(String path) {
        return new java.io.File(getTestDataPath(path));
    }

    /** Get the reader of sample datasets. */
    public static BufferedReader getTestDataReader(String path) throws FileNotFoundException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(getTestDataFile(path))));
    }

    /**
     * Returns the lines of an <code>InputStream</code> as a list of Strings.
     *
     * @param input  the <code>InputStream</code> to read from.
     * @return the list of lines.
     */
    public static List<String> readLines(InputStream input) throws IOException {
        final InputStreamReader reader = new InputStreamReader(input);
        return readLines(reader);
    }
    
    /**
     * Returns the lines of an <code>InputStream</code> as a list of Strings.
     *
     * @param input  the <code>InputStream</code> to read from.
     * @param charset a charset.
     * @return the list of lines.
     */
    public static List<String> readLines(InputStream input, Charset charset) throws IOException {
        final InputStreamReader reader = new InputStreamReader(input, charset);
        return readLines(reader);
    }
    
    /**
     * Returns the lines of a <code>Reader</code> as a list of Strings.
     *
     * @param input  the <code>Reader</code> to read from.
     * @return the list of lines.
     */
    public static List<String> readLines(Reader input) throws IOException {
        BufferedReader reader = input instanceof BufferedReader ? (BufferedReader) input : new BufferedReader(input);
        List<String> list = new ArrayList<>();
        String line = reader.readLine();
        while (line != null) {
            list.add(line);
            line = reader.readLine();
        }
        reader.close();
        return list;
    }
}
