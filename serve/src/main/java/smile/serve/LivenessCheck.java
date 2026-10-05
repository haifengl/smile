/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
package smile.serve;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness probe for the serve API server, exposed at {@code /q/health/live}.
 *
 * <p>Confirms that the server process is alive and responding. Suitable for
 * Kubernetes {@code livenessProbe} configurations to detect deadlocks or
 * unresponsive server states. This check never touches model state, so a
 * failed model load does not cause the process to be restarted.
 *
 * @author Haifeng Li
 */
@Liveness
@ApplicationScoped
public class LivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("smile-serve-liveness");
    }
}
