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
package com.example;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import org.springframework.boot.loader.thin.ThinJarLauncher;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(Parameterized.class)
public class LocalRepositoryTests {

	// Counter for performance testing
	private static int COUNT = 1;

	@Parameters
	public static List<Object[]> params() {
		List<Object[]> list = new ArrayList<>();
		for (int i = 0; i < COUNT; i++) {
			list.add(new Object[] {});
		}
		return list;
	}

	@Test
	public void dryrunSpringCore() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/basic",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File(
				"target/thin/test/repository/org/springframework/spring-core/4.3.8.RELEASE/spring-core-4.3.8.RELEASE.jar"))
						.exists();
	}

	@Test
	public void dryrunLocalApp() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/thin/test/repository"));
		String[] args = new String[] { "--thin.root=target/thin/test",
				"--thin.dryrun=true", "--thin.archive=src/test/resources/local",
				"--debug" };
		ThinJarLauncher.main(args);
		assertThat(new File(
				"target/thin/test/repository/com/example/app/0.0.1-SNAPSHOT/app-0.0.1-SNAPSHOT.jar"))
						.exists();
		assertThat(new File(
				"target/thin/test/repository/org/springframework/spring-beans/4.3.8.RELEASE/spring-beans-4.3.8.RELEASE.jar"))
						.exists();
	}

}
