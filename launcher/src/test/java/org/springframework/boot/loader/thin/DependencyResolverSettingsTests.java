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
import java.util.List;
import java.util.Properties;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.ProjectBuildingRequest;
import org.assertj.core.api.filter.NotFilter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.After;
import org.junit.Test;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class DependencyResolverSettingsTests {

	private String home = System.getProperty("user.home");

	@After
	public void close() {
		System.setProperty("user.home", home);
		DependencyResolver.close();
	}

	@Test
	public void testVanilla() throws Exception {
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		ProjectBuildingRequest request = getProjectBuildingRequest(resolver);
		assertThat(request.getRemoteRepositories()).filteredOnNull("proxy")
				.hasSameSizeAs(request.getRemoteRepositories());
		List<ArtifactRepository> repositories = request.getRemoteRepositories();
		assertThat(repositories).filteredOnNull("snapshots").isEmpty();
		assertThat(repositories.get(0).getSnapshots().isEnabled()).isTrue();
	}

	@Test
	public void testProxy() throws Exception {
		System.setProperty("user.home", "src/test/resources/settings/proxy");
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		ProjectBuildingRequest request = getProjectBuildingRequest(resolver);
		assertThat(request.getRemoteRepositories()).filteredOnNull("proxy").isEmpty();
	}

	@Test
	public void testThinRoot() throws Exception {
		Properties properties = new Properties();
		properties.setProperty("thin.root", "src/test/resources/settings/proxy");
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		ProjectBuildingRequest request = getProjectBuildingRequest(resolver, properties);
		assertThat(request.getRemoteRepositories()).filteredOn("proxy", NotFilter.not(null)).isNotEmpty();
	}

	@Test
	public void testLocalRepository() throws Exception {
		System.setProperty("user.home", "src/test/resources/settings/local");
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		ProjectBuildingRequest request = getProjectBuildingRequest(resolver);
		assertThat(request.getLocalRepository().getUrl()).contains("target/thin/test/repository");
	}

	@Test
	public void testRemoteRepository() throws Exception {
		File userhome = new File("target/settings/repo/.m2");
		if (!userhome.exists()) {
			userhome.mkdirs();
		}
		String settings = StreamUtils.copyToString(
				new FileInputStream(new File("src/test/resources/settings/repo/.m2/settings.xml")),
				Charset.defaultCharset());
		settings = settings.replace("${repo.url}", "file://" + new File("target/test-classes/repo").getAbsolutePath());
		StreamUtils.copy(settings, Charset.defaultCharset(), new FileOutputStream(new File(userhome, "settings.xml")));
		System.setProperty("user.home", new File("target/settings/repo").getAbsolutePath());
		ThinJarLauncherTests.deleteRecursively(new File("target/settings/repo/.m2/repository/com/example"));
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		Artifact artifact = new DefaultArtifact("com.example.maven:maven-simple:1.0");
		File file = resolver.resolve(new Dependency(artifact, "compile"));
		assertThat(file.getAbsolutePath()).contains("settings/repo/.m2/repository/com/example");
		assertThat(file.exists());
	}

	@Test
	public void testSnaphotsEnabledByDefault() throws Exception {
		System.setProperty("user.home", "src/test/resources/settings/snapshots/defaultWithNoSnapshotsElement");
		DependencyResolver.close();
		DependencyResolver resolver = DependencyResolver.instance();
		ProjectBuildingRequest request = getProjectBuildingRequest(resolver);
		List<ArtifactRepository> repositories = request.getRemoteRepositories();
		assertThat(repositories).filteredOnNull("snapshots").isEmpty();
		assertThat(repositories).hasSize(3);
		assertThat(repositories.get(0).getSnapshots().isEnabled()).isTrue();
	}

	private ProjectBuildingRequest getProjectBuildingRequest(DependencyResolver resolver) {
		Properties properties = new Properties();
		return getProjectBuildingRequest(resolver, properties);
	}

	private ProjectBuildingRequest getProjectBuildingRequest(DependencyResolver resolver, Properties properties) {
		ReflectionTestUtils.invokeMethod(resolver, "initialize", properties);
		ProjectBuildingRequest request = ReflectionTestUtils.invokeMethod(resolver, "getProjectBuildingRequest",
				properties);
		return request;
	}

}
