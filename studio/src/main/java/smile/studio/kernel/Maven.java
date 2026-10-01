/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.kernel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

/**
 * Maven dependency resolver.
 *
 * @author Haifeng Li
 */
public interface Maven {
    /**
     * Returns the list of transitive dependencies of an artifact including itself.
     * @param groupId the group or organization that created the artifact.
     * @param artifactId the specific project within the group.
     * @param version the specific version of the artifact.
     * @return the list of transitive dependencies of an artifact including itself.
     * @throws DependencyResolutionException if Maven could not resolve dependencies.
     * @throws DependencyCollectionException if bad artifact descriptors, version ranges
     * or other issues encountered during calculation of the dependency graph.
     */
    static List<Artifact> getDependencyJarPaths(String groupId, String artifactId, String version)
            throws DependencyResolutionException, DependencyCollectionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "", "jar", version);

        var systemSupplier = new RepositorySystemSupplier();
        var system = systemSupplier.get();
        var sessionSupplier = new SessionBuilderSupplier(system);
        var localRepository = Path.of(System.getProperty("user.home") + "/.m2/repository");
        var sessionBuilder = sessionSupplier.get().withLocalRepositoryBaseDirectories(localRepository);
        var session = sessionBuilder.build();

        // Define remote repositories (e.g., Maven Central)
        List<RemoteRepository> remoteRepos = new ArrayList<>();
        remoteRepos.add(new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build());

        // Create a dependency request
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, "compile")); // compile-time dependencies
        collectRequest.setRepositories(remoteRepos);

        // Resolve the dependencies
        CollectResult collectResult = system.collectDependencies(session, collectRequest);
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setRoot(collectResult.getRoot());
        DependencyResult dependencyResult = system.resolveDependencies(session, dependencyRequest);

        List<Artifact> dependencyJarPaths = new ArrayList<>();
        dependencyJarPaths.add(artifact);
        for (ArtifactResult result : dependencyResult.getArtifactResults()) {
            dependencyJarPaths.add(result.getArtifact());
        }
        return dependencyJarPaths;
    }
}
