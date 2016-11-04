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
	 * is found in).
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

	private static final String DEFAULT_LIBRARY = "org.springframework.boot.experimental:spring-boot-thin-launcher:0.0.1.BUILD-SNAPSHOT";

	private Library library;

	public static void main(String[] args) throws Exception {
		Class<?> launcher = ThinJarWrapper.class;
		System.setProperty(THIN_ARCHIVE, launcher.getProtectionDomain().getCodeSource()
				.getLocation().toURI().toString());
		new ThinJarWrapper().launch(args);
	}

	public ThinJarWrapper() {
		this.library = library();
	}

	private Library library() {
		String coordinates = System.getProperty(THIN_LIBRARY);
		return new Library(coordinates == null ? DEFAULT_LIBRARY : coordinates);
	}

	private void launch(String... args) throws Exception {
		ClassLoader classLoader = getClassLoader();
		Class<?> launcher = classLoader.loadClass(launcherClass());
		findMainMethod(launcher).invoke(null, new Object[] { args });
	}

	private String launcherClass() {
		String launcher = System.getProperty(THIN_LAUNCHER);
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
		library.download(mavenLocal());
		return new URL[] { new File(mavenLocal() + library.getPath()).toURI().toURL() };
	}

	private String mavenLocal() {
		return mvnHome() + "/repository";
	}

	private String mvnHome() {
		String home = System.getProperty(THIN_ROOT);
		if (home != null) {
			return home;
		}
		return home() + "/.m2";
	}

	private String home() {
		String home = System.getProperty("user.home");
		return home == null ? "." : home;
	}

	static class Library {

		private String coordinates;
		private String groupId;
		private String artifactId;
		private String version;
		private String classifier;

		public Library(String coordinates) {
			this.coordinates = coordinates;
			String[] parts = coordinates.split(":");
			if (parts.length < 3) {
				throw new IllegalArgumentException(
						"Co-ordinates should contain group:artifact[:classifier]:version");
			}
			if (parts.length > 3) {
				this.classifier = parts[2];
				this.version = parts[3];
			}
			else {
				this.version = parts[2];
			}
			this.groupId = parts[0];
			this.artifactId = parts[1];
		}

		public void download(String path) {
			File target = new File(path + getPath());
			if (!target.exists()) {
				String repo = repo();
				InputStream input = null;
				OutputStream output = null;
				try {
					input = new URL(repo + getPath()).openStream();
					if (target.getParentFile().mkdirs()) {
						output = new FileOutputStream(target);
						byte[] bytes = new byte[4096];
						int count = input.read(bytes);
						while (count > 0) {
							output.write(bytes, 0, count);
							count = input.read(bytes);
						}
					}
				}
				catch (Exception e) {
					throw new IllegalStateException(
							"Cannot download library for launcher " + coordinates, e);
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
			}
		}

		private static String repo() {
			String repo = System.getProperty(THIN_REPO);
			return repo != null ? repo : "https://repo.spring.io/libs-snapshot";
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

		public String getPath() {
			return "/" + groupId.replace(".", "/") + "/" + artifactId + "/" + version
					+ "/" + artifactId + "-" + version
					+ (classifier != null ? "-" + classifier : "") + ".jar";
		}

	}

}
