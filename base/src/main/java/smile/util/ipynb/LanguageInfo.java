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
package smile.util.ipynb;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about the programming language of the kernel.
 * <p>
 * All fields are optional; only {@code name} is required by the spec.
 * The {@code codemirror_mode} field may be either a plain string or an
 * object (e.g. {@code {"name":"ipython","version":3}}) so it is modelled
 * as {@link tools.jackson.databind.JsonNode} to handle both forms.
 *
 * @param name              the language name (e.g. {@code "python"}, {@code "scala"}).
 * @param version           the language version string (e.g. {@code "3.10.4"}).
 * @param mimeType          the MIME type for the language (e.g. {@code "text/x-python"}).
 * @param fileExtension     the standard file extension (e.g. {@code ".py"}).
 * @param pygmentsLexer     the name of the Pygments lexer for syntax highlighting.
 * @param nbconvertExporter the name of the nbconvert exporter for this language.
 * @param codemirrorMode    the CodeMirror mode — either a plain string or an object.
 * @param versionInfo       the language version broken into components as a list of integers
 *                          (e.g. {@code [3, 10, 4, "final", 0]} for Python 3.10.4).
 *
 * @author Haifeng Li
 */
public record LanguageInfo(
        @JsonProperty("name") String name,
        @JsonProperty("version") String version,
        @JsonProperty("mimetype") String mimeType,
        @JsonProperty("file_extension") String fileExtension,
        @JsonProperty("pygments_lexer") String pygmentsLexer,
        @JsonProperty("nbconvert_exporter") String nbconvertExporter,
        @JsonProperty("codemirror_mode") tools.jackson.databind.JsonNode codemirrorMode,
        @JsonProperty("version_info") List<Object> versionInfo
) {
}

