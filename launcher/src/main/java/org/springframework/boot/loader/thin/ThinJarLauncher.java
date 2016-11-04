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
import java.util.List;
import java.util.jar.JarFile;

import org.springframework.boot.loader.ExecutableArchiveLauncher;
import org.springframework.boot.loader.LaunchedURLClassLoader;
import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.Archive.Entry;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.env.Environment;
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
	 * System property holding the path to the root directory, where Maven repository and
	 * settings live. Defaults to <code>${user.home}/.m2</code>.
	 */
	public static final String THIN_ROOT = "thin.root";

	/**
	 * System property used by wrapper to communicate the location of the main archive.
	 */
	public static final String THIN_ARCHIVE = "thin.archive";

	private ArchiveFactory archives = new ArchiveFactory();
	private Environment environment = new StandardEnvironment();
	private boolean debug;

	public static void main(String[] args) throws Exception {
		new ThinJarLauncher().launch(args);
	}

	public ThinJarLauncher() throws Exception {
		this(computeArchive());
	}

	public ThinJarLauncher(Archive archive) {
		super(archive);
	}

	@Override
	protected void launch(String[] args) throws Exception {
		String root = environment.resolvePlaceholders("${" + THIN_ROOT + ":}");
		this.debug = !"false".equals(environment.resolvePlaceholders("${debug:false}"));
		this.archives.setDebug(debug);
		if (StringUtils.hasText(root)) {
			// There is a grape root that is used by the aether engine internally
			System.setProperty("grape.root", root);
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

	private static Archive computeArchive() throws Exception {
		File file = new File(findArchive());
		if (file.isDirectory()) {
			return new ExplodedArchive(file);
		}
		return new JarFileArchive(file);
	}

	private static URI findArchive() throws Exception {
		String path = System.getProperty(THIN_ARCHIVE);
		URI archive = path == null ? null : new URI(path);
		File dir = new File("target/classes");
		if (archive == null && dir.exists()) {
			archive = dir.toURI();
		}
		if (archive == null) {
			dir = new File("build/classes");
			if (dir.exists()) {
				archive = dir.toURI();
			}
		}
		if (archive == null) {
			dir = new File(".");
			archive = dir.toURI();
		}
		return archive;
	}

	@Override
	protected List<Archive> getClassPathArchives() throws Exception {
		List<Archive> archives = new ArrayList<>(this.archives.extract(getArchive()));
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
