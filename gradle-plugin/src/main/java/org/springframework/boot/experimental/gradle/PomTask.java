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

package org.springframework.boot.experimental.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import groovy.util.Node;

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension;
import io.spring.gradle.dependencymanagement.maven.PomDependencyManagementConfigurer;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.MavenPluginConvention;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskExecutionException;
import org.springframework.util.ClassUtils;

/**
 * Task to generate a pom file including all runtime dependencies.
 * 
 * @author Andy Wilkinson
 * @author Dave Syer
 *
 */
public class PomTask extends DefaultTask {

	@OutputDirectory
	private File output = new File("./build/resources/main");

	@TaskAction
	public void generate() {
		try {
			getLogger().info("Output: " + output);
			DependencyManagementExtension dependencies = getProject().getExtensions()
					.findByType(DependencyManagementExtension.class);
			if (dependencies != null) {
				PomDependencyManagementConfigurer pomConfigurer = dependencies.getPomConfigurer();
				Convention maven = null;
				if (ClassUtils.isPresent("org.gradle.api.plugins.MavenPluginConvention", null)) {
					// This class is not present in Gradle 7 unless the user explicitly asks for the "maven" plugin
					maven = (Convention) getProject().getConvention().findPlugin(
							ClassUtils.resolveClassName("org.gradle.api.plugins.MavenPluginConvention", null));
				}
				if (maven != null) {
					getLogger().info("Generating pom.xml with maven plugin");
					output.mkdirs();
					MavenPom pom = (MavenPom) maven.getByName("pom");
					pom.withXml(new RepositoryAdder(getProject()));
					pom.withXml(pomConfigurer).writeTo(new File(output, "pom.xml"));
				} else {
					TaskCollection<GenerateMavenPom> tasks = getProject().getTasks().withType(GenerateMavenPom.class);
					if (!tasks.isEmpty()) {
						getLogger().info("Generating pom.xml with maven-publish plugin");
						output.mkdirs();
						GenerateMavenPom plugin = tasks.iterator().next();
						plugin.setDestination(new File(output, "pom.xml"));
						plugin.getPom().withXml(new RepositoryAdder(getProject()));
						plugin.doGenerate();
					} else {
						getLogger().warn(
								"Skipping pom generation (maybe you forgot to apply plugin: 'maven' or 'maven-publish'?)");
					}
				}
			} else {
				getLogger().warn(
						"Skipping pom generation (maybe you forgot to apply plugin: 'io.spring.dependency-management'?)");
			}
		} catch (Exception e) {
			throw new TaskExecutionException(this, e);
		}
	}

	/**
	 * Sets the location to which the properties file will be written. Defaults to
	 * META-INF in the compiled classes output
	 * (build/resources/main/META-INF/${project.group}/${project.name}).
	 *
	 * @param output the output location
	 */
	public void setOutput(File output) {
		this.output = output;
	}

	public File getOutput() {
		return output;
	}

	private final class RepositoryAdder implements Action<XmlProvider> {
		private static final String NODE_NAME_REPOSITORIES = "repositories";
		private static final String NODE_NAME_REPOSITORY = "repository";
		private static final String NODE_NAME_ID = "id";
		private static final String NODE_NAME_URL = "url";
		private Project project;

		public RepositoryAdder(Project project) {
			this.project = project;
		}

		@Override
		public void execute(XmlProvider xml) {
			List<MavenArtifactRepository> repos = new ArrayList<>();
			for (ArtifactRepository repo : project.getRepositories()) {
				if (repo instanceof MavenArtifactRepository) {
					MavenArtifactRepository maven = (MavenArtifactRepository) repo;
					String proto = "" + maven.getUrl().getScheme();
					String host = maven.getUrl().getHost();
					if (proto.startsWith("http") && !host.contains("repo.maven.apache.org")) {
						repos.add(maven);
					}
				}
			}
			if (repos.isEmpty()) {
				return;
			}
			configurePom(xml.asNode(), repos);
		}

		private void configurePom(Node pom, List<MavenArtifactRepository> repos) {
			Node repositoriesNode = findChild(pom, NODE_NAME_REPOSITORIES);
			if (repositoriesNode == null) {
				repositoriesNode = pom.appendNode(NODE_NAME_REPOSITORIES);
			}
			configureRepositories(repositoriesNode, repos);
		}

		private void configureRepositories(Node repositoriesNode, List<MavenArtifactRepository> repos) {
			for (MavenArtifactRepository repo : repos) {
				Node node = repositoriesNode.appendNode(NODE_NAME_REPOSITORY);
				node.appendNode(NODE_NAME_ID, repo.getName());
				node.appendNode(NODE_NAME_URL, repo.getUrl().toString());
			}
		}

		private Node findChild(Node node, String name) {
			for (Object childObject : node.children()) {
				if ((childObject instanceof Node) && ((Node) childObject).name().equals(name)) {
					return (Node) childObject;
				}

			}
			return null;
		}
	}

}
