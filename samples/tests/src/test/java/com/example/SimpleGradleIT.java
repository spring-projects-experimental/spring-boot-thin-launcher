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

package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.junit.After;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class SimpleGradleIT {

	private Process started;

	@BeforeClass
	public static void init() {
		Assume.assumeTrue("Not an integration test",
				System.getProperty("integration.test") != null
						&& !"false".equals(System.getProperty("integration.test")));
	}

	@After
	public void after() {
		if (started != null && started.isAlive()) {
			started.destroy();
		}
	}

	@Test
	public void runJar() throws Exception {
		ProcessBuilder builder = new ProcessBuilder("java", "-jar",
				"../simple/build/libs/simple-0.0.1-SNAPSHOT.jar");
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
