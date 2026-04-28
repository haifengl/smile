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
package smile.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/**
 * A hardened {@link ObjectInputStream} that enforces an {@link ObjectInputFilter}
 * before any deserialization callbacks execute.
 *
 * <p>By default, the filter accepts only classes whose binary names begin with
 * the {@code smile.*} package hierarchy plus a carefully curated set of
 * standard JDK types (primitives, arrays, {@code java.lang}, {@code java.util},
 * {@code java.math}, {@code java.time}).  Everything else is rejected with
 * {@link InvalidClassException} before the object graph is materialized,
 * preventing gadget-chain exploits.</p>
 *
 * <p>Callers that genuinely need to deserialize additional trusted classes
 * should supply a custom {@link ObjectInputFilter} via the two-argument
 * constructor. That filter is composed with the built-in allow-list using
 * {@link ObjectInputFilter#merge(ObjectInputFilter, ObjectInputFilter)}, so
 * the caller's filter is consulted first and the built-in deny-all fallback
 * still applies for anything the caller does not explicitly permit.</p>
 *
 * @author Haifeng Li
 */
class SmileObjectInputStream extends ObjectInputStream {

    /**
     * The default allow-list pattern accepted by every instance.
     *
     * <ul>
     *   <li>{@code smile.**}            – all SMILE classes</li>
     *   <li>{@code java.lang.**}        – String, Number, Enum, etc.</li>
     *   <li>{@code java.util.**}        – Collection, Map, Optional, …</li>
     *   <li>{@code java.math.**}        – BigInteger, BigDecimal</li>
     *   <li>{@code java.time.**}        – LocalDate, Instant, …</li>
     *   <li>{@code [*}                  – any array type</li>
     *   <li>{@code !*}                  – reject everything else</li>
     * </ul>
     */
    static final ObjectInputFilter DEFAULT_FILTER =
            ObjectInputFilter.Config.createFilter(
                    "smile.**" +
                    ";java.lang.**" +
                    ";java.util.**" +
                    ";java.math.**" +
                    ";java.time.**" +
                    ";[*" +
                    ";!*");

    /**
     * Creates a {@code SmileObjectInputStream} with the default allow-list filter.
     *
     * @param in the underlying input stream.
     * @throws IOException if an I/O error occurs while reading the stream header.
     */
    SmileObjectInputStream(InputStream in) throws IOException {
        this(in, DEFAULT_FILTER);
    }

    /**
     * Creates a {@code SmileObjectInputStream} with a caller-supplied filter
     * merged on top of the built-in allow-list.
     *
     * <p>The {@code extraFilter} is consulted first.  If it returns
     * {@link ObjectInputFilter.Status#ALLOWED} the class is accepted
     * immediately without consulting the built-in allow-list, enabling callers
     * to extend the trusted set.  If {@code extraFilter} returns
     * {@link ObjectInputFilter.Status#REJECTED} the class is blocked
     * immediately.  If it returns {@link ObjectInputFilter.Status#UNDECIDED}
     * the decision falls through to the built-in allow-list.</p>
     *
     * @param in          the underlying input stream.
     * @param extraFilter an additional {@link ObjectInputFilter} to merge.
     * @throws IOException if an I/O error occurs while reading the stream header.
     */
    SmileObjectInputStream(InputStream in, ObjectInputFilter extraFilter) throws IOException {
        super(in);
        ObjectInputFilter combined;
        if (extraFilter == null) {
            combined = DEFAULT_FILTER;
        } else {
            combined = filterInfo -> {
                ObjectInputFilter.Status status = extraFilter.checkInput(filterInfo);
                if (status != ObjectInputFilter.Status.UNDECIDED) {
                    return status;
                }
                return DEFAULT_FILTER.checkInput(filterInfo);
            };
        }
        setObjectInputFilter(combined);
    }
}

