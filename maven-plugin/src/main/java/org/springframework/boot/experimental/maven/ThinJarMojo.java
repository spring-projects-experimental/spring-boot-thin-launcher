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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Settings;

import org.springframework.boot.loader.tools.JavaExecutable;
import org.springframework.boot.loader.tools.RunProcess;

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
public abstract class ThinJarMojo extends AbstractMojo {

	/**
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	/**
	 * The Maven settings.
	 */
	@Parameter(defaultValue = "${settings}", readonly = true, required = true)
	protected Settings settings;

	/**
	 * Skip the execution.
	 */
	@Parameter(property = "skip", defaultValue = "false")
	protected boolean skip;

	@Component
	private RepositorySystem repositorySystem;

	private static final int EXIT_CODE_SIGINT = 130;

	protected File resolveFile(Dependency deployable) {
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
			String localRepo = this.settings.getLocalRepository();
			String thinRepo = getThinRepo();
			String debug = getProperty("thin.debug");
			List<String> cmd = new ArrayList<>(
					Arrays.asList(new JavaExecutable().toString(), "-Dthin.dryrun",
							"-Dthin.root=.", "-jar", archive.getAbsolutePath()));
			if (localRepo != null) {
				getLog().debug("Local repo: " + localRepo);
				cmd.add(1, "-Dmaven.repo.local=" + localRepo);
			}
			if (debug != null && !"false".equals(debug)) {
				cmd.add(1, "-Dthin.debug=true");
			}
			if (thinRepo != null) {
				getLog().debug("Thin repo: " + thinRepo);
				cmd.add(1, "-Dthin.repo=" + thinRepo);
			}
			RunProcess runProcess = new RunProcess(workingDirectory,
					cmd.toArray(new String[0]));
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

	private String getThinRepo() {
		String repo = getProperty("thin.repo");
		if (repo != null) {
			return repo;
		}
		for (Mirror mirror : this.settings.getMirrors()) {
			String of = mirror.getMirrorOf();
			if ("*".equals(of) || "central".equals(of)
					|| (of != null && of.contains("spring"))) {
				return mirror.getUrl();
			}
		}
		return null;
	}

	private String getProperty(String key) {
		if (System.getProperty(key) != null) {
			return System.getProperty(key);
		}
		String KEY = key.replace(".", "_").toUpperCase();
		if (System.getenv(KEY) != null) {
			return System.getProperty(KEY);
		}
		Properties properties = project.getProperties();
		if (properties != null && properties.get(key) != null) {
			return properties.getProperty(key);
		}
		return null;
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
