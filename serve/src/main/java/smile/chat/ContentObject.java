/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import smile.chat.blob.MediaUrls;

/**
 * API response for an uploaded or retrieved media object.
 *
 * @author Haifeng Li
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentObject {
    /** Opaque content UUID. */
    public String contentId;
    /** Relative URL for chat completions and the UI. */
    public String url;
    public String mimeType;
    public String filename;
    public long sizeBytes;
    public String sha256;

    /**
     * Builds a response DTO from a persisted metadata row.
     *
     * @param content entity.
     * @return API object.
     */
    public static ContentObject from(ConversationContent content) {
        ContentObject obj = new ContentObject();
        obj.contentId = content.id;
        obj.url = MediaUrls.toUrl(content.id);
        obj.mimeType = content.mimeType;
        obj.filename = content.filename;
        obj.sizeBytes = content.sizeBytes;
        obj.sha256 = content.sha256;
        return obj;
    }
}
