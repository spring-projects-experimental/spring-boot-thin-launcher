/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.cli.compiler.grape;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link GrapeEngine} implementation that uses
 * <a href="http://eclipse.org/aether">Aether</a>, the dependency resolution system used
 * by Maven.
 *
 * @author Andy Wilkinson
 * @author Phillip Webb
 */
public class AetherEngine {

	private final DependencyResolutionContext resolutionContext;

	private final ProgressReporter progressReporter;

	private final DefaultRepositorySystemSession session;

	private final RepositorySystem repositorySystem;

	private final List<RemoteRepository> repositories;

	public AetherEngine(RepositorySystem repositorySystem,
			DefaultRepositorySystemSession repositorySystemSession,
			List<RemoteRepository> remoteRepositories,
			DependencyResolutionContext resolutionContext) {
		this.repositorySystem = repositorySystem;
		this.session = repositorySystemSession;
		this.resolutionContext = resolutionContext;
		this.repositories = new ArrayList<RemoteRepository>();
		List<RemoteRepository> remotes = new ArrayList<RemoteRepository>(
				remoteRepositories);
		Collections.reverse(remotes); // priority is reversed in addRepository
		for (RemoteRepository repository : remotes) {
			addRepository(repository);
		}
		this.progressReporter = getProgressReporter(this.session);
	}

	private ProgressReporter getProgressReporter(DefaultRepositorySystemSession session) {
		if (Boolean.getBoolean("groovy.grape.report.downloads")) {
			return new DetailedProgressReporter(session, System.out);
		}
		return new SummaryProgressReporter(session, System.out);
	}

	private List<Dependency> getDependencies(DependencyResult dependencyResult) {
		List<Dependency> dependencies = new ArrayList<Dependency>();
		for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
			dependencies.add(
					new Dependency(artifactResult.getArtifact(), JavaScopes.COMPILE));
		}
		return dependencies;
	}

	private List<File> getFiles(DependencyResult dependencyResult) {
		List<File> files = new ArrayList<File>();
		for (ArtifactResult result : dependencyResult.getArtifactResults()) {
			files.add(result.getArtifact().getFile());
		}
		return files;
	}

	protected void addRepository(RemoteRepository repository) {
		if (this.repositories.contains(repository)) {
			return;
		}
		repository = getPossibleMirror(repository);
		repository = applyProxy(repository);
		repository = applyAuthentication(repository);
		this.repositories.add(0, repository);
	}

	private RemoteRepository getPossibleMirror(RemoteRepository remoteRepository) {
		RemoteRepository mirror = this.session.getMirrorSelector()
				.getMirror(remoteRepository);
		if (mirror != null) {
			return mirror;
		}
		return remoteRepository;
	}

	private RemoteRepository applyProxy(RemoteRepository repository) {
		if (repository.getProxy() == null) {
			RemoteRepository.Builder builder = new RemoteRepository.Builder(repository);
			builder.setProxy(this.session.getProxySelector().getProxy(repository));
			repository = builder.build();
		}
		return repository;
	}

	private RemoteRepository applyAuthentication(RemoteRepository repository) {
		if (repository.getAuthentication() == null) {
			RemoteRepository.Builder builder = new RemoteRepository.Builder(repository);
			builder.setAuthentication(this.session.getAuthenticationSelector()
					.getAuthentication(repository));
			repository = builder.build();
		}
		return repository;
	}

	public List<File> resolve(List<Dependency> dependencies)
			throws ArtifactResolutionException {
		try {
			CollectRequest collectRequest = getCollectRequest(dependencies);
			DependencyRequest dependencyRequest = getDependencyRequest(collectRequest);
			DependencyResult result = this.repositorySystem
					.resolveDependencies(this.session, dependencyRequest);
			addManagedDependencies(result);
			return getFiles(result);
		}
		catch (Exception ex) {
			throw new DependencyResolutionFailedException(ex);
		}
		finally {
			this.progressReporter.finished();
		}
	}

	private CollectRequest getCollectRequest(List<Dependency> dependencies) {
		CollectRequest collectRequest = new CollectRequest((Dependency) null,
				dependencies, new ArrayList<RemoteRepository>(this.repositories));
		collectRequest
				.setManagedDependencies(this.resolutionContext.getManagedDependencies());
		return collectRequest;
	}

	private DependencyRequest getDependencyRequest(CollectRequest collectRequest) {
		DependencyRequest dependencyRequest = new DependencyRequest(collectRequest,
				DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE,
						JavaScopes.RUNTIME));
		return dependencyRequest;
	}

	private void addManagedDependencies(DependencyResult result) {
		this.resolutionContext.addManagedDependencies(getDependencies(result));
	}

}
