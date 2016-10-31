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

package org.springframework.boot.experimental.gradle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

/**
 * {@link Task} that generates a properties file to be consumed by the thin launcher.
 *
 * @author Andy Wilkinson
 */
public class GenerateLauncherPropertiesTask extends DefaultTask {

	@Input
	private Configuration configuration;

	@OutputFile
	private File output;

	@TaskAction
	public void generateLibProperties() {
		Properties properties = new Properties();
		properties.setProperty("transitive.enabled", "false");
		for (ResolvedArtifact artifact: this.configuration.getResolvedConfiguration().getResolvedArtifacts()) {
			ModuleVersionIdentifier artifactId = artifact.getModuleVersion().getId();
			properties.setProperty("dependencies." + artifactId.getName(), artifactId.getGroup()
					+ ":" + artifactId.getName() + ":" + artifactId.getVersion());
		}
		this.output.getParentFile().mkdirs();
		try (FileOutputStream stream = new FileOutputStream(this.output)) {
			properties.store(new FileOutputStream(this.output), null);
		}
		catch (IOException ex) {
			throw new TaskExecutionException(this, ex);
		}
	}

	/**
	 * Sets the {@link Configuration} that will be used to resolve the dependencies
	 * that are listed in {@code lib.proeprties}.
	 *
	 * @param configuration the configuration
	 */
	public void setConfiguration(Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Sets the location to which the properties file will be written.
	 *
	 * @param output the output location
	 */
	public void setOutput(File output) {
		this.output = output;
	}

}
