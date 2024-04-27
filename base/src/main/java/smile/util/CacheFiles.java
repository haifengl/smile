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

package smile.util;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * Static methods that manage cache files.
 *
 * @author Haifeng Li
 */
public interface CacheFiles {
    /**
     * Returns the cache directory path.
     * @return the cache directory path.
     */
    static String dir() {
        String smile = File.separator + "smile";
        String path = System.getenv("SMILE_CACHE");
        if (path == null) {
            String os = System.getProperty("os.name");
            String home = System.getProperty("user.home");
            if (os.startsWith("Windows")) {
                String localAppData = System.getenv("LocalAppData");
                path = localAppData + smile;
            } else if (os.startsWith("Mac")) {
                path = home + "/Library/Caches" + smile;
            } else {
                // Linux or others
                path =  home + File.separator + ".cache" + smile;
            }
        }
        return path;
    }

    /**
     * Downloads a file and save to the cache directory.
     * @param url the url of online file.
     * @return the path to the cache file.
     * @throws IOException if fail to download the file.
     * @throws URISyntaxException if url is invalid.
     */
    static Path download(String url) throws IOException, URISyntaxException {
        return download(url, false);
    }

    /**
     * Downloads a file and save to the cache directory.
     * @param url the url of online file.
     * @param force flag indicating if download even when cache file exists.
     * @return the path to the cache file.
     * @throws IOException if fail to download the file.
     * @throws URISyntaxException if url is invalid.
     */
    static Path download(String url, boolean force) throws IOException, URISyntaxException {
        URI uri = new URI(url);
        Path path = Paths.get(dir(), uri.getPath());
        File file = path.toFile();
        if (force || !(file.exists() && !file.isDirectory())) {
            if (file.getParentFile().mkdirs()) {
                Files.copy(
                        uri.toURL().openStream(),
                        path,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return path;
    }

    /**
     * Cleans up the cache directory.
     * @throws IOException if fail to delete the cache files.
     */
    static void clean() throws IOException {
        try (var files = Files.walk(Paths.get(dir()))) {
            files.sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        }
    }
}
