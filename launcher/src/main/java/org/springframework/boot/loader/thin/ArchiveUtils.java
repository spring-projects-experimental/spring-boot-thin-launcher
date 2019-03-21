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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.io.UrlResource;

/**
 * @author Dave Syer
 *
 */
public class ArchiveUtils {

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
			DependencyResolver engine = DependencyResolver.instance();
			File resolved = engine
					.resolve(new Dependency(new DefaultArtifact(coordinates), "runtime"));
			return resolved.toURI();
		}
		if (path.startsWith("file:")) {
			path = path.substring("file:".length());
		}
		return new File(path).toURI();
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

	public static List<URL> nestedClasses(Archive archive, String... paths) {
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
		return extras;
	}

	public static URL[] addNestedClasses(Archive archive, URL[] urls, String... paths) {
		List<URL> extras = nestedClasses(archive, paths);
		URL[] result = urls;
		if (!extras.isEmpty()) {
			extras.addAll(Arrays.asList(urls));
			result = extras.toArray(new URL[0]);
		}
		return locateFiles(result);
	}

	private static URL[] locateFiles(URL[] urls) {
		for (int i = 0; i < urls.length; i++) {
			urls[i] = jarFile(urls[i]);
		}
		return urls;
	}

}
