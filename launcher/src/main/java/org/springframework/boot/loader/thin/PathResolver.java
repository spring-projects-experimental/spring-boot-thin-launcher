/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.loader.thin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.shared.utils.io.FileUtils;
import org.eclipse.aether.graph.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class PathResolver {

	private static final Logger log = LoggerFactory.getLogger(PathResolver.class);

	private final DependencyResolver engine;

	private ResourceLoader resources = new DefaultResourceLoader();

	private String[] locations = new String[] { "classpath:/", "file:." };

	private String root;

	private String libs;

	private Properties overrides = new Properties();

	private boolean offline;

	private boolean force;

	private boolean preferLocalSnapshots = true;

	public PathResolver(DependencyResolver engine) {
		this.engine = engine;
	}

	public void setLocations(String... locations) {
		this.locations = locations;
	}

	public void setRoot(String root) {
		this.root = root;
	}

	public void setLibs(String libs) {
		this.libs = libs;
	}

	public void setOverrides(Properties overrides) {
		this.overrides = overrides;
	}

	public void setForce(boolean force) {
		this.force = force;
	}

	public void setOffline(boolean offline) {
		this.offline = offline;
	}

	public List<Archive> resolve(Archive archive, String name, String... profiles) {
		return resolve(null, archive, name, profiles);
	}

	public List<Archive> resolve(Archive parent, Archive archive, String name, String... profiles) {
		log.info("Extracting dependencies from: {}, with profiles {}", archive, Arrays.asList(profiles));
		List<Archive> archives = new ArrayList<>();
		if (parent != null) {
			archives.addAll(archives(extract(parent, archive, name, profiles)));
		}
		else {
			archives.addAll(archives(extract(archive, name, profiles)));
		}
		addRootArchive(archives, archive);
		return archives;
	}

	public Resource getPom(Archive archive) {
		Resource pom;
		try {
			String base = archive.getUrl().toString();
			pom = new UrlResource(base + "pom.xml");
			if (!pom.exists()) {
				// Running from project (i.e. exploded but with pom.xml in the wrong
				// place). Sadly only works with Maven because Gradle splits the archive
				// over multiple directories.
				String path = "target/classes/";
				if (base.endsWith(path)) {
					pom = new UrlResource(base.substring(0, base.length() - path.length()) + "pom.xml");
				}
				if (!pom.exists()) {
					path = "target/test-classes/";
					if (base.endsWith(path)) {
						pom = new UrlResource(base.substring(0, base.length() - path.length()) + "pom.xml");
					}
				}
			}
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Cannot locate archive", e);
		}
		if (!pom.exists()) {
			String artifactId;
			try {
				artifactId = extractArtifactId(archive);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException("Cannot locate archive", e);
			}
			String pattern = "META-INF/maven/**"
					+ (artifactId == null || artifactId.length() == 0 ? "" : "/" + artifactId) + "/pom.xml";
			Resource resource = findResource(archive, pattern);
			if (resource != null) {
				return resource;
			}
			// Spring Boot fat jar
			pattern = "BOOT-INF/classes/META-INF/maven/**"
					+ (artifactId == null || artifactId.length() == 0 ? "" : "/" + artifactId) + "/pom.xml";
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
		if (!pom.exists()) {
			pom = new ClassPathResource("META-INF/thin/empty-pom.xml");
		}
		return pom;
	}

	private void addRootArchive(List<Archive> archives, Archive archive) {
		if (!archives.isEmpty()) {
			archives.add(0, archive);
		}
		else {
			archives.add(archive);
		}
	}

	private List<Dependency> extract(Archive parent, Archive archive, String name, String[] profiles) {
		Resource parentPom = getPom(parent);
		// Assume the profiles only apply to child
		List<Dependency> parentDependencies = engine.dependencies(parentPom,
				getProperties(archive, name, new String[0]));
		Resource childPom = getPom(archive);
		List<Dependency> childDependencies = engine.dependencies(childPom, getProperties(archive, name, profiles));
		Map<String, Dependency> lookup = new HashMap<>();
		for (Dependency dependency : parentDependencies) {
			lookup.put(coordinates(dependency), dependency);
		}
		for (Dependency dependency : childDependencies) {
			// This isn't the most sophisticated way to combine the two, but it works for
			// the cases we need so far...
			if (!lookup.containsKey(coordinates(dependency))) {
				parentDependencies.add(dependency);
			}
		}
		maybeCopyToRoot(this.root, getLocalRepository(), parentDependencies);
		return parentDependencies;
	}

	private void maybeCopyToRoot(String root, File repo, List<Dependency> classPathArchives) {
		if (root == null || !this.preferLocalSnapshots) {
			return;
		}
		// Otherwise copy any locally installed snapshots with the same version to the
		// root
		File dir = new File(root, "repository");
		if (!dir.exists() && !dir.mkdirs() || !dir.isDirectory()) {
			throw new IllegalStateException("Cannot create root directory: " + root);
		}
		try {
			String parent = dir.getCanonicalPath();
			for (Dependency archive : classPathArchives) {
				if (!archive.getArtifact().isSnapshot()) {
					continue;
				}
				File file = archive.getArtifact().getFile();
				String path = file.getCanonicalPath();
				if (!path.endsWith(".jar")) {
					continue;
				}
				if (!path.startsWith(parent)) {
					throw new IllegalStateException("Not in thin root repository: " + path);
				}
				String jar = path.substring(parent.length());
				File source = new File(repo, jar);
				File target = file;
				if (source.exists() && !FileUtils.contentEquals(source, target)) {
					log.info("Preferring local snapshot: " + archive);
					FileUtils.deleteDirectory(target.getParentFile());
					target.getParentFile().mkdirs();
					StreamUtils.copy(new FileInputStream(source), new FileOutputStream(target));
					String pom = jar.substring(0, jar.length() - 4) + ".pom";
					file = new File(repo, pom);
					if (file.exists()) {
						StreamUtils.copy(new FileInputStream(file), new FileOutputStream(new File(dir, pom)));
					}
				}
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot copy jar files", e);
		}
	}

	private String coordinates(Dependency dependency) {
		return dependency.getArtifact().getGroupId() + ":" + dependency.getArtifact().getArtifactId();
	}

	public List<Dependency> extract(Archive archive, String name, String[] profiles) {
		Properties properties = getProperties(archive, name, profiles);
		Resource pom = getPom(archive);
		log.info("Extracting dependencies from: {}, with profiles {}", pom, Arrays.asList(profiles));
		List<Dependency> dependencies = engine.dependencies(pom, properties);
		maybeCopyToRoot(this.root, getLocalRepository(), dependencies);
		return dependencies;
	}

	private Properties getProperties(Archive archive, String name, String[] profiles) {
		Properties properties = new Properties();
		loadThinProperties(properties, archive, name, profiles);
		loadThinProperties(properties, this.locations, name, profiles);
		if (profiles != null && profiles.length > 0) {
			String values = StringUtils.arrayToCommaDelimitedString(profiles);
			if (values.length() > 0) {
				properties.setProperty("thin.profile", values);
			}
		}
		if (root != null) {
			properties.setProperty("thin.root", root);
		}
		if (libs != null) {
			properties.setProperty("thin.libs", libs);
		}
		if (offline) {
			properties.setProperty("thin.offline", "true");
		}
		if (force) {
			properties.remove("computed");
		}
		addOverrideProperties(properties);
		return properties;
	}

	private void addOverrideProperties(Properties properties) {
		properties.putAll(overrides);
	}

	private Properties loadThinProperties(Properties props, Archive archive, String name, String[] list) {
		List<String> profiles = new ArrayList<>(Arrays.asList(list));
		if (!profiles.contains("")) {
			profiles.add(0, "");
		}
		for (String profile : profiles) {
			String path = name + ("".equals(profile) ? "" : "-") + profile + ".properties";
			loadProperties(props, archive, path);
		}
		return props;
	}

	private void loadThinProperties(Properties props, String[] locations, String name, String[] profiles) {
		for (String profile : profiles) {
			String path = name + ("".equals(profile) ? "" : "-") + profile + ".properties";
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
		log.info("Searching for properties in: " + url);
		Properties added = new Properties();
		try {
			Resource resource = resources.getResource(url).createRelative("META-INF/" + path);
			if (resource.exists()) {
				log.info("Loading properties from: " + resource);
				PropertiesLoaderUtils.fillProperties(added, resource);
			}
			resource = resources.getResource(url).createRelative("/" + path);
			if (resource.exists()) {
				log.info("Loading properties from: " + resource);
				PropertiesLoaderUtils.fillProperties(added, resource);
			}
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load properties", e);
		}
		if (!added.isEmpty()) {
			merge(props, added);
		}
		return props;
	}

	private void merge(Properties props, Properties added) {
		if (!props.isEmpty()) {
			for (Object key : new HashSet<>(added.keySet())) {
				String name = (String) key;
				if (name.startsWith("dependencies.")) {
					// Later (higher priority) profile dependencies trump earlier
					// exclusions
					// with the same key
					String artifact = name.substring("dependencies.".length());
					if (props.containsKey("exclusions." + artifact)) {
						props.remove("exclusions." + artifact);
					}
				}
				else if (name.startsWith("exclusions.")) {
					// Later (higher priority) profile exclusions trump earlier
					// dependencies
					// with the same key
					String artifact = name.substring("exclusions.".length());
					if (props.containsKey("dependencies." + artifact)) {
						props.remove("dependencies." + artifact);
					}
				}
			}
		}
		if ("true".equals(props.get("computed"))) {
			if (!"true".equals(added.get("computed"))) {
				// Ensure there are no added dependencies since they are not computed
				for (Object key : new HashSet<>(added.keySet())) {
					String name = (String) key;
					if (name.startsWith("dependencies.") || name.startsWith("boms.")) {
						added.remove(key);
					}
				}
			}
		}
		else {
			if ("true".equals(added.get("computed"))) {
				// Ensure there are no added dependencies since they are not computed
				for (Object key : new HashSet<>(props.keySet())) {
					String name = (String) key;
					if (name.startsWith("dependencies.") || name.startsWith("boms.")) {
						props.remove(key);
					}
				}
			}
		}
		props.putAll(added);
	}

	private List<Archive> archives(List<Dependency> dependencies) {
		List<Archive> list = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			File file = dependency.getArtifact().getFile();
			if (file == null) {
				continue;
			}
			try {
				// Archive is kind of the wrong abstraction here. We only need the URL, so
				// make that explicit.
				list.add(new UrlArchive(file.toURI().toURL()));
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot locate archive: " + file, e);
			}
		}
		return list;
	}

	private Resource findResource(Archive archive, String pattern) {
		try {
			for (Resource resource : ResourcePatternUtils.getResourcePatternResolver(new DefaultResourceLoader())
					.getResources(archive.getUrl() + pattern)) {
				if (resource.exists()) {
					if (resource.getFilename() !=null && resource.getFilename().contains("org.springframework.boot.experimental/spring-boot-thin-wrapper")) {
						// Explicitly ignore the thin wrapper itself
						continue;
					}
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
		path = path.replace(".jar", "");
		path = path.split("-[0-9]")[0];
		return path;
	}

	public File getLocalRepository() {
		return DependencyResolver.instance().getLocalRepository();
	}

	public void setPreferLocalSnapshots(boolean preferLocalSnapshots) {
		this.preferLocalSnapshots = preferLocalSnapshots;
	}

}
