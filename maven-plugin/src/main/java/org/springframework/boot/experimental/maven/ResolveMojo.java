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

package org.springframework.boot.experimental.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.util.FileUtils;

/**
 * Resolves the dependencies for a thin jar artifact (or a set of them). The deployable
 * artifact is copied to <code>target/thin/root</code> by default, and then it is executed
 * with <code>-Dthin.root=.</code> in "dry run" mode. As a result, it can be executed
 * efficiently again from that directory, without downloading any more libraries. I.e.
 * 
 * <pre>
 * $ mvn package spring-boot-thin:resolve
 * $ cd target/thin/root
 * $ java -Dthin.root=. -jar *.jar
 * </pre>
 * 
 * @author Dave Syer
 *
 */
@Mojo(name = "resolve", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE, requiresDependencyCollection = ResolutionScope.NONE)
public class ResolveMojo extends ThinJarMojo {

	/**
	 * Directory containing the downloaded archives.
	 */
	@Parameter(defaultValue = "${project.build.directory}/thin/root", required = true, property = "thin.outputDirectory")
	private File outputDirectory;

	/**
	 * A list of the deployable thin libraries that must be downloaded and assembled.
	 */
	@Parameter
	private List<Dependency> deployables;

	/**
	 * A flag to indicate whether to include the current project as a deployable.
	 */
	@Parameter(property = "thin.includeSelf")
	private boolean includeSelf = true;

	/**
	 * A flag to indicate whether to unpack the main archive (self).
	 */
	@Parameter(property = "thin.unpack")
	private boolean unpack = false;

	/**
	 * To look up Archiver/UnArchiver implementations
	 */
	@Component
	protected ArchiverManager archiverManager;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (this.project.getPackaging().equals("pom")) {
			getLog().debug("Thin resolve goal could not be applied to pom project.");
			return;
		}
		if (skip) {
			getLog().info("Skipping execution");
			return;
		}

		outputDirectory.mkdirs();

		List<File> deployables = new ArrayList<>();
		File file = this.project.getArtifact().getFile();
		if (file != null && this.includeSelf && !this.unpack) {
			deployables.add(file);
		}
		if (this.deployables != null) {
			for (Dependency deployable : this.deployables) {
				if (deployable != null) {
					File resolved = resolveFile(deployable);
					if (resolved != null) {
						deployables.add(resolved);
					}
				}
			}
		}

		if (deployables.isEmpty()) {
			throw new MojoExecutionException(
					"No deployables found. If your only deployable is the current project jar, you need to run 'mvn package' at the same time.");
		}

		for (File deployable : deployables) {
			getLog().info("Deploying: " + deployable);
			try {
				getLog().info(
						"Copying: " + deployable.getName() + " to " + outputDirectory);

				FileUtils.copyFile(deployable,
						new File(outputDirectory, deployable.getName()));
				runWithForkedJvm(deployable, outputDirectory);
			}
			catch (Exception e) {
				throw new MojoExecutionException("Cannot locate deployable " + deployable,
						e);
			}
		}

		if (this.includeSelf && this.unpack) {
			try {
				runWithForkedJvm(file, outputDirectory);
				UnArchiver archiver = archiverManager.getUnArchiver(file);
				archiver.setSourceFile(file);
				archiver.setDestDirectory(outputDirectory);
				archiver.setOverwrite(true);
				archiver.setUseJvmChmod(true);
				archiver.extract();
			}
			catch (NoSuchArchiverException e) {
				throw new MojoExecutionException("Cannot unpack artifact " + file, e);
			}
		}

		if (!new File(outputDirectory, "repository").exists()
				|| new File(outputDirectory, "repository").listFiles().length == 0) {
			throw new MojoExecutionException(
					"No dependencies resolved. Is the thin layout applied to the Spring Boot plugin as a dependency?");
		}

		getLog().info("All deployables and dependencies ready in: " + outputDirectory);
	}

}
