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

package org.springframework.boot.loader.thin.converter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.springframework.boot.loader.tools.JarWriter;
import org.springframework.boot.loader.tools.Repackager;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class ThinConverterApplication {

	private static final String BOOT_VERSION_ATTRIBUTE = "Spring-Boot-Version";

	public static void main(String[] args) throws Exception {
		new ThinConverterApplication().repackage(args);
	}

	private String repository = "target/thin/root/repository";

	private void repackage(String[] args) throws IOException {
		File jarfile = new File(System.getProperty("java.class.path"));
		if (!jarfile.exists()) {
			System.err.println(
					"Archive does not exist (try running with java -jar): " + jarfile);
		}
		if (args.length > 0) {
			this.repository = args[0];
		}
		if (needsHelp(args, jarfile)) {
			help();
			return;
		}
		File target = getTarget(jarfile);
		rewriteManifest(jarfile, target);
		System.out.println("Fat jar output: " + target);
		new Repackager(target).repackage(target, new PathLibraries(dependencies()));
	}

	private void help() {
		System.out.println(
				"\nConverts a thin executable jar to a Spring Boot fat jar with the same name but with '-exec' suffix.\n");
		System.out.println(
				"Usage: run a thin jar and make this one the --thin.archive for it. E.g. \n");
		System.out.println(
				"    $ java -jar myapp.jar --thin.dryrun --thin.root=target/thin/root");
		System.out.println(
				"    $ java -jar myapp.jar --thin.archive=maven://org.springframework.boot.experimental:spring-boot-thin-converter:1.0.7.BUILD-SNAPSHOT");
		System.out.println("    $ java -jar myapp-exec.jar");
		System.out.println("\n  Optional args:\n");
		System.out.println(
				"    repository - location of Maven repository cache (defaults to target/thin/root/repository)");
		System.out.println(
				"\n  *ALL* jars in the Maven repository will be packed into the fat jar (so don't use ~/.m2/repository).\n");
	}

	private boolean needsHelp(String[] args, File jarfile) {
		Set<String> strings = new HashSet<>(Arrays.asList(args));
		File file = new File(this.repository);
		if (!file.exists() || !file.isDirectory()) {
			return true;
		}
		try {
			return strings.contains("--help") || strings.contains("-h")
					|| jarfile.toURI().toURL().equals(getClass().getProtectionDomain()
							.getCodeSource().getLocation());
		}
		catch (MalformedURLException e) {
			return true;
		}
	}

	private void rewriteManifest(File source, File target)
			throws FileNotFoundException, IOException {
		JarFile jarfile = new JarFile(source);
		JarWriter writer = new JarWriter(target);
		writer.writeManifest(buildManifest(jarfile));
		writer.writeEntries(jarfile);
		writer.close();
	}

	private Manifest buildManifest(JarFile archive) throws IOException {
		Manifest manifest = archive.getManifest();
		// The repackager looks for this attribute and bails out if it finds it
		manifest.getMainAttributes().remove(new Attributes.Name(BOOT_VERSION_ATTRIBUTE));
		// We also want to replace this with the default one from the repackager
		manifest.getMainAttributes().remove(Attributes.Name.MAIN_CLASS);
		return manifest;
	}

	private Set<Path> dependencies() throws IOException {
		final Set<Path> files = new LinkedHashSet<>();
		Files.walkFileTree(Paths.get(this.repository), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				if (file.getFileName().toString().endsWith(".jar")) {
					files.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}

	private File getTarget(File jarfile) {
		return new File(jarfile.getParentFile(),
				StringUtils.stripFilenameExtension(jarfile.getName()) + "-exec.jar");
	}

}
