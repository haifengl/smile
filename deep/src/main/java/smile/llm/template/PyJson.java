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
package smile.llm.template;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Formats values the way Python does, so prompts rendered by a Java port of a Jinja chat template match
 * the reference byte for byte: {@code json.dumps(x, ensure_ascii=False)} (default separators
 * {@code ", "} and {@code ": "}, insertion order kept) and {@code repr(float)}.
 *
 * @author Haifeng Li
 */
final class PyJson {
    private PyJson() { }

    /**
     * Serializes a value like Python's {@code json.dumps(value, ensure_ascii=False)}.
     *
     * @param value {@code null}, {@link Boolean}, {@link Number}, {@link CharSequence},
     *              {@link Map} (string keys) or {@link Collection}.
     * @return the JSON text.
     */
    static String dumps(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v) {
        switch (v) {
            case null -> sb.append("null");
            case Boolean b -> sb.append(b ? "true" : "false");
            case Double d -> sb.append(floatRepr(d));
            case Float f -> sb.append(floatRepr(f.doubleValue()));
            case Number n -> sb.append(n);
            case CharSequence s -> quote(sb, s);
            case Map<?, ?> m -> {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    quote(sb, String.valueOf(e.getKey()));
                    sb.append(": ");
                    write(sb, e.getValue());
                }
                sb.append('}');
            }
            case Collection<?> c -> {
                sb.append('[');
                boolean first = true;
                for (Object o : c) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    write(sb, o);
                }
                sb.append(']');
            }
            default -> quote(sb, String.valueOf(v));
        }
    }

    private static void quote(StringBuilder sb, CharSequence s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /**
     * Python's {@code repr(float)} / {@code str(float)}: shortest round-trip digits, scientific notation
     * when the decimal exponent is below -4 or at least 16, always a {@code .0} or exponent.
     *
     * @param d the value.
     * @return the text, e.g. {@code 1.0}, {@code 0.0001}, {@code 1e-05}, {@code 1.5e+20}, {@code inf}.
     */
    static String floatRepr(double d) {
        if (Double.isNaN(d)) {
            return "nan";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        if (d == 0) {
            return (1 / d < 0) ? "-0.0" : "0.0";
        }
        // Shortest digits that round-trip (Double.toString is shortest since JDK 19).
        BigDecimal bd = new BigDecimal(Double.toString(Math.abs(d))).stripTrailingZeros();
        String digits = bd.unscaledValue().toString();
        int exp10 = digits.length() - 1 - bd.scale();   // decimal exponent of the first digit
        StringBuilder out = new StringBuilder();
        if (d < 0) {
            out.append('-');
        }
        if (exp10 < -4 || exp10 >= 16) {
            out.append(digits.charAt(0));
            if (digits.length() > 1) {
                out.append('.').append(digits, 1, digits.length());
            }
            out.append('e').append(exp10 < 0 ? '-' : '+');
            int abs = Math.abs(exp10);
            if (abs < 10) {
                out.append('0');
            }
            out.append(abs);
        } else if (exp10 >= 0) {
            if (digits.length() <= exp10 + 1) {
                out.append(digits).append("0".repeat(exp10 + 1 - digits.length())).append(".0");
            } else {
                out.append(digits, 0, exp10 + 1).append('.').append(digits, exp10 + 1, digits.length());
            }
        } else {
            out.append("0.").append("0".repeat(-exp10 - 1)).append(digits);
        }
        return out.toString();
    }
}
