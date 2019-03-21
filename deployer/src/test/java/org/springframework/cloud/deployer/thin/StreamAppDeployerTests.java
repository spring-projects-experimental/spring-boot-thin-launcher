/*
 * Copyright 2012-2015 the original author or authors.
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

package org.springframework.cloud.deployer.thin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import org.springframework.boot.loader.tools.LogbackInitializer;
import org.springframework.cloud.deployer.resource.maven.MavenProperties;
import org.springframework.cloud.deployer.resource.maven.MavenProperties.RemoteRepository;
import org.springframework.cloud.deployer.resource.maven.MavenResource;
import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(Parameterized.class)
public class StreamAppDeployerTests {

	static {
		LogbackInitializer.initialize();
	}

	private static ThinJarAppDeployer deployer = new ThinJarAppDeployer();

	@Parameterized.Parameters
	public static List<Object[]> data() {
		// Repeat a couple of times to ensure it's consistent
		return Arrays.asList(new Object[2][0]);
	}

	@Test
	public void tickTock() throws Exception {
		String first = deploy(
				"org.springframework.cloud.stream.app:time-source-rabbit:2.0.1.RELEASE");
		String second = deploy(
				"org.springframework.cloud.stream.app:log-sink-rabbit:2.0.1.RELEASE",
				"--spring.cloud.stream.bindings.input.destination=output");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		assertThat(deployer.status(second).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
		deployer.undeploy(second);
	}

	private String deploy(String jarName, String... args) throws Exception {
		MavenProperties props = new MavenProperties();
		props.setRemoteRepositories(Collections.singletonMap("central",
				new RemoteRepository("https://repo1.maven.org/maven2/")));
		Resource resource = MavenResource.parse(jarName, props);
		AppDefinition definition = new AppDefinition(resource.getFilename(),
				Collections.<String, String>emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.<String, String>emptyMap(), Arrays.<String>asList(args));
		String deployed = deployer.deploy(request);
		return deployed;
	}

}
