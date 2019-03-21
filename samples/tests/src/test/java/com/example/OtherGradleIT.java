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
public class OtherGradleIT {

	private Process started;

	@After
	public void after() {
		if (started != null && started.isAlive()) {
			started.destroy();
		}
	}

	@Test
	public void thinJar() {
		File jar = new File("../other/build/libs/other-0.0.1-SNAPSHOT.jar");
		assertThat(jar).exists();
		assertThat(jar).is(new Condition<File>("thin") {
			@Override
			public boolean matches(File value) {
				return value.length() < 1024 * 1024;
			}
		});
	}

	@Test
	public void runJar() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Xmx128m",
				"-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom", "-jar",
				"../other/build/libs/other-0.0.1-SNAPSHOT.jar", "--server.port=0");
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream(), "Started LauncherApplication");
		assertThat(output).contains("Started LauncherApplication");
	}

	@Test
	public void resolveDependencies() throws Exception {
		ProcessBuilder builder = new ProcessBuilder(Utils.javaCommand(), "-Dthin.root=.",
				"-Xmx128m", "-noverify", "-XX:TieredStopAtLevel=1",
				"-Djava.security.egd=file:/dev/./urandom", "-jar",
				"other-0.0.1-SNAPSHOT.jar", "--server.port=0");
		builder.directory(new File("../other/build/thin/deploy/"));
		builder.redirectErrorStream(true);
		started = builder.start();
		String output = output(started.getInputStream(), "Started LauncherApplication");
		assertThat(output).contains("Started LauncherApplication");
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
