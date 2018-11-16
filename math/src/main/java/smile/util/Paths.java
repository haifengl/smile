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
package smile.util;

import java.nio.file.Path;

/**
 * Static methods that return a Path by converting a path string or URI.
 *
 * @author Haifeng Li
 */
public class Paths {
    /** Utility classes should not have public constructors. */
    private Paths() {

    }

    private static String home = System.getProperty("smile.home", "shell/src/universal/");

    /** Get the file path of a test sample dataset. */
    public static Path getTestData(String... path) {
        return java.nio.file.Paths.get(home + "/data", path);
    }
}
