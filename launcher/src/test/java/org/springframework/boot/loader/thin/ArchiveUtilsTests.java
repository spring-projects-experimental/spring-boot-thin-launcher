package org.springframework.boot.loader.thin;
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

import java.io.File;
import java.net.MalformedURLException;
import java.util.List;

import org.assertj.core.api.Condition;
import org.junit.Test;

import org.springframework.boot.loader.archive.Archive;
import org.springframework.boot.loader.archive.ExplodedArchive;
import org.springframework.boot.loader.archive.JarFileArchive;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ArchiveUtilsTests {

	private ArchiveUtils factory = new ArchiveUtils();
	
	@Test
	public void dependenciesWithPlaceholders() throws Exception {
		Archive child = new ExplodedArchive(
				new File("src/test/resources/apps/placeholders"));
		List<Archive> result = factory.extract(child, "thin");
		assertThat(result).isNotEmpty();
	}

	@Test
	public void libsWithProfile() throws Exception {
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/eureka"));
		List<Archive> result = factory.extract(child, "thin", "extra");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result).areAtLeastOne(UrlContains.value("spring-boot-1.4.1.RELEASE"));
	}

	@Test
	public void overlappingJarsWithDifferentVersions() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new JarFileArchive(
				new File("src/test/resources/app-with-web-in-lib-properties.jar"));
		List<Archive> result = factory.subtract(parent, child, "thin");
		assertThat(result).isEmpty();
	}

	@Test
	public void childWithDatabase() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/db"));
		List<Archive> result = factory.subtract(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result).areAtLeastOne(UrlContains.value("spring-jdbc"));
		assertThat(result).doNotHave(UrlContains.value("spring-boot/"));
	}

	@Test
	public void childWithEureka() throws Exception {
		Archive parent = new ExplodedArchive(
				new File("src/test/resources/apps/web-and-cloud"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/eureka"));
		List<Archive> result = factory.subtract(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result)
				.areAtLeastOne(UrlContains.value("spring-cloud-netflix-eureka-client"));
		assertThat(result).doNotHave(UrlContains.value("spring-boot"));
	}

	@Test
	public void childWithEurekaAndJarParent() throws Exception {
		Archive parent = new JarFileArchive(
				new File("src/test/resources/app-with-web-and-cloud-config.jar"));
		Archive child = new ExplodedArchive(new File("src/test/resources/apps/eureka"));
		List<Archive> result = factory.subtract(parent, child, "thin");
		assertThat(result).size().isGreaterThan(3);
		assertThat(result)
				.areAtLeastOne(UrlContains.value("spring-cloud-netflix-eureka-client"));
		assertThat(result).doNotHave(UrlContains.value("spring-boot"));
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
