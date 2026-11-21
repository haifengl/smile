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

import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Temporary Vert.x compatibility route filter that transparently reroutes
 * legacy {@code /api/v1/{chat,models,smile,onnx}} inference requests to
 * {@code /v1/{chat,models,smile,onnx}} while preserving HTTP method, headers,
 * body, and query string.
 *
 * <p>Application endpoints under {@code /api/v1/{auth,users,conversations,media}}
 * and extension endpoints remain untouched under {@code /api/v1/*}.
 *
 * @author Haifeng Li
 */
@ApplicationScoped
public class LegacyApiCompatFilter {

    private static final String LEGACY_PREFIX = "/api/v1";
    private static final String TARGET_PREFIX = "/v1";

    void register(@Observes Router router) {
        router.route("/api/v1/*").order(-500).handler(rc -> {
            String path = rc.normalizedPath();
            String rewritten = rewriteLegacyInferencePath(path);
            if (rewritten != null) {
                String query = rc.request().query();
                String target = (query != null && !query.isEmpty())
                        ? rewritten + "?" + query
                        : rewritten;
                rc.reroute(rc.request().method(), target);
                return;
            }
            rc.next();
        });
    }

    /**
     * Rewrites a legacy inference path under {@code /api/v1/{chat,models,smile,onnx}}
     * to {@code /v1/...}, or returns {@code null} if the path should not be rewritten.
     */
    static String rewriteLegacyInferencePath(String path) {
        if (path == null || !path.startsWith(LEGACY_PREFIX)) {
            return null;
        }
        String suffix = path.substring(LEGACY_PREFIX.length());
        if (matchesNamespace(suffix, "/chat")
                || matchesNamespace(suffix, "/models")
                || matchesNamespace(suffix, "/smile")
                || matchesNamespace(suffix, "/onnx")) {
            return TARGET_PREFIX + suffix;
        }
        return null;
    }

    private static boolean matchesNamespace(String suffix, String namespace) {
        return suffix.equals(namespace) || suffix.startsWith(namespace + "/");
    }
}
