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

package org.springframework.boot.experimental.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
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
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	/**
	 * Directory containing the downloaded archives.
	 */
	@Parameter(defaultValue = "${project.build.directory}/thin/root", required = true)
	private File outputDirectory;

	/**
	 * A list of the deployable thin libraries that must be downloaded and assembled.
	 */
	@Parameter
	private List<Dependency> deployables;

	/**
	 * A flag to indicate whether to include the current project as a deployable.
	 */
	@Parameter
	private boolean includeSelf = true;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (skip) {
			getLog().info("Skipping execution");
			return;
		}

		List<File> deployables = new ArrayList<>();
		if (this.includeSelf) {
			deployables.add(this.project.getArtifact().getFile());
		}
		if (this.deployables != null) {
			for (Dependency deployable : this.deployables) {
				deployables.add(resolveFile(deployable));
			}
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
		getLog().info("All deployables ready in: " + outputDirectory);

	}

}
