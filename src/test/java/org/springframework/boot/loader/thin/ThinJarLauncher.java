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
import org.springframework.boot.cli.compiler.GroovyCompilerConfiguration;
import org.springframework.boot.cli.compiler.GroovyCompilerScope;
import org.springframework.boot.cli.compiler.RepositoryConfigurationFactory;
import org.springframework.boot.cli.compiler.grape.AetherEngine;
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext;
import org.springframework.boot.cli.compiler.grape.RepositoryConfiguration;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.Entry;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 *
 * @author Dave Syer
 */
public class ThinJarLauncher extends ExecutableArchiveLauncher {

	public static void main(String[] args) throws Exception {
		new ThinJarLauncher().launch(args);
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		Set<Dependency> dependencies = new LinkedHashSet<>();
		// TODO: Maybe use something that conserves order?
		Properties libs = PropertiesLoaderUtils
				.loadAllProperties("META-INF/lib.properties");
		for (String key : libs.stringPropertyNames()) {
			String lib = libs.getProperty(key);
			dependencies.add(dependency(lib));
		}
		List<Archive> archives = archives(resolve(new ArrayList<>(dependencies)));
		// archives.add(0, getArchive());
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
		GroovyCompilerConfiguration configuration = new LauncherConfiguration();
		AetherEngine engine = AetherEngine.create(
				configuration.getRepositoryConfiguration(),
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

	class LauncherConfiguration implements GroovyCompilerConfiguration {

		@Override
		public GroovyCompilerScope getScope() {
			return GroovyCompilerScope.DEFAULT;
		}

		@Override
		public boolean isGuessImports() {
			return true;
		}

		@Override
		public boolean isGuessDependencies() {
			return true;
		}

		@Override
		public boolean isAutoconfigure() {
			return true;
		}

		@Override
		public String[] getClasspath() {
			return DEFAULT_CLASSPATH;
		}

		@Override
		public List<RepositoryConfiguration> getRepositoryConfiguration() {
			return RepositoryConfigurationFactory.createDefaultRepositoryConfiguration();
		}

	}
}
