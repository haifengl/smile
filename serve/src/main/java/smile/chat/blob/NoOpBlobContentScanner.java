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
package smile.chat.blob;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.profile.UnlessBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Default pass-through {@link BlobContentScanner} active when no antivirus or
 * DLP scanner plugin is installed.
 *
 * @author Haifeng Li
 */
@DefaultBean
@UnlessBuildProfile("micro")
@ApplicationScoped
public class NoOpBlobContentScanner implements BlobContentScanner {

    @Override
    public ScanResult scan(ScanContext context) {
        return ScanResult.ok();
    }
}
