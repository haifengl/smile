/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.serve;

/**
 * SPI for plugins that observe inference executions, online updates, and
 * model lifecycle transitions across classic SMILE and ONNX models.
 *
 * <p>Implementations are discovered via CDI ({@code @All List<InferenceListener>});
 * when no listener plugin jar is present in {@code serve/plugins/}, inference
 * and model lifecycle operations proceed without listener invocation.
 *
 * @author Haifeng Li
 */
public interface InferenceListener {
    /**
     * Invoked when an inference execution, online update, or model lifecycle
     * transition completes. Implementations should be non-blocking and must not
     * throw exceptions into the caller.
     *
     * @param event the inference or lifecycle event.
     */
    void onEvent(InferenceEvent event);
}
