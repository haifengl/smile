/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile Shell is free software: you can redistribute it and/or modify
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile Shell is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
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
    static List<Path> getDependencyJarPaths(String groupId, String artifactId, String version)
            throws DependencyResolutionException, DependencyCollectionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);

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

        List<Path> dependencyJarPaths = new ArrayList<>();
        dependencyJarPaths.add(artifact.getPath());
        for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
            dependencyJarPaths.add(artifactResult.getArtifact().getPath());
        }
        return dependencyJarPaths;
    }
}
