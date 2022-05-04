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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map.Entry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

/**
 * @author Dave Syer
 *
 */
public class ThinJarWrapperTests {

	private PrintStream out;

	@BeforeEach
	public void open() {
		out = System.out;
		FileSystemUtils.deleteRecursively(new File("target/repository"));
	}

	@AfterEach
	public void init() {
		System.setOut(out);
		System.clearProperty("maven.repo.local");
		System.clearProperty("thin.root");
		System.clearProperty("thin.repo");
		System.clearProperty("thin.library");
		System.clearProperty("thin.launcher");
	}

	@Test
	public void testProjectVersion() {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		Assumptions.assumeTrue(System.getProperty("project.version")!=null);
		assertThat(wrapper.download()).contains(System.getProperty("project.version"));
	}

	@Test
	public void testDefaultLibrary() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertThat(wrapper.download()).contains("spring-boot-thin-launcher");
	}

	@Test
	public void testCustomLibrary() throws Exception {
		System.setProperty("thin.library", "com.example:main:0.0.1-SNAPSHOT");
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertThat(wrapper.download()).contains("com/example/main");
	}

	@Test
	public void testCustomLibraryFilePrefix() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		System.setProperty("thin.library",
				"file:" + wrapper.thinRootRepository() + "/com/example/main/0.0.1-SNAPSHOT/main-0.0.1-SNAPSHOT.jar");
		assertThat(wrapper.download()).contains("com/example/main");
	}

	@Test
	public void testCustomLibraryFile() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		System.setProperty("thin.library", "target/rubbish/com/example/main/0.0.1-SNAPSHOT/main-0.0.1-SNAPSHOT.jar");
		assertThat(wrapper.download()).contains("com/example/main");
	}

	@Test
	public void testMavenLocalRepo() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		System.setProperty("maven.repo.local", "target/local");
		assertThat(wrapper.mavenLocal()).contains("target/local");
		assertThat(wrapper.thinRootRepository()).contains("target/local");
	}

	@Test
	public void testMavenLocalRepoDifferentFromThinRoot() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		System.setProperty("thin.root", "target");
		System.setProperty("maven.repo.local", "target/local");
		assertThat(wrapper.mavenLocal()).contains("target/local");
		assertThat(wrapper.thinRootRepository()).contains("target/repository");
	}

	@Test
	public void testUrlFromFileAbsolutePath() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper();
		// This works on Windows as well
		String path = new File("target/thin").getAbsolutePath();
		String uri = ReflectionTestUtils.invokeMethod(wrapper, "getUrl", path);
		assertThat(uri).startsWith("file:/");
		assertThat(uri).contains("target/thin");
	}

	@Test
	public void testMavenLocalRepoDownload() throws Exception {
		System.setProperty("thin.root", "target");
		// Use the default local repo, to force a copy instead of a download
		System.setProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository");
		ThinJarWrapper wrapper = new ThinJarWrapper();
		// puts the launcher in target/repository (faster than downloading from
		// internet)
		wrapper.download();
		FileSystemUtils.copyRecursively(new File("target/repository"), new File("target/local"));
		FileSystemUtils.deleteRecursively(new File("target/repository"));
		System.setProperty("maven.repo.local", "target/local");
		// Now it will download *from* the local repo to the thin.root
		assertThat(wrapper.download()).contains("target/repository");
	}

	@Test
	public void testThinRootOverride() throws Exception {
		System.setProperty("thin.root", "target");
		ThinJarWrapper wrapper = new ThinJarWrapper();
		assertThat(wrapper.thinRootRepository()).isEqualTo("target/repository");
	}

	@Test
	public void testThinRootOverrideOnCommandLine() throws Exception {
		ThinJarWrapper wrapper = new ThinJarWrapper("--thin.root=target");
		assertThat(wrapper.thinRootRepository()).isEqualTo("target/repository");
	}

	@Test
	public void testLaunch() throws Exception {
		System.setProperty("thin.root", "target");
		System.setProperty("thin.repo",
				new File("./src/test/resources/repository").getAbsoluteFile().toURI().toURL().toString());
		System.setProperty("thin.library", "com.example:main:0.0.1-SNAPSHOT");
		System.setProperty("thin.launcher", "main.Main");
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		System.setOut(new PrintStream(stream));
		ThinJarWrapper.main(new String[0]);
		String output = stream.toString();
		assertThat(output).contains("Main Running");
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
				if (System.getProperty(relaxedKey) == null && System.getenv(var) != null) {
					key = relaxedKey;
					value = entry.getValue();
				}
				break;
			}
		}
		Assumptions.assumeTrue(key != null, "WARN: no testable env var");
		assertThat(new ThinJarWrapper().getProperty(key)).isEqualTo(value);
	}

}
