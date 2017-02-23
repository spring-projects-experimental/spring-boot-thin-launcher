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
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.inject.Singleton;

import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.locator.DefaultModelLocator;
import org.apache.maven.model.locator.ModelLocator;
import org.apache.maven.model.validation.DefaultModelValidator;
import org.apache.maven.model.validation.ModelValidator;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.repository.internal.VersionsMetadataGeneratorFactory;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.MutablePlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.ArtifactDescriptorReader;
import org.eclipse.aether.impl.MetadataGeneratorFactory;
import org.eclipse.aether.impl.VersionRangeResolver;
import org.eclipse.aether.impl.VersionResolver;
import org.eclipse.aether.impl.guice.AetherModule;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.JreProxySelector;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.eclipse.sisu.plexus.ClassRealmManager;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

public class DependencyResolver {

	@SuppressWarnings("deprecation")
	private final class PropertiesModelSource
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

	private static DependencyResolver instance = new DependencyResolver();

	private static Properties globals;

	private LocalRepositoryManagerFactory localRepositoryManagerFactory;

	private PlexusContainer container;
	private Object lock = new Object();

	private ProjectBuilder projectBuilder;

	private RepositorySystem repositorySystem;

	public static DependencyResolver instance() {
		return instance;
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
				}
			}
		}
	}

	public List<Dependency> dependencies(Resource resource) {
		return dependencies(resource, new Properties());
	}

	public List<Dependency> dependencies(final Resource resource,
			final Properties properties) {
		initialize();
		try {
			ProjectBuildingRequest request = getProjectBuildingRequest(properties);
			request.setResolveDependencies(true);
			synchronized (DependencyResolver.class) {
				ProjectBuildingResult result = projectBuilder
						.build(new PropertiesModelSource(properties, resource), request);
				DependencyResolver.globals = null;
				return runtime(result.getDependencyResolutionResult().getDependencies());
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot build model", e);
		}
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
		projectBuildingRequest.setRepositorySession(session);
		projectBuildingRequest.setProcessPlugins(false);
		projectBuildingRequest.setBuildStartTime(new Date());
		projectBuildingRequest.setUserProperties(properties);
		projectBuildingRequest.setSystemProperties(System.getProperties());
		return projectBuildingRequest;
	}

	private List<RemoteRepository> repositories(Properties properties) {
		// TODO: why? Maybe it can be set centrally somewhere?
		RemoteRepository central = new RemoteRepository.Builder("central", "default",
				"https://repo1.maven.org/maven2").build();
		return Arrays.asList(central);
	}

	private DefaultRepositorySystemSession createSession(Properties properties)
			throws NoLocalRepositoryManagerException {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		LocalRepository repository = localRepository(properties);
		session.setLocalRepositoryManager(
				localRepositoryManagerFactory.newInstance(session, repository));
		ProxySelector existing = session.getProxySelector();
		if (existing == null || !(existing instanceof CompositeProxySelector)) {
			JreProxySelector fallback = new JreProxySelector();
			ProxySelector selector = existing == null ? fallback
					: new CompositeProxySelector(Arrays.asList(existing, fallback));
			session.setProxySelector(selector);
		}
		return session;
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
		return new File(getDefaultM2HomeDirectory(), "repository");
	}

	private File getDefaultM2HomeDirectory() {
		String mavenRoot = System.getProperty("maven.home");
		if (StringUtils.hasLength(mavenRoot)) {
			return new File(mavenRoot);
		}
		return new File(System.getProperty("user.home"), ".m2");
	}

	public File resolve(Dependency dependency) {
		initialize();
		return collectNonTransitive(Arrays.asList(dependency)).iterator().next()
				.getArtifact().getFile();
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
			request.setRepositories(repositories(null));
			list.add(request);
		}
		return list;
	}

	// Package private for model resolution hack in ThinPropertiesModelProcessor
	static Properties getGlobals() {
		return globals;
	}

}

class DependencyResolutionModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(ModelProcessor.class).to(ThinPropertiesModelProcessor.class)
				.in(Singleton.class);
		// bind(ModelProcessor.class).to(DefaultModelProcessor.class).in(Singleton.class);
		bind(ModelLocator.class).to(DefaultModelLocator.class).in(Singleton.class);
		bind(ModelReader.class).to(DefaultModelReader.class).in(Singleton.class);
		bind(ModelValidator.class).to(DefaultModelValidator.class).in(Singleton.class);
		bind(RepositoryConnectorFactory.class).to(BasicRepositoryConnectorFactory.class)
				.in(Singleton.class);
		bind(TransporterFactory.class).annotatedWith(Names.named("http"))
				.to(HttpTransporterFactory.class).in(Singleton.class);
		bind(TransporterFactory.class).annotatedWith(Names.named("file"))
				.to(HttpTransporterFactory.class).in(Singleton.class);
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
			@Named("http") TransporterFactory file,
			@Named("file") TransporterFactory http) {
		Set<TransporterFactory> factories = new HashSet<TransporterFactory>();
		factories.add(file);
		factories.add(file);
		return Collections.unmodifiableSet(factories);
	}

}
