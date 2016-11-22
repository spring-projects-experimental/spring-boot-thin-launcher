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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.util.FileUtils;

import org.springframework.boot.loader.tools.JavaExecutable;
import org.springframework.boot.loader.tools.RunProcess;

/**
 * @author Dave Syer
 *
 */
@Mojo(name = "resolve", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ThinJarMojo extends AbstractMojo {

	/**
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Component
	private RepositorySystem repositorySystem;

	/**
	 * Directory containing the downloaded archives.
	 */
	@Parameter(defaultValue = "${project.build.directory}/thin/root", required = true)
	private File outputDirectory;

	/**
	 * Skip the execution.
	 */
	@Parameter(property = "skip", defaultValue = "false")
	private boolean skip;

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

	private static final int EXIT_CODE_SIGINT = 130;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (skip) {
			getLog().info("Skipping exection");
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

	private File resolveFile(Dependency deployable) {
		Artifact artifact = repositorySystem.createArtifactWithClassifier(
				deployable.getGroupId(), deployable.getArtifactId(),
				deployable.getVersion(), deployable.getType(),
				deployable.getClassifier());
		artifact = repositorySystem.resolve(getRequest(artifact)).getArtifacts()
				.iterator().next();
		return artifact.getFile();
	}

	protected void runWithForkedJvm(File archive, File workingDirectory, String... args)
			throws MojoExecutionException {

		try {
			RunProcess runProcess = new RunProcess(workingDirectory,
					new JavaExecutable().toString(), "-Dthin.dryrun", "-Dthin.root=.",
					"-jar", archive.getName());
			Runtime.getRuntime()
					.addShutdownHook(new Thread(new RunProcessKiller(runProcess)));
			getLog().debug("Running: " + archive);
			int exitCode = runProcess.run(true, args);
			if (exitCode == 0 || exitCode == EXIT_CODE_SIGINT) {
				return;
			}
			throw new MojoExecutionException(
					"Application finished with exit code: " + exitCode);
		}
		catch (Exception ex) {
			throw new MojoExecutionException("Could not exec java", ex);
		}
	}

	private ArtifactResolutionRequest getRequest(Artifact artifact) {
		ArtifactResolutionRequest request = new ArtifactResolutionRequest();
		request.setArtifact(artifact);
		request.setResolveTransitively(false);
		return request;
	}

	private static final class RunProcessKiller implements Runnable {

		private final RunProcess runProcess;

		private RunProcessKiller(RunProcess runProcess) {
			this.runProcess = runProcess;
		}

		@Override
		public void run() {
			this.runProcess.kill();
		}

	}

}
