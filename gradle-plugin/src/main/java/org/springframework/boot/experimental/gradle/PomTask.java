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

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension;
import io.spring.gradle.dependencymanagement.maven.PomDependencyManagementConfigurer;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.MavenPluginConvention;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskExecutionException;

/**
 * Task to generate a pom file including all runtime dependencies.
 * 
 * @author Andy Wilkinson
 * @author Dave Syer
 *
 */
public class PomTask extends DefaultTask {

	@OutputFile
	private File output;

	@TaskAction
	public void generate() {
		try {
			getLogger().info("Output: " + output);
			DependencyManagementExtension dependencies = getProject().getExtensions()
					.findByType(DependencyManagementExtension.class);
			if (dependencies != null) {
				PomDependencyManagementConfigurer pomConfigurer = dependencies
						.getPomConfigurer();
				MavenPluginConvention maven = getProject().getConvention()
						.findPlugin(MavenPluginConvention.class);
				if (maven != null) {
					output.mkdirs();
					maven.pom().withXml(pomConfigurer)
							.writeTo(new File(output, "pom.xml"));
				}
				else {
					TaskCollection<GenerateMavenPom> tasks = getProject().getTasks()
							.withType(GenerateMavenPom.class);
					if (!tasks.isEmpty()) {
						output.mkdirs();
						GenerateMavenPom plugin = tasks.iterator().next();
						plugin.setDestination(new File(output, "pom.xml"));
						plugin.doGenerate();
					}
					else {
						getLogger().warn(
								"Skipping pom generation (maybe you forgot to apply plugin: 'maven' or 'maven-publish'?)");
					}
				}
			}
			else {
				getLogger().warn(
						"Skipping pom generation (maybe you forgot to apply plugin: 'io.spring.dependency-management'?)");
			}
		}
		catch (Exception e) {
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

}
