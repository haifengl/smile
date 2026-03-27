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
 * Notebook-level metadata. Contains information about the kernel and language
 * used in the notebook, as well as any other user-defined metadata.
 *
 * @param kernelspec    information about the kernel used to run the notebook.
 * @param languageInfo  information about the programming language of the kernel.
 * @param title         optional notebook title.
 * @param authors       optional list of authors.
 * @param origNbformat  the original nbformat major version if this notebook was
 *                      converted from an older format; {@code null} otherwise.
 *
 * @author Haifeng Li
 */
public record NotebookMetadata(
        @JsonProperty("kernelspec") KernelSpec kernelspec,
        @JsonProperty("language_info") LanguageInfo languageInfo,
        @JsonProperty("title") String title,
        @JsonProperty("authors") java.util.List<java.util.Map<String, String>> authors,
        @JsonProperty("orig_nbformat") Integer origNbformat
) {
}

