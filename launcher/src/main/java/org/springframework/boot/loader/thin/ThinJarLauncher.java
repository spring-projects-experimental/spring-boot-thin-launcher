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

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import org.springframework.boot.cli.compiler.RepositoryConfigurationFactory;
import org.springframework.boot.cli.compiler.grape.DependencyResolutionContext;
import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.Entry;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.thin.AetherEngine.ProgressType;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

/**
 *
 * @author Dave Syer
 */
public class ThinJarLauncher extends ExecutableArchiveLauncher {

	/**
	 * System property key for main class to launch. Defaults to finding it via
	 * Start-Class of the main archive.
	 */
	public static final String THIN_MAIN = "thin.main";

	/**
	 * System property to signal a "dry run" where dependencies are resolved but the main
	 * method is not executed.
	 */
	public static final String THIN_DRYRUN = "thin.dryrun";

	/**
	 * System property to signal a "classpath run" where dependencies are resolved but the
	 * main method is not executed and the output is in the form of a classpath.
	 */
	public static final String THIN_CLASSPATH = "thin.classpath";

	/**
	 * System property holding the path to the root directory, where Maven repository and
	 * settings live. Defaults to <code>${user.home}/.m2</code>.
	 */
	public static final String THIN_ROOT = "thin.root";

	/**
	 * System property used by wrapper to communicate the location of the main archive.
	 * Can also be used in a dryrun or classpath launch to override the archive location
	 * with a file or "maven://..." style URL.
	 */
	public static final String THIN_ARCHIVE = "thin.archive";

	/**
	 * A parent archive (URL or "maven://..." locator) that controls the classpath and
	 * dependency management defaults for the main archive.
	 */
	public static final String THIN_PARENT = "thin.parent";

	/**
	 * The name of the launchable (i.e. the properties file name).
	 */
	public static final String THIN_NAME = "thin.name";

	/**
	 * The name of the profile to run, changing the location of the properties files to
	 * look up.
	 */
	public static final String THIN_PROFILE = "thin.profile";

	private ArchiveFactory archives = new ArchiveFactory();
	private StandardEnvironment environment = new StandardEnvironment();
	private boolean debug;

	public static void main(String[] args) throws Exception {
		new ThinJarLauncher(args).launch(args);
	}

	protected ThinJarLauncher(String[] args) throws Exception {
		super(computeArchive(args));
	}

	@Override
	protected void launch(String[] args) throws Exception {
		addCommandLineProperties(args);
		args = removeThinArgs(args);
		String root = environment.resolvePlaceholders("${" + THIN_ROOT + ":}");
		boolean classpath = !"false".equals(
				environment.resolvePlaceholders("${" + THIN_CLASSPATH + ":false}"));
		if (classpath) {
			this.debug = false;
			this.archives.setProgress(ProgressType.NONE);
		}
		else {
			this.debug = !"false"
					.equals(environment.resolvePlaceholders("${debug:false}"));
		}
		if (debug) {
			this.archives.setProgress(ProgressType.DETAILED);
		}
		if (StringUtils.hasText(root)) {
			// There is a grape root that is used by the aether engine
			// internally
			System.setProperty("grape.root", root);
		}
		if (classpath) {
			List<Archive> archives = getClassPathArchives();
			System.out.println(classpath(archives));
			return;
		}
		if (!"false".equals(
				environment.resolvePlaceholders("${" + THIN_DRYRUN + ":false}"))) {
			getClassPathArchives();
			if (this.debug) {
				System.out.println(
						"Downloaded dependencies" + (root == null ? "" : " to " + root));
			}
			return;
		}
		super.launch(args);
	}

	private String[] removeThinArgs(String[] args) {
		List<String> result = new ArrayList<>();
		for (String arg : args) {
			if ("--".equals(arg)) {
				break;
			}
			if (arg.startsWith("--thin.")) {
				continue;
			}
			result.add(arg);
		}
		return result.toArray(new String[0]);
	}

	private String classpath(List<Archive> archives) throws Exception {
		StringBuilder builder = new StringBuilder();
		String separator = System.getProperty("path.separator");
		for (Archive archive : archives) {
			if (archive.getUrl().toString().startsWith("jar:")) {
				continue;
			}
			if (builder.length() > 0) {
				builder.append(separator);
			}
			builder.append(new File(archive.getUrl().toURI()).getCanonicalPath());
		}
		return builder.toString();
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

	protected ClassLoader createClassLoader(URL[] urls) throws Exception {
		return new LaunchedURLClassLoader(urls, getClass().getClassLoader().getParent());
	}

	@Override
	protected String getMainClass() throws Exception {
		String mainClass = environment.resolvePlaceholders("${" + THIN_MAIN + ":}");
		if (StringUtils.hasText(mainClass)) {
			return mainClass;
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

	private static Archive computeArchive(String[] args) throws Exception {
		String path = getProperty(THIN_ARCHIVE);
		for (String arg : args) {
			String prefix = "--" + THIN_ARCHIVE;
			if (arg.startsWith(prefix)) {
				// You can always override --thin.archive on the command line
				if (arg.length() <= prefix.length() + 1) {
					// ... even cancel it by setting it to empty
					path = null;
				}
				else {
					path = arg.substring(prefix.length() + 1);
				}
			}
		}
		return computeArchive(path);
	}

	static String getProperty(String key) {
		if (System.getProperty(key) != null) {
			return System.getProperty(key);
		}
		return System.getenv(key.replace(".", "_").toUpperCase());
	}

	private static Archive computeArchive(String path) throws Exception {
		File file = new File(findArchive(path));
		if (file.isDirectory()) {
			return new ExplodedArchive(file);
		}
		return new JarFileArchive(file);
	}

	private static URI findArchive(String path) throws Exception {
		URI archive = findPath(path);
		if (archive != null) {
			return archive;
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

	private static URI findPath(String path) throws Exception {
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
				List<File> resolved = engine
						.resolve(
								Arrays.asList(new Dependency(
										new DefaultArtifact(coordinates), "runtime")),
								false);
				return resolved.get(0).toURI();
			}
			catch (ArtifactResolutionException e) {
				throw new IllegalStateException("Cannot resolve archive: " + coordinates,
						e);
			}
		}
		return new URI(path);
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		String parent = environment
				.resolvePlaceholders("${" + ThinJarLauncher.THIN_PARENT + ":}");
		String name = environment
				.resolvePlaceholders("${" + ThinJarLauncher.THIN_NAME + ":thin}");
		String[] prefixes = environment
				.resolvePlaceholders("${" + ThinJarLauncher.THIN_PROFILE + ":}")
				.split(",");
		List<Archive> archives = new ArrayList<>();
		if (!StringUtils.hasText(parent)) {
			archives.addAll(this.archives.extract(getArchive(), name, prefixes));
		}
		else {
			Archive parentArchive = computeArchive(parent);
			archives.addAll(this.archives.extract(parentArchive, name, prefixes));
			archives.addAll(this.archives.subtract(parentArchive, getArchive(), name, prefixes));
		}
		if (!archives.isEmpty()) {
			archives.add(0, getArchive());
		}
		else {
			archives.add(getArchive());
		}
		return archives;
	}

	@Override
	protected boolean isNestedArchive(Entry entry) {
		return false;
	}

}
