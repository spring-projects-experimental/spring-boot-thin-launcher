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
package org.springframework.boot.loader.thin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Properties;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;

import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dave Syer
 *
 */
public class ThinJarLauncherTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Rule
	public OutputCapture output = new OutputCapture();

	@Test
	public void coords() throws Exception {
		String coords = ThinJarLauncher
				.coordinates(new DefaultArtifact("com.example:foo:1.0"));
		assertThat(coords).isEqualTo("com.example:foo:1.0");
	}

	@Test
	public void classifier() throws Exception {
		String coords = ThinJarLauncher
				.coordinates(new DefaultArtifact("com.example:foo:jar:duplicate:1.0"));
		assertThat(coords).isEqualTo("com.example:foo:jar:duplicate:1.0");
	}

	@Test
	public void extension() throws Exception {
		String coords = ThinJarLauncher
				.coordinates(new DefaultArtifact("com.example:foo:zip:1.0"));
		assertThat(coords).isEqualTo("com.example:foo:zip:1.0");
	}

	@Test
	public void extensionAndClassifier() throws Exception {
		String coords = ThinJarLauncher
				.coordinates(new DefaultArtifact("com.example:foo:zip:duplicate:1.0"));
		assertThat(coords).isEqualTo("com.example:foo:zip:duplicate:1.0");
	}

	@Test
	public void emptyProperties() throws Exception {
		String[] args = new String[] { "--thin.classpath",
				"--thin.archive=src/test/resources/apps/basic" };
		ThinJarLauncher launcher = new ThinJarLauncher(args);
		DependencyResolver resolver = mock(DependencyResolver.class);
		ReflectionTestUtils.setField(DependencyResolver.class, "instance", resolver);
		ArgumentCaptor<Properties> props = ArgumentCaptor.forClass(Properties.class);
		when(resolver.dependencies(any(Resource.class), any(Properties.class)))
				.thenReturn(Collections.<Dependency>emptyList());
		launcher.launch(args);
		verify(resolver).dependencies(any(Resource.class), props.capture());
		assertThat(props.getValue()).isEmpty();
		DependencyResolver.close();
	}

	@Test
	public void profileProperties() throws Exception {
		String[] args = new String[] { "--thin.classpath",
				"--thin.archive=src/test/resources/apps/basic", "--thin.profile=foo" };
		ThinJarLauncher launcher = new ThinJarLauncher(args);
		DependencyResolver.close();
		DependencyResolver resolver = mock(DependencyResolver.class);
		ReflectionTestUtils.setField(DependencyResolver.class, "instance", resolver);
		ArgumentCaptor<Properties> props = ArgumentCaptor.forClass(Properties.class);
		when(resolver.dependencies(any(Resource.class), any(Properties.class)))
				.thenReturn(Collections.<Dependency>emptyList());
		launcher.launch(args);
		verify(resolver).dependencies(any(Resource.class), props.capture());
		assertThat(props.getValue()).containsEntry("thin.profile", "foo");
		DependencyResolver.close();
	}

	@Test
	public void dryrun() throws Exception {
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/basic", "--debug" };
		ThinJarLauncher.main(args);
	}

	@Test
	public void classpath() throws Exception {
		String[] args = new String[] { "--thin.classpath",
				"--thin.archive=src/test/resources/apps/basic" };
		ThinJarLauncher.main(args);
		assertThat(output.toString())
				.contains("spring-web-4.3.3.RELEASE.jar" + File.pathSeparator);
	}

	@Test
	public void fatClasspath() throws Exception {
		String[] args = new String[] { "--thin.classpath",
				"--thin.archive=src/test/resources/apps/boot" };
		ThinJarLauncher.main(args);
		assertThat(output.toString())
				.contains("spring-web-5.0.9.RELEASE.jar" + File.pathSeparator);
		assertThat(output.toString()).contains("BOOT-INF" + File.separator + "classes");
	}

	@Test
	public void compute() throws Exception {
		String[] args = new String[] { "--thin.classpath=properties",
				"--thin.archive=src/test/resources/apps/basic" };
		ThinJarLauncher.main(args);
		assertThat(output.toString()).contains(
				"dependencies.spring-web=org.springframework:spring-web:4.3.3.RELEASE\n");
	}

	@Test
	public void twoClassifiers() throws Exception {
		String[] args = new String[] { "--thin.classpath=properties",
				"--thin.archive=src/test/resources/apps/classifier" };
		ThinJarLauncher.main(args);
		assertThat(output.toString()).contains(
				"dependencies.spring-boot-test=org.springframework.boot:spring-boot-test:2.1.0.RELEASE\n");
		assertThat(output.toString()).contains(
				"dependencies.spring-boot-test.tests=org.springframework.boot:spring-boot-test:jar:tests:2.1.0.RELEASE\n");
	}

	@Test
	public void sameArtifactNames() throws Exception {
		String[] args = new String[] { "--thin.classpath=properties",
				"--thin.archive=src/test/resources/apps/same-artifact-names" };
		ThinJarLauncher.main(args);
		assertThat(output.toString()).contains(
				"dependencies.jersey-client=org.glassfish.jersey.core:jersey-client:2.27\n");
		assertThat(output.toString()).contains(
				"dependencies.jersey-client.1=com.sun.jersey:jersey-client:1.19.1\n");
	}

	@Test
	public void thinRoot() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		deleteRecursively(new File("target/thin/test/repository/junit"));
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.root=target/thin/test",
				"--thin.location=file:src/test/resources/apps/db/META-INF",
				"--thin.archive=src/test/resources/apps/basic", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(
				new File("target/thin/test/repository/org/springframework/spring-core"))
						.exists();
		assertThat(new File("target/thin/test/repository/junit/junit")).doesNotExist();
	}

	@Test
	public void thinRootWithPom() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		deleteRecursively(new File("target/thin/test/repository/junit"));
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.root=target/thin/test",
				"--thin.archive=src/test/resources/apps/petclinic", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(
				new File("target/thin/test/repository/org/springframework/spring-core"))
						.exists();
		assertThat(new File("target/thin/test/repository/junit/junit")).doesNotExist();
	}

	@Test
	public void thinRootWithProperties() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		deleteRecursively(new File("target/thin/test/repository/junit"));
		String[] args = new String[] { "--thin.dryrun=true", "--thin.force=false",
				"--thin.root=target/thin/test",
				"--thin.archive=src/test/resources/apps/petclinic-preresolved",
				"--thin.debug" };
		ThinJarLauncher.main(args);
		assertThat(
				new File("target/thin/test/repository/org/springframework/spring-core"))
						.exists();
		assertThat(new File("target/thin/test/repository/junit/junit")).doesNotExist();
		// assertThat(output.toString())
		// .contains("Dependencies are pre-computed in properties");
	}

	@Test
	public void thinRootWithForce() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		deleteRecursively(new File("target/thin/test/repository/junit"));
		String[] args = new String[] { "--thin.dryrun=true", "--thin.force=true",
				"--thin.root=target/thin/test",
				"--thin.archive=src/test/resources/apps/petclinic-preresolved",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(
				new File("target/thin/test/repository/org/springframework/spring-core"))
						.exists();
		assertThat(new File("target/thin/test/repository/junit/junit")).doesNotExist();
	}

	@Test
	public void overrideLocalRepository() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/basic",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void missingThinRootWithPom() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		expected.expect(RuntimeException.class);
		expected.expectMessage("spring-web:jar:X.X.X");
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/missing",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void missingThinRootWithoutPom() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		expected.expect(RuntimeException.class);
		expected.expectMessage("nonexistent:jar:0.0.1");
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/missingthin", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideExistingRepository() throws Exception {
		deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/repositories", "--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void settingsReadFromRoot() throws Exception {
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/local").getAbsolutePath());
		try {
			deleteRecursively(new File(
					"target/thin/test/repository/org/springframework/spring-core"));
			String[] args = new String[] { "--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/snapshots-with-repos",
					"--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void settingsReadFromRootWithThinRoot() throws Exception {
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/local").getAbsolutePath());
		try {
			deleteRecursively(new File(
					"target/thin/other/repository/org/springframework/spring-core"));
			deleteRecursively(new File(
					"target/thin/test/repository/org/springframework/spring-core"));
			String[] args = new String[] { "--thin.dryrun=true",
					"--thin.root=target/thin/other",
					"--thin.archive=src/test/resources/apps/snapshots-with-repos",
					"--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/other/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isFalse();
		assertThat(
				new File("target/thin/other/repository/org/springframework/spring-core")
						.exists()).isTrue();
	}

	@Test
	public void commandLineOffline() throws Exception {
		settingsReadFromRoot();
		DependencyResolver.close();
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.offline=true",
				"--thin.archive=src/test/resources/apps/snapshots-with-repos",
				"--debug" };
		assertThat(deleteRecursively(
				new File("target/thin/test/repository/org/springframework/spring-core")))
						.isTrue();
		expected.expect(RuntimeException.class);
		expected.expectMessage("spring-core");
		ThinJarLauncher.main(args);
	}

	@Test
	public void settingsOffline() throws Exception {
		settingsReadFromRoot();
		DependencyResolver.close();
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("src/test/resources/settings/offline").getAbsolutePath());
		try {
			String[] args = new String[] { "--thin.root=target/thin/test",
					"--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/snapshots-with-repos",
					"--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void repositorySettingsMissing() throws Exception {
		DependencyResolver.close();
		deleteRecursively(new File("target/thin/test/repository/com/example"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/jitpack",
				"--debug" };
		expected.expect(RuntimeException.class);
		expected.expectMessage("maven-simple:jar:1.0");
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository/com/example/maven/maven-simple")
				.exists()).isFalse();
	}

	@Test
	public void repositorySettingsPresent() throws Exception {
		DependencyResolver.close();
		File userhome = new File("target/settings/repo/.m2");
		if (!userhome.exists()) {
			userhome.mkdirs();
		}
		String settings = StreamUtils.copyToString(
				new FileInputStream(
						new File("src/test/resources/settings/repo/.m2/settings.xml")),
				Charset.defaultCharset());
		settings = settings.replace("${repo.url}",
				"file://" + new File("target/test-classes/repo").getAbsolutePath());
		StreamUtils.copy(settings, Charset.defaultCharset(),
				new FileOutputStream(new File(userhome, "settings.xml")));
		String home = System.getProperty("user.home");
		System.setProperty("user.home",
				new File("target/settings/repo").getAbsolutePath());
		try {
			deleteRecursively(new File("target/thin/test/repository/com/example"));
			String[] args = new String[] { "--thin.root=target/thin/test",
					"--thin.dryrun=true",
					"--thin.archive=src/test/resources/apps/jitpack", "--debug" };
			ThinJarLauncher.main(args);
		}
		finally {
			System.setProperty("user.home", home);
		}
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/com/example/maven/maven-simple")
				.exists()).isTrue();
	}

	/**
	 * There is a snapshot dependency in the POM, but no repository configured for it.
	 * @throws Exception ...
	 */
	@Test
	public void repositorySettingsMissingForSnapshotDependency() throws Exception {
		DependencyResolver.close();
		deleteRecursively(
				new File("target/thin/test/repository/javax/validation/validation-api"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/beanvalidation-snapshot",
				"--debug" };
		expected.expect(RuntimeException.class);
		expected.expectMessage("validation-api:jar:2.0.2-SNAPSHOT");
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository/javax/validation/validation-api")
				.exists()).isFalse();
	}

	public static boolean deleteRecursively(File root) {
		return FileSystemUtils.deleteRecursively(root);
	}

}
