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

package org.springframework.boot.loader.wrapper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map.Entry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.util.FileSystemUtils;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinJarWrapperTests {

	private PrintStream out;

	@Before
	public void open() {
		out = System.out;
		FileSystemUtils.deleteRecursively(new File("target/repository"));
	}

	@After
	public void init() {
		System.setOut(out);
		System.clearProperty("thin.root");
		System.clearProperty("thin.repo");
		System.clearProperty("thin.library");
		System.clearProperty("thin.launcher");
	}

	@Test
	public void testDefaultLibrary() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertThat(wrapper.library(), containsString("spring-boot-thin-launcher"));
		if (System.getProperty("project.version") != null) {
			assertThat(wrapper.library(),
					containsString(System.getProperty("project.version")));
		}
	}

	@Test
	public void testCustomLibrary() throws Exception {
		System.setProperty("thin.library", "com.example:main:0.0.1-SNAPSHOT");
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertThat(wrapper.library(), containsString("com/example/main"));
	}

	@Test
	public void testMavenLocalOverride() throws Exception {
		System.setProperty("thin.root", "target");
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertEquals("target/repository", wrapper.mavenLocal());
	}

	@Test
	public void testMavenLocalOverrideOnCommandLine() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper("--thin.root=target");
		assertEquals("target/repository", wrapper.mavenLocal());
	}

	@Test
	public void testLaunch() throws Exception {
		System.setProperty("thin.root", "target");
		System.setProperty("thin.repo", new File("./src/test/resources/repository")
				.getAbsoluteFile().toURI().toURL().toString());
		System.setProperty("thin.library", "com.example:main:0.0.1-SNAPSHOT");
		System.setProperty("thin.launcher", "main.Main");
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(stream));
		ThinJarWrapper.main(new String[0]);
		String output = stream.toString();
		assertThat(output, containsString("Main Running"));
	}

	@Test
	public void testSystemEnvironmentOverride() throws Exception {
		String key = null;
		String value = null;
		for (Entry<String, String> entry : System.getenv().entrySet()) {
			String var = entry.getKey();
			if (var.contains("_") && var.equals(var.toUpperCase())) {
				String relaxedKey = var.toLowerCase().replace("_", ".");
				// since ThinJarWrapper gives system properties precedence, we only want
				// an env var whose key is not also in relaxed form as a system property
				if (System.getProperty(relaxedKey) == null
						&& System.getenv(var) != null) {
					key = relaxedKey;
					value = entry.getValue();
				}
				break;
			}
		}
		if (key != null) {
			assertEquals("Wrong value for key=" + key, value,
					new ThinJarWrapper().getProperty(key));
		}
		else {
			System.err.println("WARN: no testable env var");
		}
	}

}
