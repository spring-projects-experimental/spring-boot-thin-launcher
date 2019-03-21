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

package org.springframework.boot.loader.thin.converter;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PatternTests {

	@Test
	public void test() {
		Path path = Paths.get(
				"spring-cloud-function-context/1.0.0.BUILD-SNAPSHOT/",
				"spring-cloud-function-context-1.0.0.BUILD-20180521.090752-196.jar");
		assertThat(isDuplicate(path)).isTrue();
	}

	private boolean isDuplicate(Path file) {
		String name = file.getFileName().toString();
		String alt = name.replaceAll("[0-9]*\\.[0-9]*-[0-9]*", "SNAPSHOT");
		if (!name.equals(alt)) {
			return true;
		}
		return false;
	}
}
