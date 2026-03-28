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
