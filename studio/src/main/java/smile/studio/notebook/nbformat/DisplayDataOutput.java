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

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * A rich display-data output (output type {@code "display_data"}).
 * <p>
 * The {@code data} map is keyed by MIME type (e.g. {@code "text/plain"},
 * {@code "text/html"}, {@code "image/png"}) and the value is either a string
 * or an array of strings for multiline text, or a base64-encoded string for
 * binary formats such as images. The optional {@code metadata} map may carry
 * MIME-type-specific metadata (e.g. image dimensions).
 *
 * @param data     the MIME-bundle mapping MIME types to their content.
 * @param metadata optional MIME-type-specific metadata.
 * @param transientData transient data not saved with the notebook (e.g. display ids).
 *
 * @author Haifeng Li
 */
public record DisplayDataOutput(
        @JsonProperty("data") Map<String, JsonNode> data,
        @JsonProperty("metadata") Map<String, JsonNode> metadata,
        @JsonProperty("transient") Map<String, JsonNode> transientData
) implements Output {

    @Override
    public String outputType() {
        return "display_data";
    }
}

