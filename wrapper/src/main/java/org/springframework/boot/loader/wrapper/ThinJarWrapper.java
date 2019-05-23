/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.boot.loader.wrapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dave Syer
 *
 */
public class ThinJarWrapper {

	/**
	 * Property key for the root where the launcher jar is downloaded and cached (level
	 * above /repository). Defaults to <code>${user.home}/.m2</code>.
	 */
	public static final String THIN_ROOT = "thin.root";

	/**
	 * Property key for the maven co-ordinates of the main library where the launcher
	 * class is located.
	 */
	public static final String THIN_LIBRARY = "thin.library";

	/**
	 * Property key used to store location of main archive (the one that this class is
	 * found in). Can also be used to run a different archive, resolving it via a URL or a
	 * "maven://group:artifact:version".
	 */
	public static final String THIN_ARCHIVE = "thin.archive";

	/**
	 * Property key for downstream tool to find archive.
	 */
	private static final String THIN_SOURCE = "thin.source";

	/**
	 * Property key for remote location of the launcher jar. Defaults to Maven Central
	 * with a fallback to the public Spring repos for snapshots and milestones. If you
	 * have a mirror for Maven Central in your local settings, that's the value you need
	 * here (the thin wrapper does not parse Maven settings).
	 */
	public static final String THIN_REPO = "thin.repo";

	/**
	 * Property key used to override the launcher main class if necessary. Defaults to
	 * <code>ThinJarLauncher</code>.
	 */
	public static final String THIN_LAUNCHER = "thin.launcher";

	/**
	 * Property key for flag to switch on debug logging to stderr.
	 */
	public static final String THIN_DEBUG = "thin.debug";

	/**
	 * Property key to override the location of local Maven cache. If the launcher jar is
	 * available here it will be used before trying the remote repo. Useful in cases where
	 * the local cache location is different from the target root.
	 */
	private static final String MAVEN_REPO_LOCAL = "maven.repo.local";

	private static final String DEFAULT_LAUNCHER_CLASS = "org.springframework.boot.loader.thin.ThinJarLauncher";

	private static final String DEFAULT_LIBRARY = "org.springframework.boot.experimental:spring-boot-thin-launcher:jar:exec:1.0.23.BUILD-SNAPSHOT";

	private Properties properties;

	private boolean debug;

	public static void main(String[] args) throws Exception {
		Class<?> launcher = ThinJarWrapper.class;
		ThinJarWrapper wrapper = new ThinJarWrapper(args);
		if (wrapper.getProperty(THIN_ARCHIVE) == null) {
			System.setProperty(THIN_ARCHIVE, new File(
					launcher.getProtectionDomain().getCodeSource().getLocation().toURI())
							.getAbsolutePath());
		}
		wrapper.launch(args);
	}

	ThinJarWrapper(String... args) {
		this.properties = properties(args);
		String debug = getProperty(THIN_DEBUG);
		this.debug = debug != null && !"false".equals(debug);
	}

	private static Properties properties(String[] args) {
		Properties properties = new Properties();
		for (String arg : args) {
			if (arg.startsWith("--") && arg.length() > 2) {
				arg = arg.substring(2);
				String[] split = arg.split("=");
				properties.setProperty(split[0].trim(),
						split.length > 1 ? split[1].trim() : "");
			}
		}
		return properties;
	}

	private void launch(String... args) throws Exception {
		String target = download();
		if (!new File(target).exists()) {
			throw new IllegalStateException("Cannot locate launcher: " + target);
		}
		if (this.debug) {
			System.err.println("Using launcher: " + target);
		}
		ClassLoader classLoader = getClassLoader(target);
		String launcherClass = launcherClass(target);
		Class<?> launcher = classLoader.loadClass(launcherClass);
		findMainMethod(launcher).invoke(null, new Object[] { args(launcherClass, args) });
	}

	String download() {
		String library = getProperty(THIN_LIBRARY);
		if (library != null && !library.startsWith("maven://")) {
			if (library.startsWith("file:")) {
				library = library.substring("file:".length());
				if (library.startsWith("//")) {
					library = library.substring("//".length());
				}
				return library;
			}
			if (library.contains("//")) {
				// it's a real URL, so download it
				String parent = thinRootRepository();
				String file = "/" + new File(library).getName();
				File target = new File(parent + file);
				downloadFromUrl(library, target);
				return parent + file;
			}
			if (library.endsWith(".jar")) {
				return library;
			}
		}
		String file = getArtifactPath(coordinates(library));
		String parent = thinRootRepository();
		File target = new File(parent + file);
		if (!target.exists()) {
			if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
				throw new IllegalStateException(
						"Cannot create directory for library at " + target);
			}
			boolean result = false;
			String defaultPath = mavenLocal();
			if (!defaultPath.equals(parent)) {
				// Try default local repo first
				result = downloadFromUrl(getUrl(defaultPath) + file, target);
			}
			if (!result) {
				String repo = getProperty(THIN_REPO);
				repo = repo != null ? repo : "https://repo.spring.io/libs-snapshot";
				if (repo.endsWith("/")) {
					repo = repo.substring(0, repo.length() - 1);
				}
				downloadFromUrl(repo + file, target);
			}
		}
		else {
			if (this.debug) {
				System.err.println("Cached launcher found: " + parent);
			}
		}
		return parent + file;
	}

	private String coordinates(String library) {
		if (library == null) {
			return DEFAULT_LIBRARY;
		}
		if (library.startsWith("maven://")) {
			library = library.substring("maven://".length());
		}
		return library;
	}

	private String[] args(String launcherClass, String[] args) {
		List<String> result = new ArrayList<>();
		for (String arg : args) {
			if (!arg.startsWith("--" + THIN_LIBRARY)) {
				result.add(arg);
			}
		}
		if (getClass().getName().equals(launcherClass)) {
			// Danger of infinite loop, re-executing the same archive over and over...
			String archive = getProperty(THIN_ARCHIVE);
			if (archive != null) {
				// Downstream tools have to look for thin.source, not thin.archive:
				System.setProperty(THIN_SOURCE, archive);
			}
			for (String arg : args) {
				if (arg.startsWith("--" + THIN_ARCHIVE)) {
					result.remove(arg);
				}
			}
			System.clearProperty(THIN_ARCHIVE);
		}
		return result.toArray(new String[result.size()]);
	}

	private String launcherClass(String library) {
		String launcher = getProperty(THIN_LAUNCHER);
		if (launcher == null) {
			JarFile jar = null;
			try {
				jar = new JarFile(library);
				Manifest manifest = jar.getManifest();
				if (manifest != null) {
					String mainClass = manifest.getMainAttributes()
							.getValue("Main-Class");
					if (mainClass != null) {
						return mainClass;
					}
				}
			}
			catch (IOException e) {
			}
			finally {
				if (jar != null) {
					try {
						jar.close();
					}
					catch (IOException e) {
					}
				}
			}
		}
		return launcher == null ? DEFAULT_LAUNCHER_CLASS : launcher;
	}

	private Method findMainMethod(Class<?> launcher) throws NoSuchMethodException {
		return launcher.getMethod("main", String[].class);
	}

	private ClassLoader getClassLoader(String library) throws Exception {
		URL[] urls = new URL[] { new File(library).toURI().toURL() };
		URLClassLoader classLoader = new URLClassLoader(urls,
				ThinJarWrapper.class.getClassLoader().getParent());
		Thread.currentThread().setContextClassLoader(classLoader);
		return classLoader;
	}

	String thinRootRepository() {
		String root = getProperty(THIN_ROOT);
		if (root == null) {
			return mavenLocal();
		}
		return mavenLocal(root);
	}

	String mavenLocal() {
		String repo = getProperty(MAVEN_REPO_LOCAL);
		if (repo != null) {
			return repo;
		}
		return mavenLocal(null);
	}

	private String mavenLocal(String home) {
		return mvnHome(home) + "/repository";
	}

	private String mvnHome(String home) {
		if (home != null) {
			return home;
		}
		return home() + "/.m2";
	}

	private String home() {
		String home = getProperty("user.home");
		return home == null ? "." : home;
	}

	String getProperty(String key) {
		if (properties != null && properties.getProperty(key) != null) {
			return properties.getProperty(key);
		}
		if (System.getProperty(key) != null) {
			return System.getProperty(key);
		}
		return System.getenv(key.replace(".", "_").toUpperCase());
	}

	private String get(String value, String defaultValue) {
		return (value == null || value.length() <= 0) ? defaultValue : value;
	}

	private String getUrl(String path) {
		if (!path.startsWith("./") && !path.startsWith("/")) {
			path = "./" + path;
		}
		File file = new File(path);
		return "file://" + file.getAbsolutePath();
	}

	private boolean downloadFromUrl(String path, File target) {
		if (this.debug) {
			System.err.println("Downloading launcher from: " + path);
		}
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new URL(path).openStream();
			output = new FileOutputStream(target);
			byte[] bytes = new byte[4096];
			int count = input.read(bytes);
			while (count > 0) {
				output.write(bytes, 0, count);
				count = input.read(bytes);
			}
			return true;
		}
		catch (Exception e) {
			if (this.debug) {
				System.err.println("Failed to download: " + path);
			}
		}
		finally {
			if (input != null) {
				try {
					input.close();
				}
				catch (Exception e) {
				}
			}
			if (output != null) {
				try {
					output.close();
				}
				catch (Exception e) {
				}
			}
		}
		return false;
	}

	private String getArtifactPath(String coordinates) {
		Pattern p = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
		Matcher m = p.matcher(coordinates);
		if (!m.matches()) {
			throw new IllegalArgumentException("Bad artifact coordinates " + coordinates
					+ ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
		}
		String groupId = m.group(1);
		String artifactId = m.group(2);
		String extension = get(m.group(4), "jar");
		String classifier = get(m.group(6), null);
		String version = m.group(7);
		return "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/"
				+ artifactId + "-" + version
				+ (classifier != null ? "-" + classifier : "") + "." + extension;
	}

}
