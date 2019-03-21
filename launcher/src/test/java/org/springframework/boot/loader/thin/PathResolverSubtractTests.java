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
import java.net.MalformedURLException;
import java.util.List;

import org.assertj.core.api.Condition;
import org.junit.Test;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;
import org.springframework.boot.loader.thin.DependencyResolver;
import org.springframework.boot.loader.thin.PathResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PathResolverSubtractTests {

	private DependencyResolver dependencies = DependencyResolver.instance();
	private PathResolver resolver = new PathResolver(dependencies);

	@Test
	public void childWithDatabase() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/db"));
		List<Archive> result = resolver.resolve(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result).areAtLeastOne(UrlContains.value("spring-jdbc"));
		assertThat(result).areExactly(1, UrlContains.value("spring-boot-1.3.8"));
	}

	@Test
	public void childWithEureka() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/web-and-cloud"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/eureka"));
		List<Archive> result = resolver.resolve(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result).areExactly(1,
				UrlContains.value("spring-cloud-netflix-eureka-client"));
		assertThat(result).areExactly(1, UrlContains.value("spring-boot-1.3.8"));
	}

	@Test
	public void childWithEurekaAndJarParent() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/eureka"));
		List<Archive> result = resolver.resolve(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result)
				.areAtLeastOne(UrlContains.value("spring-cloud-netflix-eureka-client"));
		assertThat(result).areExactly(1, UrlContains.value("spring-boot-1.3.8"));
	}

	@Test
	public void overlappingJarsWithDifferentVersions() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new JarFileArchive(
				new File("src/test/resources/app-with-web-in-lib-properties.jar"));
		List<Archive> result = resolver.resolve(parent, child, "thin");
		assertThat(result.toString())
				.isEqualTo(resolver.resolve(null, child, "thin").toString());
	}

	private static final class UrlContains extends Condition<Archive> {
		private String string;

		public static UrlContains value(String string) {
			return new UrlContains(string);
		}

		private UrlContains(String string) {
			this.string = string;
		}

		@Override
		public boolean matches(Archive value) {
			try {
				return value.getUrl().toString().contains(string);
			}
			catch (MalformedURLException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
