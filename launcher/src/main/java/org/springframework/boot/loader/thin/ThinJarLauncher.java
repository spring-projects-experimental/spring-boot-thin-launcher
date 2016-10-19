/*
 * Copyright 2012-2016 the original author or authors.
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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.springframework.boot.cli.compiler.RepositoryConfigurationFactory;
import org.springframework.boot.cli.compiler.grape.AetherEngine;
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.Entry;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;

/**
 *
 * @author Dave Syer
 */
public class ThinJarLauncher extends ExecutableArchiveLauncher {

	public static void main(String[] args) throws Exception {
		new ThinJarLauncher().launch(args);
	}

	public ThinJarLauncher() throws Exception {
		super(computeArchive());
	}

	@Override
	protected void launch(String[] args) throws Exception {
		if (System.getProperty("main.dryrun") != null) {
			getClassPathArchives();
			return;
		}
		super.launch(args);
	}

	@Override
	protected String getMainClass() throws Exception {
		if (System.getProperty("main.class") != null) {
			return System.getProperty("main.class");
		}
		try {
			return super.getMainClass();
		}
		catch (IllegalStateException e) {
			File root = new File(getArchive().getUrl().toURI());
			if (getArchive() instanceof ExplodedArchive) {
				return MainClassFinder.findSingleMainClass(root);
			}
			else {
				return MainClassFinder.findSingleMainClass(new JarFile(root), "/");
			}
		}
	}

	private static Archive computeArchive() throws Exception {
		File file = new File(new URI(findArchive()));
		if (file.isDirectory()) {
			return new ExplodedArchive(file);
		}
		return new JarFileArchive(file);
	}

	private static String findArchive() {
		String archive = System.getProperty("main.archive");
		File dir = new File("target/classes");
		if (archive == null && dir.exists()) {
			archive = dir.toURI().toString();
		}
		dir = new File("build/classes");
		if (archive == null && dir.exists()) {
			archive = dir.toURI().toString();
		}
		dir = new File(".");
		if (archive == null) {
			archive = dir.toURI().toString();
		}
		return archive;
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		Map<String, Dependency> dependencies = new LinkedHashMap<>();
		// TODO: Maybe use something that conserves order?
		Properties libs = PropertiesLoaderUtils.loadProperties(
				new UrlResource(getArchive().getUrl() + "META-INF/lib.properties"));
		for (String key : libs.stringPropertyNames()) {
			String lib = libs.getProperty(key);
			dependencies.put(key, dependency(lib));
		}
		List<Archive> archives = archives(
				resolve(new ArrayList<>(dependencies.values())));
		archives.set(0, getArchive());
		return archives;
	}

	private List<Archive> archives(List<File> files) throws IOException {
		List<Archive> archives = new ArrayList<>();
		for (File file : files) {
			archives.add(new JarFileArchive(file, file.toURI().toURL()));
		}
		return archives;
	}

	private Dependency dependency(String lib) {
		return new Dependency(new DefaultArtifact(lib), "compile");
	}

	private List<File> resolve(List<Dependency> dependencies) throws Exception {
		AetherEngine engine = AetherEngine.create(
				RepositoryConfigurationFactory.createDefaultRepositoryConfiguration(),
				new DependencyResolutionContext());
		Method method = ReflectionUtils.findMethod(AetherEngine.class, "resolve",
				List.class);
		ReflectionUtils.makeAccessible(method);
		@SuppressWarnings("unchecked")
		List<File> files = (List<File>) ReflectionUtils.invokeMethod(method, engine,
				dependencies);
		return files;
	}

	@Override
	protected boolean isNestedArchive(Entry entry) {
		return false;
	}

}
