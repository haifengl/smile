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
package smile.serve.model;

/**
 * Helpers for interpreting {@code model} specifications. Kept free of any chat dependency so that
 * model loading works in builds that exclude the chat stack.
 *
 * @author Haifeng Li
 */
public final class ModelSpecs {
    private ModelSpecs() {
    }

    /**
     * Returns {@code true} when the spec looks like a Hugging Face repo id ({@code owner/name}) rather than
     * a filesystem path.
     *
     * @param spec a model spec such as a path or a repo id.
     * @return {@code true} if the value should be resolved via Hugging Face Hub.
     */
    public static boolean looksLikeHuggingFaceRepoId(String spec) {
        if (spec == null || spec.isBlank()) return false;
        String s = spec.trim();
        if (s.startsWith("/") || s.startsWith(".") || s.contains("\\") || s.contains(":")) {
            return false;
        }
        int slash = s.indexOf('/');
        return slash > 0 && slash == s.lastIndexOf('/') && slash != s.length() - 1;
    }
}
