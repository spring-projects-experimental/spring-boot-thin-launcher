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
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import org.springframework.boot.cli.compiler.RepositoryConfigurationFactory;
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class ArchiveFactory {

	private static final String DEFAULT_BOM = "org.springframework.boot:spring-boot-dependencies:pom:1.4.1.RELEASE";
	private boolean debug;

	/**
	 * Flag to say that we want verbose output on stdout.
	 * 
	 * @param debug the debug flag to set
	 */
	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public List<Archive> subtract(Archive parent, Archive child) {

		ArchiveDependencies parents = new ArchiveDependencies(parent);
		List<File> resolved = parents.resolve();

		ArchiveDependencies childs = new ArchiveDependencies(child);
		childs.addBoms(parents.getBoms());
		childs.mergeExclusions(parents.getDependencies());
		ArrayList<File> result = new ArrayList<>();
		for (File file : childs.resolve()) {
			if (!resolved.contains(file)) {
				result.add(file);
			}
		}
		return archives(result);

	}

	public List<Archive> extract(Archive root) throws Exception {

		ArchiveDependencies computed = new ArchiveDependencies(root);
		Collection<Dependency> dependencies = computed.getDependencies();
		Collection<Dependency> boms = computed.getBoms();

		if (this.debug) {
			System.out.println("BOMs:");
			for (Dependency dependency : boms) {
				System.out.println(" " + dependency);
			}
			System.out.println("Dependencies:");
			for (Dependency dependency : dependencies) {
				System.out.println(" " + dependency);
				if (dependency.getExclusions() != null) {
					for (Exclusion exclude : dependency.getExclusions()) {
						System.out.println(" - " + exclude);
					}
				}
			}
		}

		List<Archive> archives = archives(computed.resolve());

		if (this.debug) {
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

	private Properties loadLibraryProperties(Archive archive) {
		UrlResource resource;
		try {
			resource = new UrlResource(archive.getUrl() + "META-INF/lib.properties");
			Properties props = resource.exists()
					? PropertiesLoaderUtils.loadProperties(resource) : new Properties();
			FileSystemResource local = new FileSystemResource("lib.properties");
			if (local.exists()) {
				PropertiesLoaderUtils.fillProperties(props, local);
			}
			return props;
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load properties", e);
		}
	}

	class ArchiveDependencies {
		private Map<String, Dependency> dependencies = new LinkedHashMap<>();
		private Map<String, Dependency> boms = new LinkedHashMap<>();
		private boolean transitive = true;
		private PomLoader pomLoader = new PomLoader();

		public ArchiveDependencies(Archive root) {
			compute(root);
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

		public void addBoms(Collection<Dependency> boms) {
			for (Dependency dependency : boms) {
				this.boms.put(key(dependency), dependency);
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

		public List<File> resolve() {
			DependencyResolutionContext context = new DependencyResolutionContext();
			AetherEngine engine = AetherEngine.create(
					RepositoryConfigurationFactory.createDefaultRepositoryConfiguration(),
					context);
			engine.addDependencyManagementBoms(new ArrayList<>(boms.values()));
			List<File> files;
			try {
				files = engine.resolve(new ArrayList<>(dependencies.values()),
						transitive);
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException("Cannot resolve artifacts", e);
			}
			return files;
		}

		private void compute(Archive root) {

			// TODO: Maybe use something that conserves order?
			Properties libs = loadLibraryProperties(root);
			this.transitive = libs.getProperty("transitive.enabled", "true")
					.equals("true");
			for (String key : libs.stringPropertyNames()) {
				String lib = libs.getProperty(key);
				if (key.startsWith("dependencies")) {
					Dependency dependency = dependency(lib);
					addDependency(dependency);
				}
				if (key.startsWith("boms")) {
					addBom(bom(lib));
				}
			}

			addBoms(getPomDependencyManagement(root));
			addDependencies(getPomDependencies(root));

			if (boms.isEmpty()) {
				addBom(dependency(DEFAULT_BOM));
			}

		}

		private List<Dependency> getPomDependencies(Archive archive) {
			return this.pomLoader.getDependencies(getPom(archive));
		}

		private List<Dependency> getPomDependencyManagement(Archive archive) {
			return this.pomLoader.getDependencyManagement(getPom(archive));
		}

		private Resource getPom(Archive archive) {
			Resource pom;
			try {
				pom = new UrlResource(archive.getUrl() + "pom.xml");
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Cannot locate pom", e);
			}
			if (!pom.exists()) {
				try {
					for (Resource resource : ResourcePatternUtils
							.getResourcePatternResolver(new DefaultResourceLoader())
							.getResources(
									archive.getUrl() + "META-INF/maven/**/pom.xml")) {
						if (resource.exists()) {
							return resource;
						}
					}
				}
				catch (Exception e) {
				}
			}
			return pom;
		}

		private Dependency bom(String coordinates) {
			if (!coordinates.contains(":pom")) {
				String[] parts = coordinates.split(":");
				String[] result = new String[parts.length + 1];
				for (int i = 0; i < result.length - 2; i++) {
					result[i] = parts[i];
				}
				result[parts.length - 1] = "pom:";
				result[parts.length] = parts[parts.length - 1];
				coordinates = StringUtils.arrayToDelimitedString(result, ":");
			}
			return dependency(coordinates);
		}

		private Dependency dependency(String coordinates) {
			String[] parts = coordinates.split(":");
			if (parts.length < 2) {
				throw new IllegalArgumentException(
						"Co-ordinates should contain group:artifact[:extension][:classifier][:version]. Found "
								+ coordinates + ".");
			}
			String extension = "jar", classifier, version, artifactId, groupId;
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
				if (parts[2].contains(".")) {
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
					extension, version), "compile");
		}

	}

}
