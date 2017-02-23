/*
 * Copyright 2016-2017 the original author or authors.
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
package org.springframework.boot.loader.thin;

import java.io.File;

import org.junit.Test;

import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinJarLauncherTests {

	@Test
	public void dryrun() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.dryrun=true",
				"--thin.archive=src/test/resources/apps/basic", "--debug" };
		ThinJarLauncher.main(args);
	}

	@Test
	public void overrideLocalRepository() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/basic",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideExistingRepository() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/repositories",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

	@Test
	public void overrideSnapshotRepository() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository/org/springframework/spring-core"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/apps/snapshots",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File("target/thin/test/repository").exists()).isTrue();
		assertThat(new File("target/thin/test/repository/org/springframework/spring-core")
				.exists()).isTrue();
	}

}
