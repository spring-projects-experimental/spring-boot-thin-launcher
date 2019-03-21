/*
 * Copyright 2012-2016 the original author or authors.
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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.tasks.Jar;

import org.springframework.util.StringUtils;

/**
 * Gradle {@link Plugin} for Spring Boot's thin launcher.
 * 
 * If the Java plugin is applied to the project, some tasks are added to the project.
 * <ul>
 * <li>"thinResolve": runs the project jar and download its dependencies. If you have more
 * than one jar task then an additional task is created for each one named
 * "thinResolve[JarTaskName]" (where "JarTaskName" is the capitalized name of the jar
 * task).</li>
 * <li>"thinResolvePrepare": copies the project jar to the "root" directory preparing for
 * the resolution. The same naming convention applies to multiple jar tasks.</li>
 * <li>"thinProperties": calculates thin.properties and puts them in the main build
 * output.</li>
 * <li>"thinPom": runs automatically if you apply the Maven plugin. Generates a pom.xml
 * and puts it in the main build output.</li>
 * </ul>
 * 
 * @author Andy Wilkinson
 */
public class ThinLauncherPlugin implements Plugin<Project> {

	@Override
	public void apply(final Project project) {
		project.getTasks().withType(Jar.class, new Action<Jar>() {

			@Override
			public void execute(Jar jar) {
				createCopyTask(project, jar);
				createResolveTask(project, jar);
				createPropertiesTask(project);
				createPomTask(project);
			}

		});
	}

	private void createPomTask(final Project project) {
		TaskContainer taskContainer = project.getTasks();
		create(taskContainer, "thinPom", PomTask.class, new Action<PomTask>() {
			@Override
			public void execute(final PomTask thin) {
				thin.doFirst(new Action<Task>() {
					@Override
					public void execute(Task task) {
						SourceSetContainer sourceSets = project.getConvention()
								.getPlugin(JavaPluginConvention.class).getSourceSets();
						File resourcesDir = sourceSets.getByName("main").getOutput()
								.getResourcesDir();
						thin.setOutput(new File(resourcesDir, "META-INF/maven/"
								+ project.getGroup() + "/" + project.getName()));
					}
				});
				project.getTasks().withType(Jar.class, new Action<Jar>() {
					@Override
					public void execute(Jar jar) {
						jar.dependsOn(thin);
					}
				});
			}
		});
	}

	private void createPropertiesTask(final Project project) {
		TaskContainer taskContainer = project.getTasks();
		create(taskContainer, "thinProperties", PropertiesTask.class,
				new Action<PropertiesTask>() {
					@Override
					public void execute(PropertiesTask libPropertiesTask) {
						configureLibPropertiesTask(libPropertiesTask, project);
					}
				});
	}

	private void configureLibPropertiesTask(PropertiesTask thin, Project project) {
		thin.setConfiguration(findRuntimeClasspath(project));
		SourceSetContainer sourceSets = project.getConvention()
				.getPlugin(JavaPluginConvention.class).getSourceSets();
		File resourcesDir = sourceSets.getByName("main").getOutput().getResourcesDir();
		thin.setOutput(new File(resourcesDir, "META-INF"));
	}

	private Configuration findRuntimeClasspath(Project project) {
		Configuration configuration = project.getConfigurations()
				.getByName("runtimeClasspath");
		if (configuration == null) {
			configuration = project.getConfigurations()
					.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME);
		}
		return configuration;
	}

	private void createCopyTask(final Project project, final Jar jar) {
		String task = "bootRepackage";
		Jar thinJar = jar;
		if (project.getTasksByName(task, true).isEmpty()) {
			task = "thinJar" + suffix(jar);
			if (suffix(jar).startsWith("ThinJar")) {
				return;
			}
			if (!project.getTasksByName(task, true).isEmpty()) {
				return;
			}
			if (suffix(jar).isEmpty()) {
				thinJar = create(project.getTasks(), task, Jar.class, new Action<Jar>() {

					@Override
					public void execute(final Jar thin) {
						final Jar bootJar;
						if (project.getTasks().findByName("bootJar") != null) {
							bootJar = (Jar) project.getTasks().getByName("bootJar");
						}
						else {
							bootJar = (Jar) project.getTasks().getByName("jar");
						}
						thin.dependsOn(bootJar);
						project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME)
								.dependsOn(thin);
						thin.doFirst(new Action<Task>() {
							@Override
							public void execute(Task t) {
								Map<String, Object> attrs = new HashMap<>();
								attrs.put("Main-Class",
										"org.springframework.boot.loader.wrapper.ThinJarWrapper");
								attrs.put("Start-Class", getMainClass(bootJar));
								thin.setManifest(bootJar.getManifest());
								thin.getManifest().attributes(attrs);
								SourceSetContainer sources = (SourceSetContainer) project
										.getProperties().get("sourceSets");
								thin.from(project.zipTree(new Callable<File>() {
									@Override
									public File call() throws Exception {
										File file = File.createTempFile("tmp", ".jar",
												project.getBuildDir());
										file.delete();
										Files.copy(getClass().getClassLoader()
												.getResourceAsStream(
														"META-INF/loader/spring-boot-thin-wrapper.jar"),
												file.toPath());
										return file;
									}
								}));
								thin.from((Object) sources.findByName("main")
										.getRuntimeClasspath().filter(new Spec<File>() {
											@Override
											public boolean isSatisfiedBy(File element) {
												return element.isDirectory();
											}
										}).getFiles().toArray(new File[0]));
							}

							private Object getMainClass(Jar bootJar) {
								Object result = bootJar.getManifest().getAttributes()
										.get("Start-Class");
								if (result != null) {
									return result;
								}
								return bootJar.getManifest().getAttributes()
										.get("Main-Class");
							}

						});
						thin.setDescription(
								"Assembles a thin executable jar archive containing the main"
										+ " classes and the thin wrapper.");
						thin.setGroup(BasePlugin.BUILD_GROUP);
					}
				});
			}
		}
		final String bootJarTask = task;
		final Jar targetJar = thinJar;
		create(project.getTasks(), "thinResolvePrepare" + suffix(jar), Copy.class,
				new Action<Copy>() {
					@Override
					public void execute(Copy copy) {
						copy.dependsOn(bootJarTask);
						copy.from(targetJar.getOutputs().getFiles());
						copy.into(new File(project.getBuildDir(), "thin/root"));
					}
				});
	}

	private void createResolveTask(final Project project, final Jar jar) {
		create(project.getTasks(), "thinResolve" + suffix(jar), Exec.class,
				new Action<Exec>() {
					@Override
					public void execute(final Exec exec) {
						final Jar thinJar;
						if (project.getTasks()
								.findByName("thinJar" + suffix(jar)) != null) {
							thinJar = (Jar) project.getTasks()
									.getByName("thinJar" + suffix(jar));
						}
						else {
							thinJar = (Jar) project.getTasks().getByName("jar");
						}
						final String prepareTask = "thinResolvePrepare" + suffix(jar);
						exec.dependsOn(prepareTask);
						exec.doFirst(new Action<Task>() {
							@SuppressWarnings("unchecked")
							@Override
							public void execute(Task task) {
								Copy copy = (Copy) project.getTasks()
										.getByName(prepareTask);
								exec.setWorkingDir(
										copy.getOutputs().getFiles().getSingleFile());
								exec.setCommandLine(Jvm.current().getJavaExecutable());
								List<String> args = Arrays.asList("-Dthin.root=.",
										"-Dthin.dryrun", "-jar",
										thinJar.getArchiveName());
								String thinRepo = getThinRepo(project);
								if (thinRepo != null) {
									args.add(1, "-Dthin.repo=" + thinRepo);
								}
								exec.args(args);
							}
						});
					}
				});
	}

	private String getThinRepo(Project project) {
		if (System.getProperty("thin.repo") != null) {
			return System.getProperty("thin.repo");
		}
		if (System.getenv("THIN_REPO") != null) {
			return System.getProperty("THIN_REPO");
		}
		Map<String, ?> properties = project.getProperties();
		if (properties != null && properties.get("thin.repo") != null) {
			return (String) properties.get("thin.repo");
		}
		return null;
	}

	private String suffix(Jar jar) {
		String name = jar.getName();
		return "jar".equals(name) || "bootJar".equals(name) ? ""
				: StringUtils.capitalize(name);
	}

	@SuppressWarnings("unchecked")
	private <T extends Task> T create(TaskContainer taskContainer, String name,
			Class<T> type, Action<? super T> configuration)
			throws InvalidUserDataException {
		Task existing = taskContainer.findByName(name);
		if (existing != null) {
			return (T) existing;
		}
		return taskContainer.create(name, type, configuration);
	}
}
