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
import org.junit.After;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class FatMavenIT {

	private Process started;

	@After
	public void after() {
		if (started != null && started.isAlive()) {
			started.destroy();
		}
	}

	@Test
	public void fatJar() {
		File jar = new File("../fat/target/fat-0.0.1-SNAPSHOT.jar");
		assertThat(jar).exists();
		assertThat(jar).is(new Condition<File>("fat") {
			@Override
			public boolean matches(File value) {
				return value.length() > 1024 * 1024;
			}
		});
	}

	@Test
	public void exploded() throws Exception {
		File exploded = new File("../fat/target/dependency");
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(),
				// "-agentlib:jdwp=transport=dt_socket,server=y,address=8000",
				"-Dthin.archive=" + exploded, "-jar",
				"../simple/target/simple-0.0.1-SNAPSHOT.jar", "--thin.classpath");
		builder.redirectErrorStream(true);
		assertThat(exploded).exists();
		started = builder.start();
		String output = output(started.getInputStream());
		assertThat(output).doesNotContain("file:");
		assertThat(output).contains(
				"target/dependency/BOOT-INF/classes".replace("/", File.separator));
		assertThat(output).contains("2.0.5.RELEASE");
		assertThat(output).doesNotContain("actuator");
		assertThat(output).doesNotContain("spring-cloud");
	}

	@Test
	public void classpath() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(),
				"-Dthin.archive=../fat/target/fat-0.0.1-SNAPSHOT.jar", "-jar",
				"../simple/target/simple-0.0.1-SNAPSHOT.jar", "--thin.classpath");
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream());
		assertThat(output).doesNotContain("target/fat/BOOT-INF/classes");
		assertThat(output).contains("2.0.5.RELEASE");
		assertThat(output).doesNotContain("actuator");
		assertThat(output).doesNotContain("spring-cloud");
	}

	@Test
	public void profile() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(),
				"-Dthin.archive=../fat/target/fat-0.0.1-SNAPSHOT.jar", "-jar",
				"../simple/target/simple-0.0.1-SNAPSHOT.jar", "--thin.classpath",
				"--thin.profile=actr");
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream());
		assertThat(output).contains("fat-0.0.1");
		assertThat(output).contains("actuator");
	}

	private static String output(InputStream inputStream) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
		}
		finally {
			br.close();
		}
		return sb.toString();
	}
}
