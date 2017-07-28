/*
 * Copyright 2012-2017 the original author or authors.
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
import java.util.Arrays;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.tasks.Jar;

/**
 * Gradle {@link Plugin} for Spring Boot's thin launcher.
 *
 * @author Andy Wilkinson
 */
public class ThinLauncherPlugin implements Plugin<Project> {

	@Override
	public void apply(final Project project) {
		project.getTasks().withType(Jar.class, new Action<Jar>() {

			@Override
			public void execute(Jar jar) {
				Copy copy = createCopyTask(project, jar);
				createResolveTask(project, jar, copy);
			}

		});
	}

	private Copy createCopyTask(Project project, Jar jar) {
		Copy copy = project.getTasks().create(jar.getName() + "ThinResolvePrepare", Copy.class);
		copy.dependsOn("bootRepackage");
		copy.from(jar.getOutputs().getFiles());
		copy.into(new File(project.getBuildDir(), "thin/root"));
		return copy;
	}

	private void createResolveTask(final Project project, final Jar jar, final Copy copy) {
		final Exec exec = project.getTasks().create(jar.getName() + "ThinResolve", Exec.class);
		exec.dependsOn(copy);
		exec.doFirst(new Action<Task>() {
			@SuppressWarnings("unchecked")
			@Override
			public void execute(Task task) {
				exec.setWorkingDir(copy.getOutputs().getFiles().getSingleFile());
				exec.setCommandLine(Jvm.current().getJavaExecutable());
				exec.args(Arrays.asList("-Dthin.root=.", "-Dthin.dryrun", "-jar",
						jar.getArchiveName()));
			}
		});
	}

}
