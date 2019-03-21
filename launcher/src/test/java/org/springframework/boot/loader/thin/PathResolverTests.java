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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;
import org.mockito.Mockito;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;

/**
 * @author Dave Syer
 *
 */
public class PathResolverTests {

	private DependencyResolver dependencies = Mockito.mock(DependencyResolver.class);
	private PathResolver resolver = new PathResolver(dependencies);

	@Test
	public void petclinic() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/petclinic"));
		Artifact artifact = new DefaultArtifact("org.foo:whatever:1.2.3");
		artifact = artifact.setFile(
				new File("src/test/resources/app-with-web-in-lib-properties.jar"));
		List<Dependency> list = Arrays.asList(new Dependency(artifact, "compile"));
		Mockito.when(
				dependencies.dependencies(any(Resource.class), any(Properties.class)))
				.thenReturn(list);
		List<Archive> result = resolver.resolve(parent, "thin");
		assertThat(result.size()).isEqualTo(2);
		assertThat(result.get(1).getUrl()).isEqualTo(artifact.getFile().toURI().toURL());
		// Mockito.verify(dependencies);
	}

	@Test
	public void properties() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/exclusions"));
		Properties result = ReflectionTestUtils.invokeMethod(resolver, "getProperties",
				parent, "thin", new String[] {});
		assertThat(result.size()).isEqualTo(2);
	}

	@Test
	public void source() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/source/target/classes"));
		Resource pom = resolver.getPom(parent);
		assertThat(pom.exists()).isTrue();
		assertThat(pom.getFilename()).isEqualTo("pom.xml");
	}

	@Test
	public void testSource() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/source/target/test-classes"));
		Resource pom = resolver.getPom(parent);
		assertThat(pom.exists()).isTrue();
		assertThat(pom.getFilename()).isEqualTo("pom.xml");
	}

	@Test
	public void propertiesPreresolved() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/preresolved"));
		Properties result = ReflectionTestUtils.invokeMethod(resolver, "getProperties",
				parent, "thin", new String[] {});
		assertThat(result.size()).isEqualTo(71);
	}

	@Test
	public void propertiesExcludeThenInclude() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/exclude-include"));
		Properties result = ReflectionTestUtils.invokeMethod(resolver, "getProperties",
				parent, "thin", new String[] {});
		assertThat(result).doesNotContainKey("dependencies.spring-boot-starter-actuator");
		assertThat(result).containsKey("exclusions.spring-boot-starter-actuator");
		result = ReflectionTestUtils.invokeMethod(resolver, "getProperties", parent,
				"thin", new String[] { "actr" });
		assertThat(result).doesNotContainKey("exclusions.spring-boot-starter-actuator");
		assertThat(result).containsKey("dependencies.spring-boot-starter-actuator");
	}

	@Test
	public void propertiesPreresolvedMixed() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/preresolved"));
		Properties result = ReflectionTestUtils.invokeMethod(resolver, "getProperties",
				parent, "thin", new String[] { "added" });
		assertThat(result.size()).isEqualTo(72);
	}

	@Test
	public void pomWithMavenArchiveOldStyle() throws Exception {
		Resource resource = resolver.getPom(ArchiveUtils.getArchive(
				"maven://org.springframework.boot:spring-boot-cli:jar:full:1.3.8.RELEASE"));
		assertThat(resource.getURL().toString()).endsWith(
				"META-INF/maven/org.springframework.boot/spring-boot-cli/pom.xml");
	}

	@Test
	public void pomWithMavenArchiveBootInf() throws Exception {
		Resource resource = resolver.getPom(ArchiveUtils.getArchive(
				"maven://org.springframework.boot:spring-boot-cli:jar:full:1.4.2.RELEASE"));
		assertThat(resource.getURL().toString()).endsWith(
				"BOOT-INF/classes/META-INF/maven/org.springframework.boot/spring-boot-cli/pom.xml");
	}

}
