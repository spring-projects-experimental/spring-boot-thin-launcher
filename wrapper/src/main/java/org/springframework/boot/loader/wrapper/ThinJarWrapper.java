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
	public static final String THIN_SOURCE = "thin.source";

	/**
	 * Property key for remote location of main archive (the one that this class is found
	 * in).
	 */
	public static final String THIN_REPO = "thin.repo";

	/**
	 * Property key used to override the launcher main class if necessary. Defaults to
	 * <code>ThinJarLauncher</code>.
	 */
	public static final String THIN_LAUNCHER = "thin.launcher";

	private static final String DEFAULT_LAUNCHER_CLASS = "org.springframework.boot.loader.thin.ThinJarLauncher";

	private static final String DEFAULT_LIBRARY = "org.springframework.boot.experimental:spring-boot-thin-launcher:jar:exec:1.0.7.BUILD-SNAPSHOT";

	/**
	 * The file path of the library to launch (relative to the repository root).
	 */
	private String library;

	private Properties properties;

	public static void main(String[] args) throws Exception {
		Class<?> launcher = ThinJarWrapper.class;
		ThinJarWrapper wrapper = new ThinJarWrapper(args);
		if (wrapper.getProperty(THIN_ARCHIVE) == null) {
			System.setProperty(THIN_ARCHIVE, launcher.getProtectionDomain()
					.getCodeSource().getLocation().toURI().toString());
		}
		wrapper.launch(args);
	}

	ThinJarWrapper(String... args) {
		this.properties = properties(args);
		this.library = library();
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

	private String coordinates(String library) {
		if (library == null) {
			return DEFAULT_LIBRARY;
		}
		if (library.startsWith("maven://")) {
			library = library.substring("maven://".length());
		}
		return library;
	}

	private void launch(String... args) throws Exception {
		String repo = getProperty(THIN_REPO);
		repo = repo != null ? repo : "https://repo.spring.io/libs-snapshot";
		download(repo, this.library);
		ClassLoader classLoader = getClassLoader();
		String launcherClass = launcherClass();
		Class<?> launcher = classLoader.loadClass(launcherClass);
		findMainMethod(launcher).invoke(null, new Object[] { args(launcherClass, args) });
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

	private String launcherClass() {
		String launcher = getProperty(THIN_LAUNCHER);
		return launcher == null ? findLauncherClass() : launcher;
	}

	private String findLauncherClass() {
		JarFile jar = null;
		try {
			jar = new JarFile(mavenLocal() + this.library);
			Manifest manifest = jar.getManifest();
			if (manifest != null) {
				String mainClass = manifest.getMainAttributes().getValue("Main-Class");
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
		return DEFAULT_LAUNCHER_CLASS;
	}

	private Method findMainMethod(Class<?> launcher) throws NoSuchMethodException {
		return launcher.getMethod("main", String[].class);
	}

	private ClassLoader getClassLoader() throws Exception {
		URL[] urls = getUrls();
		URLClassLoader classLoader = new URLClassLoader(urls,
				ThinJarWrapper.class.getClassLoader().getParent());
		Thread.currentThread().setContextClassLoader(classLoader);
		return classLoader;
	}

	private URL[] getUrls() throws Exception {
		return new URL[] { new File(mavenLocal() + this.library).toURI().toURL() };
	}

	String mavenLocal() {
		return mavenLocal(getProperty(THIN_ROOT));
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

	String library() {
		String library = getProperty(THIN_LIBRARY);
		String coordinates = coordinates(library);
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
		String path = getArtifactPath(groupId, artifactId, version, classifier,
				extension);
		return path;
	}

	private String get(String value, String defaultValue) {
		return (value == null || value.length() <= 0) ? defaultValue : value;
	}

	private void download(String repo, String file) {
		String path = mavenLocal();
		String defaultPath = mavenLocal(null);
		File target = new File(path + file);
		if (!target.exists()) {
			if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
				throw new IllegalStateException(
						"Cannot create directory launcher at " + target);
			}
			boolean result = false;
			if (!defaultPath.equals(path)) {
				// Try local repo first
				result = downloadFromUrl(getUrl(defaultPath) + file, target);
			}
			if (!result) {
				result = downloadFromUrl(repo + file, target);
			}
			if (!result) {
				throw new IllegalStateException("Cannot download library: " + path);
			}
		}
	}

	private String getUrl(String file) {
		if (!file.startsWith(".") && !file.startsWith("/")) {
			file = "./" + file;
		}
		return "file://" + file;
	}

	private boolean downloadFromUrl(String path, File target) {
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

	private String getArtifactPath(String groupId, String artifactId, String version,
			String classifier, String extension) {
		return "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version + "/"
				+ artifactId + "-" + version
				+ (classifier != null ? "-" + classifier : "") + "." + extension;
	}

}
