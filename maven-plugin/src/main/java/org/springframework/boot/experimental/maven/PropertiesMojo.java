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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Resolves the dependencies for a thin jar artifact and outputs a thin properties file.
 * 
 * @author Dave Syer
 *
 */
@Mojo(name = "properties", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresProject = true, threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PropertiesMojo extends ThinJarMojo {

	/**
	 * The Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	/**
	 * Directory containing the generated file.
	 */
	@Parameter(defaultValue = "src/main/resources/META-INF", required = true, property = "thin.output")
	private File outputDirectory;

	/**
	 * A flag to indicate whether to compute transitive dependencies.
	 */
	@Parameter(property = "thin.compute", defaultValue = "true", required = true)
	private boolean compute;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		if (skip) {
			getLog().info("Skipping execution");
			return;
		}

		File deployable = this.project.getArtifact().getFile();
		getLog().info("Calculating properties for: " + deployable);
		Properties props = new Properties();
		try {
			File target = new File(outputDirectory, "thin.properties");
			if (target.exists()) {
				props.load(new FileInputStream(target));
			}
			props.setProperty("computed", "" + this.compute);
			for (Enumeration<?> keys = props.propertyNames(); keys.hasMoreElements();) {
				String key = (String) keys.nextElement();
				if (key.startsWith("dependencies.")) {
					props.remove(key);
				}
				if (key.startsWith("boms.")) {
					props.remove(key);
				}
			}

			boms(project, props);
			Set<Artifact> artifacts = this.compute ? project.getArtifacts()
					: project.getDependencyArtifacts();
			for (Artifact artifact : artifacts) {
				if ("runtime".equals(artifact.getScope())
						|| "compile".equals(artifact.getScope())) {
					props.setProperty("dependencies." + artifact.getArtifactId(),
							coordinates(artifact));
				}
			}
			props.store(new FileOutputStream(target), "Enhanced by thin jar maven plugin");
			getLog().info("Saved thin.properties");
		}
		catch (Exception e) {
			throw new MojoExecutionException(
					"Cannot calculate dependencies for: " + deployable, e);
		}

		getLog().info("Properties ready in: " + outputDirectory);

	}

	private void boms(MavenProject project, Properties props) {
		while (project != null) {
			String artifactId = project.getArtifactId();
			if (isBom(artifactId)) {
				props.setProperty("boms." + artifactId,
						coordinates(project.getArtifact(), true));
			}
			for (Dependency dependency : project.getDependencyManagement()
					.getDependencies()) {
				if ("import".equals(dependency.getScope())) {
					props.setProperty("boms." + dependency.getArtifactId(),
							coordinates(dependency));
				}
			}
			project = project.getParent();
		}
	}

	private boolean isBom(String artifactId) {
		return artifactId.endsWith("-dependencies")
				|| artifactId.endsWith("-bom");
	}

	private String coordinates(Dependency dependency) {
		return dependency.getGroupId() + ":" + dependency.getArtifactId() + ":"
				+ dependency.getVersion();
	}

	private String coordinates(Artifact dependency) {
		return coordinates(dependency, this.compute);
	}

	private String coordinates(Artifact dependency, boolean withVersion) {
		return dependency.getGroupId() + ":" + dependency.getArtifactId()
				+ (withVersion ? ":" + dependency.getVersion() : "");
	}

}
