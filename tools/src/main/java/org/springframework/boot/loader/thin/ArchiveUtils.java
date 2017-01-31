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

package org.springframework.boot.loader.thin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import org.springframework.boot.cli.compiler.RepositoryConfigurationFactory;
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext;
import org.springframework.boot.cli.compiler.grape.RepositoryConfiguration;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.thin.AetherEngine.ProgressType;
import org.springframework.boot.loader.tools.LogbackInitializer;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class ArchiveUtils {

	private static final String DEFAULT_BOM = "org.springframework.boot:spring-boot-dependencies:1.4.2.RELEASE";
	private ProgressType progress = ProgressType.SUMMARY;
	private ResourceLoader resources = new DefaultResourceLoader();
	private String[] locations = new String[] { "classpath:/", "file:." };

	static {
		LogbackInitializer.initialize();
	}

	/**
	 * Flag to indicate what level of progress report is needed
	 * @param progress the progress type to set
	 */
	public void setProgress(ProgressType progress) {
		this.progress = progress;
	}

	public void setLocations(String... locations) {
		this.locations = locations;
	}

	public static Archive getArchive(Class<?> cls) {
		URL location = cls.getProtectionDomain().getCodeSource().getLocation();
		return getArchive(location.toString());
	}

	public static Archive getArchive(String path) {
		File file = new File(findArchive(path));
		if (file.isDirectory()) {
			return new ExplodedArchive(file);
		}
		try {
			return new JarFileArchive(file);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create JAR archive: " + file, e);
		}
	}

	public static File getArchiveRoot(Archive archive) {
		try {
			return new File(jarFile(archive.getUrl()).toURI());
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot locate JAR archive: " + archive, e);
		}
	}

	public static String findMainClass(Archive archive) {
		String mainClass = null;
		try {
			Manifest manifest = archive.getManifest();
			if (manifest != null) {
				mainClass = manifest.getMainAttributes().getValue("Start-Class");
				if (mainClass != null) {
					return mainClass;
				}
				mainClass = manifest.getMainAttributes().getValue("Main-Class");
				if (mainClass != null) {
					return mainClass;
				}
			}
		}
		catch (Exception e) {
		}
		try {
			File root = getArchiveRoot(archive);
			if (archive instanceof ExplodedArchive) {
				return MainClassFinder.findSingleMainClass(root);
			}
			else {
				return MainClassFinder.findSingleMainClass(new JarFile(root), "");
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot locate main class in " + archive, e);
		}
	}

	private static URI findArchive(String path) {
		URI archive = findPath(path);
		if (archive != null) {
			try {
				return jarFile(archive.toURL()).toURI();
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot create URI for " + archive);
			}
		}
		File dir = new File("target/classes");
		if (dir.exists()) {
			return dir.toURI();
		}
		dir = new File("build/classes");
		if (dir.exists()) {
			return dir.toURI();
		}
		dir = new File(".");
		return dir.toURI();
	}

	private static URI findPath(String path) {
		if (path == null) {
			return null;
		}
		if (path.startsWith("maven:")) {
			// Resolving an explicit external archive
			String coordinates = path.replaceFirst("maven:\\/*", "");
			DependencyResolutionContext context = new DependencyResolutionContext();
			AetherEngine engine = AetherEngine.create(
					RepositoryConfigurationFactory.createDefaultRepositoryConfiguration(),
					context);
			try {
				List<File> resolved = engine.resolve(Arrays.asList(
						new Dependency(new DefaultArtifact(coordinates), "runtime")),
						false);
				return resolved.get(0).toURI();
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException("Cannot resolve archive: " + coordinates,
						e);
			}
		}
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}
		return new File(path).toURI();
	}

	private static URL[] locateFiles(URL[] urls) {
		for (int i = 0; i < urls.length; i++) {
			urls[i] = jarFile(urls[i]);
		}
		return urls;
	}

	private static URL jarFile(URL url) {
		String path = url.toString();
		if (path.endsWith("!/")) {
			path = path.substring(0, path.length() - "!/".length());
			if (path.startsWith("jar:")) {
				path = path.substring("jar:".length());
			}
			try {
				url = new URL(path);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Bad URL for jar file: " + path, e);
			}
		}
		return url;
	}

	public static URL[] addNestedClasses(Archive archive, URL[] urls, String... paths) {
		List<URL> extras = new ArrayList<>();
		try {
			for (String path : paths) {
				UrlResource classes = new UrlResource(archive.getUrl().toString() + path);
				if (classes.exists()) {
					extras.add(classes.getURL());
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot create urls for resources", e);
		}
		URL[] result = urls;
		if (!extras.isEmpty()) {
			extras.addAll(Arrays.asList(urls));
			result = extras.toArray(new URL[0]);
		}
		return locateFiles(result);
	}

	public List<Archive> subtract(Archive parent, Archive child, String name,
			String... profiles) {

		ArchiveDependencies parents = new ArchiveDependencies(parent, name, profiles);
		Map<String, Dependency> resolved = map(parents.collect());

		ArchiveDependencies childs = new ArchiveDependencies(child, name, profiles);
		childs.addBoms(parents.getBoms());
		childs.mergeExclusions(parents.getDependencies());
		ArrayList<File> result = new ArrayList<>();
		for (Dependency file : childs.collect()) {
			if (!resolved.containsKey(key(file))) {
				result.add(file.getArtifact().getFile());
			}
		}
		return archives(result);

	}

	private Map<String, Dependency> map(List<Dependency> collect) {
		Map<String, Dependency> map = new HashMap<>();
		for (Dependency dependency : collect) {
			map.put(key(dependency), dependency);
		}
		return map;
	}

	private String key(Dependency dependency) {
		return dependency.getArtifact().getGroupId() + ":"
				+ dependency.getArtifact().getArtifactId();
	}

	public List<Archive> extract(Archive root, String name, String... profiles) {

		ArchiveDependencies computed = new ArchiveDependencies(root, name, profiles);

		List<Archive> archives = archives(computed.resolve());

		if (this.progress == ProgressType.DETAILED) {
			System.out.println("Archives:");
			for (Archive dependency : archives) {
				System.out.println(" " + dependency);
			}
		}
		return archives;

	}

	private List<Archive> archives(List<File> files) {
		List<Archive> archives = new ArrayList<>();
		for (File file : files) {
			try {
				archives.add(new JarFileArchive(file, file.toURI().toURL()));
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot create archive", e);
			}
		}
		return archives;
	}

	class ArchiveDependencies {

		private Map<String, Dependency> dependencies = new LinkedHashMap<>();
		private Map<String, Dependency> managed = new LinkedHashMap<>();
		private Map<String, Dependency> boms = new LinkedHashMap<>();
		private Set<Dependency> exclusions = new LinkedHashSet<>();
		private boolean transitive = true;
		private PomLoader pomLoader;
		private String name;
		private List<String> profiles;
		private AetherEngine engine;

		public ArchiveDependencies(Archive root, String name, String... profiles) {
			this.name = name;
			this.profiles = new ArrayList<>(Arrays.asList(profiles));
			DependencyResolutionContext context = new DependencyResolutionContext();
			List<RepositoryConfiguration> repositories = RepositoryConfigurationFactory
					.createDefaultRepositoryConfiguration();
			engine = AetherEngine.create(repositories, context, progress);
			pomLoader = new PomLoader(engine);
			computeProperties(root);
		}

		public void mergeExclusions(Collection<Dependency> dependencies) {
			for (Dependency dependency : dependencies) {
				String key = key(dependency);
				Dependency target = this.dependencies.get(key);
				if (target != null && dependency.getExclusions() != null) {
					Collection<Exclusion> exclusions = target.getExclusions();
					if (exclusions == null) {
						exclusions = new HashSet<>();
					}
					else {
						exclusions = new HashSet<>(exclusions);
					}
					exclusions.addAll(dependency.getExclusions());
					addDependency(target.setExclusions(exclusions));
				}
			}
		}

		public void addBom(Dependency dependency) {
			this.boms.put(key(dependency), dependency);
		}

		public void addDependency(Dependency dependency) {
			this.dependencies.put(key(dependency), dependency);
		}

		public void addManaged(Dependency dependency) {
			this.managed.put(key(dependency), dependency);
		}

		public void removeDependency(Dependency dependency) {
			this.dependencies.remove(key(dependency));
			this.exclusions.add(dependency);
		}

		public void addBoms(Collection<Dependency> boms) {
			for (Dependency dependency : boms) {
				if ("pom".equals(dependency.getArtifact().getExtension())) {
					this.boms.put(key(dependency), dependency);
				}
			}
		}

		public void addDependencies(Collection<Dependency> dependencies) {
			for (Dependency dependency : dependencies) {
				this.dependencies.put(key(dependency), dependency);
			}
		}

		private String key(Dependency dependency) {
			return dependency.getArtifact().getGroupId() + ":"
					+ dependency.getArtifact().getArtifactId();
		}

		public Collection<Dependency> getDependencies() {
			return dependencies.values();
		}

		public Collection<Dependency> getBoms() {
			return boms.values();
		}

		public Dependency getDependency(String coordinates) {
			return dependencies.get(coordinates);
		}

		public Dependency getBom(String coordinates) {
			return boms.get(coordinates);
		}

		public boolean isTransitive() {
			return transitive;
		}

		private void prepare() throws ArtifactResolutionException {
			addParentBoms();
			engine.addDependencyManagementBoms(new ArrayList<>(boms.values()));
			engine.addDependencyManagement(new ArrayList<>(this.managed.values()));
			addExclusions();
			if (progress == ProgressType.DETAILED) {
				System.out.println("BOMs:");
				for (Dependency dependency : boms.values()) {
					System.out.println(" " + dependency);
				}
				System.out.println("Dependencies:");
				for (Dependency dependency : dependencies.values()) {
					System.out.println(" " + dependency);
					if (dependency.getExclusions() != null) {
						for (Exclusion exclude : dependency.getExclusions()) {
							System.out.println(" - " + exclude);
						}
					}
				}
			}

		}

		public List<Dependency> collect() {
			List<Dependency> files;
			try {
				prepare();
				files = engine.collect(new ArrayList<>(dependencies.values()));
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException("Cannot resolve artifacts", e);
			}
			return files;
		}

		public List<File> resolve() {
			List<File> files;
			try {
				prepare();
				files = engine.resolve(new ArrayList<>(dependencies.values()),
						transitive);
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException("Cannot resolve artifacts", e);
			}
			return files;
		}

		private void addParentBoms() throws ArtifactResolutionException {
			DependencyResolutionContext context = new DependencyResolutionContext();
			List<RepositoryConfiguration> repositories = RepositoryConfigurationFactory
					.createDefaultRepositoryConfiguration();
			AetherEngine engine = AetherEngine.create(repositories, context,
					ProgressType.NONE);
			List<Dependency> poms = engine.collect(new ArrayList<>(boms.values()), false);
			for (String bom : new HashSet<>(boms.keySet())) {
				// TODO: a better job of this maybe (or maybe it doesn't matter?)
				if (bom.contains("spring-boot-starter-parent")) {
					boms.remove(bom);
				}
			}
			for (Dependency pom : poms) {
				String parent = pomLoader
						.getParent(new FileSystemResource(pom.getArtifact().getFile()));
				while (parent != null) {
					Dependency dependency = dependency(parent, "pom", "import");
					if (!boms.containsKey(key(dependency))) {
						addBom(dependency);
					}
					List<Dependency> parents = engine.collect(Arrays.asList(dependency),
							false);
					if (parents.isEmpty()) {
						break;
					}
					parent = pomLoader.getParent(new FileSystemResource(
							parents.get(0).getArtifact().getFile()));
				}
			}
		}

		private void addExclusions() {
			if (this.exclusions.isEmpty()) {
				return;
			}
			try {
				Set<Dependency> collected = new HashSet<>(
						engine.collect(new ArrayList<>(this.dependencies.values())));
				for (Dependency target : collected) {
					Set<Exclusion> possible = new HashSet<>();
					possible.addAll(exclusions(Collections.singleton(target)));
					Collection<Exclusion> exclusions = target.getExclusions();
					if (exclusions == null) {
						exclusions = new HashSet<>();
					}
					else {
						exclusions = new HashSet<>(exclusions);
					}
					for (Exclusion exclusion : exclusions(this.exclusions)) {
						if (possible.contains(exclusion)) {
							exclusions.add(exclusion);
						}
					}
					addDependency(target.setExclusions(exclusions));
				}
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException(
						"Cannot collect transitive dependencies for: "
								+ dependencies.values(),
						e);
			}
		}

		private Collection<? extends Exclusion> exclusions(Set<Dependency> exclusions) {
			List<Exclusion> result = new ArrayList<>();
			for (Dependency dependency : exclusions) {
				result.add(new Exclusion(dependency.getArtifact().getGroupId(),
						dependency.getArtifact().getArtifactId(), "*", "*"));
			}
			return result;
		}

		private Properties computeProperties(Archive root) {

			// TODO: Maybe use something that conserves order?
			Properties properties = new Properties();
			loadThinProperties(properties, root);
			loadThinProperties(properties, ArchiveUtils.this.locations);
			this.pomLoader.loadThinProperties(getPom(root), properties);

			this.transitive = properties.getProperty("transitive.enabled", "true")
					.equals("true");
			for (String key : properties.stringPropertyNames()) {
				String lib = properties.getProperty(key);
				if (StringUtils.hasText(lib)) {
					if (key.startsWith("dependencies")) {
						addDependency(dependency(lib));
					}
					if (key.startsWith("boms")) {
						addBom(bom(lib));
					}
					if (key.startsWith("managed")) {
						addManaged(dependency(lib));
					}
					if (key.startsWith("exclusions")) {
						removeDependency(dependency(lib));
					}
				}
			}

			if (boms.isEmpty()) {
				addBom(bom(DEFAULT_BOM));
			}

			return properties;

		}

		private Resource getPom(Archive archive) {
			Resource pom;
			String artifactId;
			try {
				pom = new UrlResource(archive.getUrl() + "pom.xml");
				artifactId = extractArtifactId(archive);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Cannot locate archive", e);
			}
			if (!pom.exists()) {
				String pattern = "META-INF/maven/**"
						+ (artifactId == null ? "" : "/" + artifactId) + "/pom.xml";
				Resource resource = findResource(archive, pattern);
				if (resource != null) {
					return resource;
				}
				// Spring Boot fat jar
				pattern = "BOOT-INF/classes/META-INF/maven/**"
						+ (artifactId == null ? "" : "/" + artifactId) + "/pom.xml";
				resource = findResource(archive, pattern);
				if (resource != null) {
					return resource;
				}
				// Someone renamed the jar, so we don't know the artifactid
				pattern = "META-INF/maven/**/pom.xml";
				resource = findResource(archive, pattern);
				if (resource != null) {
					return resource;
				}
				// Last chance
				pattern = "**/META-INF/maven/**/pom.xml";
				resource = findResource(archive, pattern);
				if (resource != null) {
					return resource;
				}
			}
			return pom;
		}

		private Resource findResource(Archive archive, String pattern) {
			try {
				for (Resource resource : ResourcePatternUtils
						.getResourcePatternResolver(new DefaultResourceLoader())
						.getResources(archive.getUrl() + pattern)) {
					if (resource.exists()) {
						return resource;
					}
				}
			}
			catch (Exception e) {
			}
			return null;
		}

		private String extractArtifactId(Archive archive) throws MalformedURLException {
			String path = archive.getUrl().getPath();
			if (path.endsWith("!/")) {
				path = path.substring(0, path.length() - 2);
			}
			path = StringUtils.getFilename(path);
			path = path.split("-[0-9]")[0];
			return path;
		}

		private Dependency bom(String coordinates) {
			return dependency(coordinates, "pom", "import");
		}

		private Properties loadThinProperties(Properties props, Archive archive) {
			if (!profiles.contains("")) {
				profiles.add(0, "");
			}
			for (String profile : profiles) {
				String path = name + ("".equals(profile) ? "" : "-") + profile
						+ ".properties";
				loadProperties(props, archive, path);
			}
			return props;
		}

		private void loadThinProperties(Properties props, String... locations) {
			for (String profile : profiles) {
				String path = name + ("".equals(profile) ? "" : "-") + profile
						+ ".properties";
				for (String location : locations) {
					try {
						if (!location.endsWith("/")) {
							location = location + "/";
						}
						loadProperties(props, location, path);
					}
					catch (Exception e) {
						throw new IllegalStateException("Cannot load properties", e);
					}
				}
			}
		}

		private void loadProperties(Properties props, Archive archive, String path) {
			try {
				loadProperties(props, archive.getUrl().toString(), path);
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot load properties", e);
			}
		}

		private Properties loadProperties(Properties props, String url, String path) {
			try {
				Resource resource = resources.getResource(url)
						.createRelative("META-INF/" + path);
				if (resource.exists()) {
					if (progress == ProgressType.DETAILED) {
						System.out.println("Loading properties from archive: " + path);
					}
					PropertiesLoaderUtils.fillProperties(props, resource);
				}
				resource = resources.getResource(url).createRelative("/" + path);
				if (resource.exists()) {
					if (progress == ProgressType.DETAILED) {
						System.out.println("Loading properties from archive: " + path);
					}
					PropertiesLoaderUtils.fillProperties(props, resource);
				}
				return props;
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot load properties", e);
			}
		}

		private Dependency dependency(String coordinates) {
			return dependency(coordinates, "jar", "compile");
		}

		private Dependency dependency(String coordinates, String defaultExtension,
				String scope) {
			String[] parts = coordinates.split(":");
			if (parts.length < 2) {
				throw new IllegalArgumentException(
						"Co-ordinates should contain group:artifact[:extension][:classifier][:version]. Found "
								+ coordinates + ".");
			}
			String extension = defaultExtension, classifier, version, artifactId, groupId;
			if (parts.length > 4) {
				extension = parts[2];
				classifier = parts[3];
				version = parts[4];
			}
			else if (parts.length > 3) {
				if (parts[3].contains(".")) {
					version = parts[3];
					classifier = parts[2];
				}
				else {
					extension = parts[2];
					classifier = parts[3];
					version = null;
				}

			}
			else if (parts.length > 2) {
				if (parts[2].contains(".") || parts[2].contains("-RELEASE")
						|| parts[2].contains("-RC") || parts[2].contains("-SR")
						|| parts[2].matches("^[0-9].*")) {
					version = parts[2];
					classifier = null;
				}
				else {
					classifier = parts[2];
					version = null;
				}
			}
			else {
				classifier = null;
				version = null;
			}
			if ("".equals(classifier)) {
				classifier = null;
			}
			groupId = parts[0];
			artifactId = parts[1];
			return new Dependency(new DefaultArtifact(groupId, artifactId, classifier,
					extension, version), scope);
		}

	}

	public List<Archive> combine(Archive parent, Archive archive, String name,
			String[] profiles) {
		List<Archive> archives = new ArrayList<>();
		if (parent == null) {
			archives.addAll(extract(archive, name, profiles));
		}
		else {
			archives.addAll(extract(parent, name, profiles));
			archives.addAll(subtract(parent, archive, name, profiles));
		}
		if (!archives.isEmpty()) {
			archives.add(0, archive);
		}
		else {
			archives.add(archive);
		}
		return archives;
	}

}
