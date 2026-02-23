/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Studio is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.client;

import java.util.Optional;

/**
 * The handler for streaming response from LLM service.
 *
 * @author Haifeng Li
 */
public interface StreamResponseHandler {
    /**
     * Handles the next chunk of response.
     * @param chunk the next chunk of response.
     */
    void onNext(String chunk);
    
    /**
     * Handles the completion of response stream.
     * @param error the error if any, or empty if the stream is completed successfully.
     */
    void onComplete(Optional<Throwable> error);
}
