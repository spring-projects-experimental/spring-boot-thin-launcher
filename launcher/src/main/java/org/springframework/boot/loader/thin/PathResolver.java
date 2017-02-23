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

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.aether.graph.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
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
public class PathResolver {

	private static final Logger log = LoggerFactory.getLogger(PathResolver.class);

	private final DependencyResolver engine;

	private ResourceLoader resources = new DefaultResourceLoader();

	private String[] locations = new String[] { "classpath:/", "file:." };

	public PathResolver(DependencyResolver engine) {
		this.engine = engine;
	}

	public void setLocations(String... locations) {
		this.locations = locations;
	}

	public List<Archive> combine(Archive parent, Archive archive, String name,
			String... profiles) {
		List<Archive> archives = new ArrayList<>();
		if (parent == null) {
			archives.addAll(archives(extract(archive, name, profiles)));
		}
		else {
			archives.addAll(archives(extract(parent, archive, name, profiles)));
		}
		if (!archives.isEmpty()) {
			archives.add(0, archive);
		}
		else {
			archives.add(archive);
		}
		return archives;
	}

	private List<Dependency> extract(Archive parent, Archive archive, String name,
			String[] profiles) {
		Properties properties = getProperties(archive, name, profiles);
		Resource parentPom = getPom(parent);
		List<Dependency> parentDependencies = engine.dependencies(parentPom, properties);
		Resource childPom = getPom(archive);
		List<Dependency> childDependencies = engine.dependencies(childPom, properties);
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
		return parentDependencies;
	}

	private String coordinates(Dependency dependency) {
		return dependency.getArtifact().getGroupId() + ":"
				+ dependency.getArtifact().getArtifactId();
	}

	private List<Dependency> extract(Archive archive, String name, String[] profiles) {
		Properties properties = getProperties(archive, name, profiles);
		Resource pom = getPom(archive);
		log.info("Extracting dependencies from: {}", pom);
		return engine.dependencies(pom, properties);
	}

	private Properties getProperties(Archive archive, String name, String[] profiles) {
		Properties properties = new Properties();
		loadThinProperties(properties, archive, name, profiles);
		loadThinProperties(properties, this.locations, name, profiles);
		return properties;
	}

	private Properties loadThinProperties(Properties props, Archive archive, String name,
			String[] list) {
		List<String> profiles = new ArrayList<>(Arrays.asList(list));
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

	private void loadThinProperties(Properties props, String[] locations, String name,
			String[] profiles) {
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
				log.info("Loading properties from archive: " + path);
				PropertiesLoaderUtils.fillProperties(props, resource);
			}
			resource = resources.getResource(url).createRelative("/" + path);
			if (resource.exists()) {
				log.info("Loading properties from archive: " + path);
				PropertiesLoaderUtils.fillProperties(props, resource);
			}
			return props;
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot load properties", e);
		}
	}

	private List<Archive> archives(List<Dependency> dependencies) {
		List<Archive> list = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			File file = dependency.getArtifact().getFile();
			if (file == null) {
				continue;
			}
			try {
				list.add(new JarFileArchive(file, file.toURI().toURL()));
			}
			catch (Exception e) {
				throw new IllegalStateException("Cannot locate archive", e);
			}
		}
		return list;
	}

	public Resource getPom(Archive archive) {
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
		if (!pom.exists()) {
			pom = new ClassPathResource("META-INF/thin/empty-pom.xml");
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

}
