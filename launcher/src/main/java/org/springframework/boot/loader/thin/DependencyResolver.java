/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.boot.loader.thin;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.inject.Singleton;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.apache.maven.model.locator.ModelLocator;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest.RepositoryMerging;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.impl.guice.AetherModule;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RemoteRepository.Builder;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.eclipse.aether.util.repository.JreProxySelector;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.eclipse.sisu.plexus.ClassRealmManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class DependencyResolver {

	private static final Logger log = LoggerFactory.getLogger(DependencyResolver.class);

	private static DependencyResolver instance = new DependencyResolver();

	private static Properties globals;

	private LocalRepositoryManagerFactory localRepositoryManagerFactory;

	private PlexusContainer container;
	private Object lock = new Object();

	private ProjectBuilder projectBuilder;

	private RepositorySystem repositorySystem;

	private MavenSettings settings;

	public static DependencyResolver instance() {
		return instance;
	}

	public static void close() {
		if (instance != null) {
			instance.dispose();
		}
		instance = new DependencyResolver();
	}

	private void dispose() {
		try {
			if (this.container != null) {
				this.container.dispose();
			}
		}
		catch (Exception e) {
			// swallow
		}
	}

	private DependencyResolver() {
	}

	private void initialize() {
		if (this.container == null) {
			synchronized (lock) {
				if (this.container == null) {
					ClassWorld classWorld = new ClassWorld("plexus.core",
							Thread.currentThread().getContextClassLoader());
					ContainerConfiguration config = new DefaultContainerConfiguration()
							.setClassWorld(classWorld)
							.setRealm(classWorld.getClassRealm("plexus.core"))
							.setClassPathScanning(PlexusConstants.SCANNING_INDEX)
							.setAutoWiring(true).setName("maven");
					PlexusContainer container;
					try {
						container = new DefaultPlexusContainer(config, new AetherModule(),
								new DependencyResolutionModule());
						localRepositoryManagerFactory = container
								.lookup(LocalRepositoryManagerFactory.class);
						container.addComponent(
								new ClassRealmManager((MutablePlexusContainer) container,
										new DefaultBeanLocator()),
								ClassRealmManager.class.getName());
						projectBuilder = container.lookup(ProjectBuilder.class);
						repositorySystem = container.lookup(RepositorySystem.class);
					}
					catch (Exception e) {
						throw new IllegalStateException("Cannot create container", e);
					}
					this.container = container;
					this.settings = new MavenSettingsReader().readSettings();
				}
			}
		}
	}

	public List<Dependency> dependencies(Resource resource) {
		return dependencies(resource, new Properties());
	}

	public List<Dependency> dependencies(final Resource resource,
			final Properties properties) {
		if ("true".equals(properties.getProperty("computed", "false"))) {
			log.info("Dependencies are pre-computed in properties");
			Model model = new Model();
			model = ThinPropertiesModelProcessor.process(model, properties);
			return aetherDependencies(model.getDependencies());
		}
		initialize();
		try {
			log.info("Computing dependencies from pom and properties");
			ProjectBuildingRequest request = getProjectBuildingRequest(properties);
			request.setResolveDependencies(true);
			synchronized (DependencyResolver.class) {
				ProjectBuildingResult result = projectBuilder
						.build(new PropertiesModelSource(properties, resource), request);
				DependencyResolver.globals = null;
				DependencyResolutionResult dependencies = result
						.getDependencyResolutionResult();
				if (!dependencies.getUnresolvedDependencies().isEmpty()) {
					StringBuilder builder = new StringBuilder();
					for (Dependency dependency : dependencies
							.getUnresolvedDependencies()) {
						List<Exception> errors = dependencies
								.getResolutionErrors(dependency);
						for (Exception exception : errors) {
							if (builder.length() > 0) {
								builder.append("\n");
							}
							builder.append(exception.getMessage());
						}
					}
					throw new RuntimeException(builder.toString());
				}
				List<Dependency> output = runtime(dependencies.getDependencies());
				if (log.isInfoEnabled()) {
					for (Dependency dependency : output) {
						log.info("Resolved: " + coordinates(dependency) + "="
								+ dependency.getArtifact().getFile());
					}
				}
				return output;
			}
		}
		catch (ProjectBuildingException | NoLocalRepositoryManagerException e) {
			throw new IllegalStateException("Cannot build model", e);
		}
	}

	private List<Dependency> aetherDependencies(
			List<org.apache.maven.model.Dependency> dependencies) {
		List<Dependency> list = new ArrayList<>();
		for (org.apache.maven.model.Dependency dependency : dependencies) {
			Artifact artifact = new DefaultArtifact(coordinates(dependency));
			Dependency converted = new Dependency(artifact, "runtime");
			list.add(converted);
		}
		initialize();
		List<ArtifactResult> result = collectNonTransitive(list);
		list = new ArrayList<>();
		for (ArtifactResult item : result) {
			Artifact artifact = item.getArtifact();
			Dependency converted = new Dependency(artifact, "runtime");
			list.add(converted);
		}
		return list;
	}

	private String coordinates(org.apache.maven.model.Dependency artifact) {
		// group:artifact:extension:classifier:version
		String classifier = artifact.getClassifier();
		String extension = artifact.getType();
		return artifact.getGroupId() + ":" + artifact.getArtifactId()
				+ (extension != null ? ":" + extension : "")
				+ (classifier != null ? ":" + classifier : "") + ":"
				+ artifact.getVersion();
	}

	private String coordinates(Dependency dependency) {
		Artifact artifact = dependency.getArtifact();
		// group:artifact:extension:classifier:version
		String classifier = artifact.getClassifier();
		String extension = artifact.getExtension();
		if ("jar".equals(extension) && !StringUtils.hasText(classifier)) {
			extension = null;
		}
		boolean hasExtension = extension != null && !"jar".equals(extension);
		return artifact.getGroupId() + ":" + artifact.getArtifactId()
				+ (hasExtension ? ":" + extension : "")
				+ (StringUtils.hasText(classifier) ? ":" + classifier : "") + ":"
				+ artifact.getVersion();
	}

	public File resolve(Dependency dependency) {
		initialize();
		return collectNonTransitive(Arrays.asList(dependency)).iterator().next()
				.getArtifact().getFile();
	}

	private List<Dependency> runtime(List<Dependency> dependencies) {
		List<Dependency> list = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			if (!"test".equals(dependency.getScope())
					&& !"provided".equals(dependency.getScope())) {
				list.add(dependency);
			}
		}
		return list;
	}

	private ProjectBuildingRequest getProjectBuildingRequest(Properties properties)
			throws NoLocalRepositoryManagerException {
		DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
		DefaultRepositorySystemSession session = createSession(properties);
		projectBuildingRequest.setRepositoryMerging(RepositoryMerging.REQUEST_DOMINANT);
		projectBuildingRequest
				.setRemoteRepositories(mavenRepositories(settings, session, properties));
		projectBuildingRequest.setRemoteRepositories(mavenRepositories(settings, session,
				projectBuildingRequest.getRemoteRepositories()));
		projectBuildingRequest.setRepositorySession(session);
		projectBuildingRequest.setProcessPlugins(false);
		projectBuildingRequest.setBuildStartTime(new Date());
		projectBuildingRequest.setUserProperties(properties);
		projectBuildingRequest.setSystemProperties(System.getProperties());
		Set<String> profiles = new LinkedHashSet<String>();
		for (Profile profile : settings.getActiveProfiles()) {
			profiles.add(profile.getId());
		}
		if (properties.containsKey(ThinJarLauncher.THIN_PROFILE)) {
			String property = properties.getProperty(ThinJarLauncher.THIN_PROFILE);
			if (property.length() > 0) {
				profiles.addAll(StringUtils.commaDelimitedListToSet(property));
			}
		}
		if (!profiles.isEmpty()) {
			projectBuildingRequest.setActiveProfileIds(new ArrayList<>(profiles));
		}
		return projectBuildingRequest;
	}

	private List<ArtifactRepository> mavenRepositories(MavenSettings settings,
			DefaultRepositorySystemSession session,
			List<ArtifactRepository> repositories) {
		List<ArtifactRepository> list = new ArrayList<>(repositories);
		for (Profile profile : settings.getActiveProfiles()) {
			for (Repository repository : profile.getRepositories()) {
				addRepositoryIfMissing(settings, session, list, repository.getId(),
						repository.getUrl(),
						repository.getReleases() != null
								? repository.getReleases().isEnabled()
								: true,
						repository.getSnapshots() != null
								? repository.getSnapshots().isEnabled()
								: true);
			}
		}
		return list;
	}

	private List<ArtifactRepository> mavenRepositories(MavenSettings settings,
			DefaultRepositorySystemSession session, Properties properties) {
		List<ArtifactRepository> list = new ArrayList<>();
		if (properties.containsKey(ThinJarLauncher.THIN_ROOT)) {
			addRepositoryIfMissing(settings, session, list, "local",
					"file://" + getM2RepoDirectory(), true, true);
		}
		addRepositoryIfMissing(settings, session, list, "spring-snapshots",
				"https://repo.spring.io/libs-snapshot", true, true);
		addRepositoryIfMissing(settings, session, list, "central",
				"https://repo1.maven.org/maven2", true, false);
		return list;
	}

	private List<RemoteRepository> aetherRepositories(MavenSettings settings,
			DefaultRepositorySystemSession session, Properties properties) {
		List<RemoteRepository> list = new ArrayList<>();
		for (ArtifactRepository input : mavenRepositories(settings, session,
				properties)) {
			list.add(remote(input));
		}
		return list;
	}

	private RemoteRepository remote(ArtifactRepository input) {
		Proxy proxy = proxy(input);
		Builder builder = new RemoteRepository.Builder(input.getId(),
				input.getLayout().getId(), input.getUrl())
						.setSnapshotPolicy(policy(input.getSnapshots()))
						.setReleasePolicy(policy(input.getReleases()));
		if (proxy != null) {
			builder = builder.setProxy(proxy);
		}
		return builder.build();
	}

	private org.eclipse.aether.repository.Proxy proxy(ArtifactRepository repo) {
		org.apache.maven.repository.Proxy proxy = repo.getProxy();
		if (proxy == null) {
			return null;
		}
		Authentication authentication = new AuthenticationBuilder()
				.addUsername(proxy.getUserName()).addPassword(proxy.getPassword())
				.build();
		return new org.eclipse.aether.repository.Proxy(proxy.getProtocol(),
				proxy.getHost(), proxy.getPort(), authentication);
	}

	private RepositoryPolicy policy(ArtifactRepositoryPolicy input) {
		RepositoryPolicy policy = new RepositoryPolicy(input.isEnabled(),
				RepositoryPolicy.UPDATE_POLICY_DAILY,
				RepositoryPolicy.CHECKSUM_POLICY_WARN);
		return policy;
	}

	private void addRepositoryIfMissing(MavenSettings settings,
			DefaultRepositorySystemSession session, List<ArtifactRepository> list,
			String id, String url, boolean releases, boolean snapshots) {
		for (ArtifactRepository repo : list) {
			if (url.equals(repo.getUrl())) {
				return;
			}
			if (id.equals(repo.getId())) {
				return;
			}
		}
		list.add(repo(settings, session, id, url, releases, snapshots));
	}

	private ArtifactRepository repo(MavenSettings settings,
			DefaultRepositorySystemSession session, String id, String url,
			boolean releases, boolean snapshots) {
		MavenArtifactRepository repository = new MavenArtifactRepository();
		repository.setLayout(new DefaultRepositoryLayout());
		repository.setId(id);
		repository.setUrl(url);
		ArtifactRepositoryPolicy enabled = new ArtifactRepositoryPolicy();
		enabled.setEnabled(true);
		ArtifactRepositoryPolicy disabled = new ArtifactRepositoryPolicy();
		disabled.setEnabled(false);
		repository.setReleaseUpdatePolicy(releases ? enabled : disabled);
		repository.setSnapshotUpdatePolicy(snapshots ? enabled : disabled);
		RemoteRepository remote = new RemoteRepository.Builder(id, null, url).build();
		Authentication authentication = settings.getAuthenticationSelector()
				.getAuthentication(remote);
		if (authentication != null) {
			remote = new RemoteRepository.Builder(remote)
					.setAuthentication(authentication).build();
			repository.setAuthentication(
					authentication(settings, session, remote, authentication));
		}
		ProxySelector proxy = settings.getProxySelector();
		if (proxy != null) {
			org.apache.maven.repository.Proxy value = proxy(settings, session, remote,
					proxy);
			if (value != null) {
				repository.setProxy(value);
			}
		}
		return repository;
	}

	private org.apache.maven.repository.Proxy proxy(MavenSettings settings,
			DefaultRepositorySystemSession session, RemoteRepository remote,
			ProxySelector proxy) {
		Proxy config = proxy.getProxy(remote);
		if (config == null) {
			return null;
		}
		org.apache.maven.repository.Proxy result = new org.apache.maven.repository.Proxy();
		result.setHost(config.getHost());
		if (config.getAuthentication() != null) {
			org.apache.maven.artifact.repository.Authentication auth = authentication(
					settings, session,
					new RemoteRepository.Builder(remote)
							.setAuthentication(config.getAuthentication()).build(),
					config.getAuthentication());
			result.setUserName(auth.getUsername());
			result.setPassword(auth.getPassword() != null ? auth.getPassword()
					: auth.getPassphrase());
		}
		result.setProtocol(config.getType());
		result.setPort(config.getPort());
		return result;
	}

	private org.apache.maven.artifact.repository.Authentication authentication(
			MavenSettings settings, RepositorySystemSession session,
			RemoteRepository remote, Authentication authentication) {
		AuthenticationContext context = AuthenticationContext.forRepository(session,
				remote);
		if (context == null) {
			return null;
		}
		authentication.fill(context, "username", Collections.<String, String>emptyMap());
		authentication.fill(context, "password", Collections.<String, String>emptyMap());
		authentication.fill(context, "passphrase",
				Collections.<String, String>emptyMap());
		authentication.fill(context, "privateKey",
				Collections.<String, String>emptyMap());
		org.apache.maven.artifact.repository.Authentication maven = new org.apache.maven.artifact.repository.Authentication(
				context.get("username"), context.get("password"));
		if (context.get("passphrase") != null) {
			maven.setPassphrase(context.get("passphrase"));
		}
		if (context.get("privateKey") != null) {
			maven.setPrivateKey(context.get("privateKey"));
		}
		return maven;
	}

	private DefaultRepositorySystemSession createSession(Properties properties)
			throws NoLocalRepositoryManagerException {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository repository = localRepository(properties);
		session.setLocalRepositoryManager(
				localRepositoryManagerFactory.newInstance(session, repository));
		applySettings(session);
		ProxySelector existing = session.getProxySelector();
		if (existing == null || !(existing instanceof CompositeProxySelector)) {
			JreProxySelector fallback = new JreProxySelector();
			ProxySelector selector = existing == null ? fallback
					: new CompositeProxySelector(Arrays.asList(existing, fallback));
			session.setProxySelector(selector);
		}
		if (properties.containsKey("thin.offline")
				&& !"false".equals(properties.getProperty("thin.offline"))) {
			session.setOffline(true);
		}
		return session;
	}

	private void applySettings(DefaultRepositorySystemSession session) {
		MavenSettingsReader.applySettings(settings, session);
	}

	private LocalRepository localRepository(Properties properties) {
		if (!properties.containsKey("thin.root")) {
			return new LocalRepository(getM2RepoDirectory());
		}
		String root = properties.getProperty("thin.root");
		return new LocalRepository(StringUtils.cleanPath(root + "/repository"));
	}

	public Model readModel(Resource resource) {
		return readModel(resource, new Properties());
	}

	public Model readModel(final Resource resource, final Properties properties) {
		initialize();
		try {
			ProjectBuildingRequest request = getProjectBuildingRequest(properties);
			request.setResolveDependencies(false);
			ProjectBuildingResult result = projectBuilder
					.build(new PropertiesModelSource(properties, resource), request);
			return result.getProject().getModel();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to build model from effective pom",
					e);
		}
	}

	private File getM2RepoDirectory() {
		String mavenRoot = System.getProperty("maven.repo.local");
		if (StringUtils.hasLength(mavenRoot)) {
			return new File(mavenRoot);
		}
		return new File(getDefaultM2HomeDirectory(), "repository");
	}

	private File getDefaultM2HomeDirectory() {
		String mavenRoot = System.getProperty("maven.home");
		if (StringUtils.hasLength(mavenRoot)) {
			return new File(mavenRoot);
		}
		return new File(System.getProperty("user.home"), ".m2");
	}

	private List<ArtifactResult> collectNonTransitive(List<Dependency> dependencies) {
		try {
			List<ArtifactRequest> artifactRequests = getArtifactRequests(dependencies);
			List<ArtifactResult> result = this.repositorySystem
					.resolveArtifacts(createSession(new Properties()), artifactRequests);
			return result;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private List<ArtifactRequest> getArtifactRequests(List<Dependency> dependencies) {
		List<ArtifactRequest> list = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			ArtifactRequest request = new ArtifactRequest(dependency.getArtifact(), null,
					null);
			DefaultRepositorySystemSession session;
			try {
				session = createSession(new Properties());
				request.setRepositories(
						aetherRepositories(settings, session, new Properties()));
			}
			catch (NoLocalRepositoryManagerException e) {
				throw new IllegalStateException("No local repository manager", e);
			}
			list.add(request);
		}
		return list;
	}

	// Package private for model resolution hack in ThinPropertiesModelProcessor
	static Properties getGlobals() {
		return globals;
	}

	@SuppressWarnings("deprecation")
	private static final class PropertiesModelSource
			implements org.apache.maven.model.building.ModelSource {
		private final Properties properties;

		private final Resource resource;

		private PropertiesModelSource(Properties properties, Resource resource) {
			this.properties = properties;
			this.resource = resource;
		}

		@Override
		public InputStream getInputStream() throws IOException {
			DependencyResolver.globals = properties;
			return new BufferedInputStream(resource.getInputStream()) {
				@Override
				public void close() throws IOException {
					DependencyResolver.globals = null;
					super.close();
				}
			};
		}

		@Override
		public String getLocation() {
			return resource.getDescription();
		}
	}

}

class DependencyResolutionModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ModelProcessor.class).to(ThinPropertiesModelProcessor.class)
				.in(Singleton.class);
		bind(ModelLocator.class).to(DefaultModelLocator.class).in(Singleton.class);
		bind(ModelReader.class).to(DefaultModelReader.class).in(Singleton.class);
		bind(ModelValidator.class).to(DefaultModelValidator.class).in(Singleton.class);
		bind(RepositoryConnectorFactory.class).to(BasicRepositoryConnectorFactory.class)
				.in(Singleton.class);
		bind(ArtifactDescriptorReader.class) //
				.to(DefaultArtifactDescriptorReader.class).in(Singleton.class);
		bind(VersionResolver.class) //
				.to(DefaultVersionResolver.class).in(Singleton.class);
		bind(VersionRangeResolver.class) //
				.to(DefaultVersionRangeResolver.class).in(Singleton.class);
		bind(MetadataGeneratorFactory.class).annotatedWith(Names.named("snapshot")) //
				.to(SnapshotMetadataGeneratorFactory.class).in(Singleton.class);
		bind(MetadataGeneratorFactory.class).annotatedWith(Names.named("versions")) //
				.to(VersionsMetadataGeneratorFactory.class).in(Singleton.class);
		bind(TransporterFactory.class).annotatedWith(Names.named("http"))
				.to(HttpTransporterFactory.class).in(Singleton.class);
		bind(TransporterFactory.class).annotatedWith(Names.named("file"))
				.to(FileTransporterFactory.class).in(Singleton.class);
	}

	@Provides
	@Singleton
	Set<MetadataGeneratorFactory> provideMetadataGeneratorFactories(
			@Named("snapshot") MetadataGeneratorFactory snapshot,
			@Named("versions") MetadataGeneratorFactory versions) {
		Set<MetadataGeneratorFactory> factories = new HashSet<>();
		factories.add(snapshot);
		factories.add(versions);
		return Collections.unmodifiableSet(factories);
	}

	@Provides
	@Singleton
	Set<RepositoryConnectorFactory> provideRepositoryConnectorFactories(
			RepositoryConnectorFactory factory) {
		return Collections.singleton(factory);
	}

	@Provides
	@Singleton
	Set<TransporterFactory> provideTransporterFactories(
			@Named("file") TransporterFactory file,
			@Named("http") TransporterFactory http) {
		// Order is decided elsewhere (by priority)
		Set<TransporterFactory> factories = new HashSet<TransporterFactory>();
		factories.add(file);
		factories.add(http);
		return Collections.unmodifiableSet(factories);
	}

}
