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
package org.springframework.cloud.deployer.thin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.boot.loader.thin.DependencyResolver;
import org.springframework.boot.loader.thin.PathResolver;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.cloud.deployer.spi.task.LaunchState;
import org.springframework.core.io.Resource;
import org.springframework.util.ClassUtils;
import org.springframework.util.DigestUtils;
import org.springframework.util.ReflectionUtils;

public class ThinJarAppWrapper {

	private static Log logger = LogFactory.getLog(ThinJarAppWrapper.class);

	private String id;

	private Object app;

	private Object status;

	private Resource resource;

	private LaunchState state = LaunchState.unknown;

	private final String name;

	private final String[] profiles;

	public ThinJarAppWrapper(Resource resource, String name, String[] profiles) {
		this.resource = resource;
		this.name = name;
		this.profiles = profiles;
		try {
			this.id = DigestUtils.md5DigestAsHex(resource.getFile().getAbsolutePath()
					.getBytes(Charset.forName("UTF-8")));
		}
		catch (IOException e) {
			throw new IllegalArgumentException("Not a valid file resource");
		}
	}

	public void run(Map<String, String> properties, List<String> args) {
		if (this.app == null) {
			this.state = LaunchState.launching;
			ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
			try {
				Archive child = getRootArchive();
				Class<?> cls = createContextRunnerClass(child, args);
				this.app = cls.newInstance();
				runContext(getMainClass(child), properties, args.toArray(new String[0]));
				boolean running = isRunning();
				this.state = running ? LaunchState.running
						: (getError() != null ? LaunchState.failed
								: LaunchState.complete);
			}
			catch (Exception e) {
				this.state = LaunchState.failed;
				logger.error("Cannot deploy " + resource, e);
			}
			finally {
				ClassUtils.overrideThreadContextClassLoader(contextLoader);
			}
		}
	}

	private Archive getRootArchive() throws IOException {
		File file = resource.getFile();
		if (file.isDirectory()) {
			return new ExplodedArchive(file);
		}
		return new JarFileArchive(file);
	}

	private boolean isRunning() {
		if (app == null) {
			return false;
		}
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "isRunning");
		return (Boolean) ReflectionUtils.invokeMethod(method, this.app);
	}

	private Throwable getError() {
		if (app == null) {
			return null;
		}
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "getError");
		return (Throwable) ReflectionUtils.invokeMethod(method, this.app);
	}

	private void runContext(String mainClass, Map<String, String> properties,
			String... args) {
		Method method = ReflectionUtils.findMethod(this.app.getClass(), "run",
				String.class, Map.class, String[].class);
		ReflectionUtils.invokeMethod(method, this.app, mainClass, properties, args);
	}

	private Class<?> createContextRunnerClass(Archive child, List<String> args)
			throws Exception, ClassNotFoundException {
		Archive parent = createArchive();
		PathResolver archives = new PathResolver(DependencyResolver.instance());
		if (args.contains("--debug")) {
			// set log level
		}
		List<Archive> extracted = archives.resolve(child, name, profiles);
		ClassLoader loader = createClassLoader(extracted, parent, child);
		ClassUtils.overrideThreadContextClassLoader(loader);
		reset();
		Class<?> cls = loader.loadClass(ContextRunner.class.getName());
		return cls;
	}

	private void reset() {
		if (ClassUtils.isPresent(
				"org.apache.catalina.webresources.TomcatURLStreamHandlerFactory", null)) {
			setField(ClassUtils.resolveClassName(
					"org.apache.catalina.webresources.TomcatURLStreamHandlerFactory",
					null), "instance", null);
			setField(URL.class, "factory", null);
		}
	}

	private void setField(Class<?> type, String name, Object value) {
		Field field = ReflectionUtils.findField(type, name);
		ReflectionUtils.makeAccessible(field);
		ReflectionUtils.setField(field, null, value);
	}

	public void cancel() {
		if (isRunning()) {
			this.state = LaunchState.cancelled;
			close();
		}
	}

	private void close() {
		if (this.app != null) {
			try {
				Method method = ReflectionUtils.findMethod(this.app.getClass(), "close");
				ReflectionUtils.invokeMethod(method, this.app);
			}
			catch (Exception e) {
				this.state = LaunchState.error;
				logger.error("Cannot undeploy " + resource, e);
			}
			finally {
				reset();
				if (this.app != null) {
					try {
						((URLClassLoader) app.getClass().getClassLoader()).close();
						this.app = null;
					}
					catch (Exception e) {
						this.state = LaunchState.error;
						logger.error("Cannot clean up " + resource, e);
					}
					finally {
						this.app = null;
						System.gc();
					}
				}
			}
		}
	}

	public String getId() {
		return id;
	}

	public Object status() {
		return this.status;
	}

	public Object getApp() {
		return this.app;
	}

	public LaunchState getState() {
		if (!isRunning() && this.app != null) {
			close();
		}
		return this.state;
	}

	@Override
	public String toString() {
		return "Wrapper [id=" + id + ", resource=" + resource + ", state=" + state + "]";
	}

	protected String getMainClass(Archive archive) {
		try {
			Manifest manifest = archive.getManifest();
			String mainClass = null;
			if (manifest != null) {
				mainClass = manifest.getMainAttributes().getValue("Start-Class");
			}
			if (mainClass == null) {
				throw new IllegalStateException(
						"No 'Start-Class' manifest entry specified in " + this);
			}
			return mainClass;
		}
		catch (Exception e) {
			try {
				File root = new File(archive.getUrl().toURI());
				String mainClass = null;
				if (archive instanceof ExplodedArchive) {
					mainClass = MainClassFinder.findSingleMainClass(root);
				}
				else {
					mainClass = MainClassFinder.findSingleMainClass(new JarFile(root), "/");
				}
				return mainClass;
			}
			catch (Exception ex) {
				throw new IllegalStateException("Cannot find main class", e);
			}
		}
	}

	private ClassLoader createClassLoader(List<Archive> archives, Archive... roots) {
		URL[] urls = getUrls(archives, roots);
		URLClassLoader classLoader = new URLClassLoader(urls,
				getClass().getClassLoader().getParent());
		Thread.currentThread().setContextClassLoader(classLoader);
		return classLoader;
	}

	private URL[] getUrls(List<Archive> archives, Archive... roots) {
		try {
			List<URL> urls = new ArrayList<URL>(archives.size());
			for (Archive archive : archives) {
				urls.add(archive.getUrl());
			}
			for (int i = 0; i < roots.length; i++) {
				urls.add(i, roots[i].getUrl());
			}
			URL[] result = urls.toArray(new URL[0]);
			for (int i = 0; i < roots.length; i++) {
				result = ArchiveUtils.addNestedClasses(roots[i], result,
						"BOOT-INF/classes/");
			}
			return result;
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException("Cannot create URL", e);
		}
	}

	protected final Archive createArchive() {
		try {
			ProtectionDomain protectionDomain = getClass().getProtectionDomain();
			CodeSource codeSource = protectionDomain.getCodeSource();
			URI location;
			location = (codeSource == null ? null : codeSource.getLocation().toURI());
			String path = (location == null ? null : location.getSchemeSpecificPart());
			if (path == null) {
				throw new IllegalStateException(
						"Unable to determine code source archive");
			}
			File root = new File(path);
			if (!root.exists()) {
				throw new IllegalStateException(
						"Unable to determine code source archive from " + root);
			}
			return (root.isDirectory() ? new ExplodedArchive(root)
					: new JarFileArchive(root));
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannt create local archive", e);
		}
	}

	public void status(Object status) {
		this.status = status;
	}

}