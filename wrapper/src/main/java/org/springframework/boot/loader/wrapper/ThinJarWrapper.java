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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dave Syer
 *
 */
public class ThinJarWrapper {

	/**
	 * System property for the root where the launcher jar is downloaded and cached (level
	 * above /repository). Defaults to <code>${user.home}/.m2</code>.
	 */
	public static final String THIN_ROOT = "thin.root";

	/**
	 * System property key for the main library where the launcher class is located.
	 */
	public static final String THIN_LIBRARY = "thin.library";

	/**
	 * System property key used to store location of main archive (the one that this class
	 * is found in). Can also be used to run a different archive, resolving it via a URL
	 * or a "maven://group:artifact:version".
	 */
	public static final String THIN_ARCHIVE = "thin.archive";

	/**
	 * System property key for remote location of main archive (the one that this class is
	 * found in).
	 */
	public static final String THIN_REPO = "thin.repo";

	/**
	 * System property key used to override the launcher main class if necessary. Defaults
	 * to <code>ThinJarLauncher</code>.
	 */
	public static final String THIN_LAUNCHER = "thin.launcher";

	private static final String DEFAULT_LAUNCHER_CLASS = "org.springframework.boot.loader.thin.ThinJarLauncher";

	private static final String DEFAULT_LIBRARY = "org.springframework.boot.experimental:spring-boot-thin-launcher:jar:exec:1.0.5.BUILD-SNAPSHOT";

	private Library library;

	private Properties args;

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
		this.args = properties(args);
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

	Library library() {
		String coordinates = getProperty(THIN_LIBRARY);
		String repo = getProperty(THIN_REPO);
		repo = repo != null ? repo : "https://repo.spring.io/libs-snapshot";
		return new Library(coordinates == null ? DEFAULT_LIBRARY : coordinates, repo);
	}

	private void launch(String... args) throws Exception {
		ClassLoader classLoader = getClassLoader();
		Class<?> launcher = classLoader.loadClass(launcherClass());
		findMainMethod(launcher).invoke(null, new Object[] { args });
	}

	private String launcherClass() {
		String launcher = getProperty(THIN_LAUNCHER);
		return launcher == null ? DEFAULT_LAUNCHER_CLASS : launcher;
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
		library.download(mavenLocal(), mavenLocal(null));
		return new URL[] { new File(mavenLocal() + library.getPath()).toURI().toURL() };
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
		if (args != null && args.getProperty(key) != null) {
			return args.getProperty(key);
		}
		if (System.getProperty(key) != null) {
			return System.getProperty(key);
		}
		return System.getenv(key.replace(".", "_").toUpperCase());
	}

	static class Library {

		private String coordinates;
		private String groupId;
		private String artifactId;
		private String version;
		private String classifier;
		private String extension;
		private String repo;

		public Library(String coordinates, String repo) {
			this.coordinates = coordinates;
			this.repo = repo;
			Pattern p = Pattern
					.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
			Matcher m = p.matcher(coordinates);
			if (!m.matches()) {
				throw new IllegalArgumentException("Bad artifact coordinates "
						+ coordinates
						+ ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
			}
			this.groupId = m.group(1);
			this.artifactId = m.group(2);
			this.extension = get(m.group(4), "jar");
			this.classifier = get(m.group(6), null);
			this.version = m.group(7);
		}

		private static String get(String value, String defaultValue) {
			return (value == null || value.length() <= 0) ? defaultValue : value;
		}

		public void download(String path, String defaultPath) {
			if (path == null) {
				path = defaultPath;
			}
			File target = new File(path + getPath());
			if (!target.exists()) {
				if (!target.getParentFile().exists()
						&& !target.getParentFile().mkdirs()) {
					throw new IllegalStateException(
							"Cannot create directory launcher at " + target);
				}
				boolean result = false;
				if (!defaultPath.equals(path)) {
					// Try local repo first
					result = downloadFromUrl(getUrl(defaultPath), target);
				}
				if (!result) {
					result = downloadFromUrl(this.repo, target);
				}
				if (!result) {
					throw new IllegalStateException(
							"Cannot download library for launcher " + coordinates);
				}
			}
		}

		private String getUrl(String file) {
			if (!file.startsWith(".") && !file.startsWith("/")) {
				file = "./" + file;
			}
			return "file://" + file;
		}

		private boolean downloadFromUrl(String repo, File target) {
			InputStream input = null;
			OutputStream output = null;
			try {
				input = new URL(repo + getPath()).openStream();
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

		public String getCoordinates() {
			return coordinates;
		}

		public String getGroupId() {
			return groupId;
		}

		public String getArtifactId() {
			return artifactId;
		}

		public String getVersion() {
			return version;
		}

		public String getClassifier() {
			return classifier;
		}

		public String getExtension() {
			return extension;
		}

		public String getPath() {
			return "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version
					+ "/" + artifactId + "-" + version
					+ (classifier != null ? "-" + classifier : "") + "." + this.extension;
		}

	}

}
