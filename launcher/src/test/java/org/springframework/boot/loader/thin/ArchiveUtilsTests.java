/*
 * Copyright 2023 the original author or authors.
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

public class ArchiveUtilsTests {

	@Test
	public void resolveAll() throws Exception {
		assertThat(ArchiveUtils.getArchives("src/test/resources/*").size()).isEqualTo(2);
	}

	@Test
	public void resolveSome() throws Exception {
		assertThat(ArchiveUtils.getArchives("src/test/resources/app-with-web-and-cloud-config.jar" + File.pathSeparator
				+ "src/test/resources/app-with-web-in-lib-properties.jar").size()).isEqualTo(2);
	}

}
