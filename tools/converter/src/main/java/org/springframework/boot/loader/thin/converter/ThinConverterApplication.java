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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.springframework.boot.loader.tools.JarWriter;
import org.springframework.boot.loader.tools.Repackager;
import org.springframework.util.StringUtils;

/**
 * Utility for converting a thin jar to a standard Spring Boot executable fat jar. Command
 * line arguments: Run the main method with no arguments (or --help) to print a usage
 * message.
 * 
 * @author Dave Syer
 *
 */
public class ThinConverterApplication {

	private static final String THIN_SOURCE = "thin.source";
	private static final String BOOT_VERSION_ATTRIBUTE = "Spring-Boot-Version";

	public static void main(String[] args) throws Exception {
		new ThinConverterApplication(args).repackage();
	}

	private String repository = "target/thin/root/repository";
	private String mainClass;
	private Properties properties;
	private String[] args;

	private ThinConverterApplication(String[] args) {
		this.properties = properties(args);
		this.args = args(args);
	}

	private static String[] args(String[] args) {
		List<String> result = new ArrayList<>();
		for (String arg : args) {
			if (!arg.startsWith("--")) {
				result.add(arg);
			}
		}
		return result.toArray(new String[result.size()]);
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

	private void repackage() throws IOException {
		File jarfile = new File(findSource());
		if (!jarfile.exists()) {
			System.err.println(
					"Archive does not exist or is not a file (try running with java -jar): " + jarfile);
			return;
		}
		if (this.args.length > 0) {
			this.repository = this.args[0];
		}
		if (this.args.length > 1) {
			this.mainClass = this.args[1];
		}
		if (needsHelp(this.args, jarfile)) {
			help();
			return;
		}
		File target = getTarget(jarfile);
		rewriteManifest(jarfile, target);
		System.out.println("Fat jar output: " + target);
		new Repackager(target).repackage(target, new PathLibraries(dependencies()));
	}

	private String findSource() {
		String source = null;
		if (this.properties.containsKey(THIN_SOURCE)) {
			source = this.properties.getProperty(THIN_SOURCE);
		}
		if (source == null) {
			source = System.getProperty(THIN_SOURCE);
			if (source == null) {
				source = System.getProperty("java.class.path");
				String separator = System.getProperty("path.separator");
				if (source.contains(separator)) {
					source = source.substring(source.indexOf(separator));
				}
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
				"\nConverts a thin executable jar to a Spring Boot fat jar with the same name but with '-exec' suffix.\n\n"
						+ "Usage: run a thin jar and make this one the --thin.library for it. E.g. \n\n"
						+ "    $ java -jar myapp.jar --thin.dryrun --thin.root=target/thin/root\n"
						+ "    $ java -jar myapp.jar --thin.library=org.springframework.boot.experimental:spring-boot-thin-converter:1.0.23.BUILD-SNAPSHOT\n"
						+ "    $ java -jar myapp-exec.jar\n\n" + //
						"  Optional args:\n\n" //
						+ "    repository - location of Maven repository cache (defaults to target/thin/root/repository)\n"
						+ "    mainClass  - name of main class in application (defaults to the one in the thin jar manifest)\n\n"
						+ "  *ALL* jars in the Maven repository will be packed into the fat jar (so don't use ~/.m2/repository).\n");
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
		if (this.mainClass != null) {
			manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, this.mainClass);
		}
		return manifest;
	}

	private Set<Path> dependencies() throws IOException {
		final Set<Path> files = new LinkedHashSet<>();
		Files.walkFileTree(Paths.get(this.repository), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException {
				String name = file.getFileName().toString();
				if (name.endsWith(".jar") && !name.startsWith("spring-boot-thin-launcher") && !isDuplicate(file)) {
					files.add(file);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}

	private boolean isDuplicate(Path file) {
		String name = file.getFileName().toString();
		String alt = name.replaceAll("[0-9]*\\.[0-9]*-[0-9]*", "SNAPSHOT");
		if (!name.equals(alt) && file.getParent().resolve(alt).toFile().exists()) {
			return true;
		}
		return false;
	}

	private File getTarget(File jarfile) {
		return new File(jarfile.getParentFile(),
				StringUtils.stripFilenameExtension(jarfile.getName()) + "-exec.jar");
	}

}
