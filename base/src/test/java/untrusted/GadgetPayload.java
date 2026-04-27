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
package untrusted;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simulated deserialization gadget used for security testing.
 *
 * <p>This class intentionally lives in the {@code untrusted} package, which is
 * outside the {@code smile.**} allow-list enforced by
 * {@link smile.io.SmileObjectInputStream}.  Its sole purpose is to verify that
 * the filter rejects classes from untrusted packages <em>before</em> any
 * {@code readObject} callback executes.</p>
 */
public final class GadgetPayload implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Set to {@code true} the moment {@link #readObject} is called.
     * Remains {@code false} when the class is properly filtered out.
     */
    public static final AtomicBoolean CALLBACK_FIRED = new AtomicBoolean(false);

    @Serial
    private void readObject(ObjectInputStream ois)
            throws IOException, ClassNotFoundException {
        CALLBACK_FIRED.set(true);
        ois.defaultReadObject();
    }
}

