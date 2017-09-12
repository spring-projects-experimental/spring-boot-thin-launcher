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

package org.springframework.boot.loader.thin.classpath;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.thin.ThinJarLauncher;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import ch.qos.logback.classic.Level;

/**
 * Utility for printing classpath in various formats.
 * 
 * @author Dave Syer
 *
 */
public class ThinClasspathApplication {

	private static final Logger log = LoggerFactory
			.getLogger(ThinClasspathApplication.class);

	private static final String THIN_SOURCE = "thin.source";

	private static final String THIN_CLASSPATH = "thin.classpath";

	private StandardEnvironment environment = new StandardEnvironment();

	public static void main(String[] args) throws Exception {
		LogUtils.setLogLevel(Level.OFF);
		new ThinClasspathApplication(args).print();
	}

	private String[] args;

	private ThinClasspathApplication(String[] args) {
		addCommandLineProperties(args);
		this.args = args;
	}

	private void addCommandLineProperties(String[] args) {
		if (args == null || args.length == 0) {
			return;
		}
		MutablePropertySources properties = environment.getPropertySources();
		SimpleCommandLinePropertySource source = new SimpleCommandLinePropertySource(
				"commandArgs", args);
		if (!properties.contains("commandArgs")) {
			properties.addFirst(source);
		}
		else {
			properties.replace("commandArgs", source);
		}
	}

	private void print() throws Exception {
		File jarfile = new File(findSource());
		if (!jarfile.exists()) {
			System.err.println(
					"Archive does not exist or is not a file (try running with java -jar): "
							+ jarfile);
			return;
		}
		if (needsHelp(this.args, jarfile)) {
			help();
			return;
		}
		boolean trace = !"false"
				.equals(environment.resolvePlaceholders("${thin.trace:${trace:false}}"));
		if (trace) {
			LogUtils.setLogLevel(Level.DEBUG);
		}
		String classpathValue = environment
				.resolvePlaceholders("${" + THIN_CLASSPATH + ":true}");
		boolean classpath = "".equals(classpathValue) || "true".equals(classpathValue)
				|| "path".equals(classpathValue);
		boolean compute = "properties".equals(classpathValue);
		System.setProperty(ThinJarLauncher.THIN_ARCHIVE, jarfile.getAbsolutePath());
		if (classpath) {
			new ClasspathLauncher(args).print();
			return;
		}
		if (compute) {
			new DependencyLauncher(args).print();
			return;
		}
	}

	private String findSource() {
		String source = environment.getProperty(THIN_SOURCE);
		if (source == null) {
			source = System.getProperty("java.class.path");
			String separator = System.getProperty("path.separator");
			if (source.contains(separator)) {
				source = source.substring(source.indexOf(separator));
			}
		}
		if (source.startsWith("file:")) {
			source = source.substring("file:".length(), source.length());
			if (source.startsWith("//")) {
				source = source.substring(2);
			}
		}
		return source;
	}

	private void help() {
		System.out.println(
				"\nPrints the classpath for a thin executable jar or exploded archive.\n\n"
						+ "Usage: run a thin jar and make this one the --thin.library for it. E.g. \n\n"
						+ "    $ java -jar myapp.jar --thin.classpath\n\n"
						+ "The thin.classpath flag has 2 possible values (or it can be empty):\n\n"
						+ "    * path (the default): prints the path in a form suitable for the java command line\n"
						+ "    * properties: prints the path in properties form (can be piped straight to thin.properties)\n");
	}

	private boolean needsHelp(String[] args, File jarfile) {
		Set<String> strings = new HashSet<>(Arrays.asList(args));
		try {
			return this.environment.getProperty("help") != null || strings.contains("-h")
					|| jarfile.toURI().toURL().equals(getClass().getProtectionDomain()
							.getCodeSource().getLocation());
		}
		catch (MalformedURLException e) {
			return true;
		}
	}

	private static class DependencyLauncher extends ThinJarLauncher {

		protected DependencyLauncher(String[] args) throws Exception {
			super(args);
		}

		public void print() throws Exception {
			System.out.println(properties(getDependencies()));
		}

		private String properties(List<Dependency> dependencies) {
			StringBuilder builder = new StringBuilder("computed=true\n");
			for (Dependency dependency : dependencies) {
				builder.append("dependencies." + dependency.getArtifact().getArtifactId()
						+ "=" + coordinates(dependency.getArtifact()) + "\n");
			}
			return builder.toString();
		}

		static String coordinates(Artifact artifact) {
			// group:artifact:extension:classifier:version
			String classifier = artifact.getClassifier();
			String extension = artifact.getExtension();
			return artifact.getGroupId() + ":" + artifact.getArtifactId()
					+ (StringUtils.hasText(extension) && !"jar".equals(extension)
							? ":" + extension
							: (StringUtils.hasText(classifier) ? ":jar" : ""))
					+ (StringUtils.hasText(classifier) ? ":" + classifier : "") + ":"
					+ artifact.getVersion();
		}

	}

	private static class ClasspathLauncher extends ThinJarLauncher {

		protected ClasspathLauncher(String[] args) throws Exception {
			super(args);
		}

		public void print() throws Exception {
			System.out.println(classpath(getClassPathArchives()));
		}

		private String classpath(List<Archive> archives) throws Exception {
			StringBuilder builder = new StringBuilder();
			String separator = System.getProperty("path.separator");
			boolean first = true;
			for (Archive archive : archives) {
				if (!first) {
					first = false;
					continue;
				}
				if (builder.length() > 0) {
					builder.append(separator);
				}
				log.info("Archive: {}", archive);
				String uri = archive.getUrl().toURI().toString();
				if (uri.startsWith("jar:")) {
					uri = uri.substring("jar:".length());
				}
				if (uri.startsWith("file:")) {
					uri = uri.substring("file:".length());
				}
				if (uri.endsWith("!/")) {
					uri = uri.substring(0, uri.length() - "!/".length());
				}
				builder.append(new File(uri).getCanonicalPath());
			}
			return builder.toString();
		}

	}
}
