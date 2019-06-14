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
import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(Parameterized.class)
public class DependencyResolverComputeTests {

	private static int TOTAL = 1;

	private static List<Dependency> dependencies;

	@Parameters
	public static Iterable<Object[]> data() {
		List<Object[]> args = new ArrayList<>();
		for (int i = 0; i < TOTAL; i++) {
			args.add(new Object[0]);
		}
		DependencyResolver resolver = DependencyResolver.instance();
		Resource resource = new ClassPathResource("apps/petclinic/pom.xml");
		DependencyResolverComputeTests.dependencies = resolver.dependencies(resource);
		// for (Dependency dependency : dependencies) {
		// System.err.println("dependencies." + dependency.getArtifact().getArtifactId()
		// + "=" + coordinates(dependency.getArtifact()));
		// }
		DependencyResolver.close();
		return args;
	}

	@Test
	public void resolveAll() throws Exception {
		DependencyResolver resolver = DependencyResolver.instance();
		Resource resource = new ClassPathResource("apps/petclinic/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(20);
		DependencyResolver.close();
	}

	@Test
	public void resolvePreComputed() throws Exception {
		DependencyResolver resolver = DependencyResolver.instance();
		List<File> files = new ArrayList<>();
		for (Dependency dependency : dependencies) {
			files.add(resolver.resolve(dependency));
		}
		assertThat(dependencies.size()).isEqualTo(files.size());
		DependencyResolver.close();
	}

	static String coordinates(Artifact artifact) {
		// group:artifact:extension:classifier:version
		return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
	}

}
