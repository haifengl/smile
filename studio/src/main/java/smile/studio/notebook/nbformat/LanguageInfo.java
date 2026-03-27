/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.studio.notebook.nbformat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about the programming language of the kernel.
 *
 * @param name              the language name (e.g. {@code "python"}, {@code "scala"}).
 * @param version           the language version string.
 * @param mimeType          the MIME type for the language (e.g. {@code "text/x-python"}).
 * @param fileExtension     the standard file extension for source files (e.g. {@code ".py"}).
 * @param pygmentsLexer     the name of the Pygments lexer used for syntax highlighting.
 * @param nbconvertExporter the name of the nbconvert exporter to use for this language.
 * @param codemirrorMode    the CodeMirror mode for syntax highlighting in the front end.
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
        @JsonProperty("codemirror_mode") tools.jackson.databind.JsonNode codemirrorMode
) {
}

