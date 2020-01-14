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

package smile.data;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serialization help functions.
 *
 * @author Haifeng Li
 */
public interface Serialize {
    /**
     * Writes an object to a temp file and returns the path of temp file.
     * The temp file will be deleted when the VM exits.
     */
    static Path write(Object o) throws IOException {
        Path temp = Files.createTempFile("smile-test-", ".tmp");
        OutputStream file = Files.newOutputStream(temp);
        ObjectOutputStream out = new ObjectOutputStream(file);
        out.writeObject(o);
        out.close();
        file.close();
        temp.toFile().deleteOnExit();
        return temp;
    }

    /** Reads an object from a (temp) file. */
    static Object read(Path path) throws IOException, ClassNotFoundException {
        InputStream file = Files.newInputStream(path);
        ObjectInputStream in = new ObjectInputStream(file);
        Object o = in.readObject();
        in.close();
        file.close();
        return o;
    }
}
