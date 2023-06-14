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

package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class AppMavenIT {

	private Process started;

	private static boolean online = false;

	@AfterEach
	public void after() {
		if (started != null && started.isAlive()) {
			started.destroy();
		}
	}

	@Test
	public void thinJar() {
		File jar = new File("../app/target/app-0.0.1-SNAPSHOT.jar");
		assertThat(jar).exists();
		assertThat(jar).is(new Condition<File>("thin") {
			@Override
			public boolean matches(File value) {
				return value.length() < 1024 * 1024;
			}
		});
	}

	@Test
	public void launcherDownloaded() {
		// This one fails unless you run the invoke plugin from the command line (per the
		// pom)
		File downloaded = new File(
				"target/it/app/target/thin/root/repository/org/springframework/boot/experimental");
		assertThat(downloaded).exists();
		downloaded = new File(downloaded,
				"spring-boot-thin-launcher/1.1.0-SNAPSHOT/spring-boot-thin-launcher-1.1.0-SNAPSHOT-exec.jar");
		assertThat(downloaded).exists();
	}

	@Test
	public void runJar() throws Exception {
		if (!online) {
			ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
					"-noverify", "-XX:TieredStopAtLevel=1",
					"-Djava.security.egd=file:/dev/./urandom", "-jar",
					"../app/target/app-0.0.1-SNAPSHOT.jar", "--server.port=0");
			builder.redirectErrorStream(true);
			started = builder.start();
			String output = output(started.getInputStream(), "Started");
			assertThat(output).contains("Started LauncherApplication");
			// There's a thin.properties in the jar that changes Spring Boot version
			// (bizarrely)
			assertThat(output).contains("2.0.6.RELEASE");
			online = true;
		}
	}

	@Test
	@Disabled
	public void runJarOffline() throws Exception {
		if (!online) {
			runJar(); // need this to ensure ordering
		}
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-jar",
				"../app/target/app-0.0.1-SNAPSHOT.jar", "--thin.offline",
				"--server.port=0");
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream(), "Started");
		assertThat(output).contains("Started LauncherApplication").withFailMessage(() -> output);
	}

	@Test
	public void runJarCustomProperties() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
				"-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom", "-jar",
				"../../../../../app/target/app-0.0.1-SNAPSHOT.jar", "--server.port=0");
		builder.redirectErrorStream(true);
		builder.directory(new File("src/test/resources/app"));
		started = builder.start();
		String output = output(started.getInputStream(), "Started");
		assertThat(output).contains("Started LauncherApplication");
		assertThat(output).contains("2.0.4.RELEASE");
	}

	@Test
	public void runJarNamedProperties() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
				"-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom", "-Dthin.debug",
				// Switches to Spring Boot 2.0.1
				"-Dthin.name=app", //
				"-jar", "../../../../../app/target/app-0.0.1-SNAPSHOT.jar",
				"--server.port=0");
		builder.redirectErrorStream(true);
		builder.directory(new File("src/test/resources/app"));
		started = builder.start();
		String output = output(started.getInputStream(), "Started");
		assertThat(output).contains("Started LauncherApplication");
		assertThat(output).contains("2.0.1.RELEASE");
	}

	@Test
	public void runJarNamedPropertiesEnvVar() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
				"-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom", "-jar",
				"../../../../../app/target/app-0.0.1-SNAPSHOT.jar", "--server.port=0");
		// Switches to Spring Boot 2.0.1
		builder.environment().put("THIN_NAME", "app");
		builder.redirectErrorStream(true);
		builder.directory(new File("src/test/resources/app"));
		started = builder.start();
		String output = output(started.getInputStream(), "Started");
		assertThat(output).contains("Started LauncherApplication");
		assertThat(output).contains("2.0.1.RELEASE");
	}

	@Test
	public void runJarExternalArchive() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
				"-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom",
				"-Dthin.archive=maven://com.example:simple:0.0.1-SNAPSHOT", "-jar",
				"../app/target/app-0.0.1-SNAPSHOT.jar", "--server.port=0");
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream(), "Started LauncherApplication");
		assertThat(output).contains("Jetty started");
		assertThat(output).contains("2.6.6");
	}

	private static String output(InputStream inputStream, String marker)
			throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = br.readLine()) != null && !line.contains(marker)) {
				sb.append(line + System.getProperty("line.separator"));
			}
			if (line != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
		}
		finally {
			br.close();
		}
		return sb.toString();
	}

}
