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

import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.cloud.deployer.spi.local.LocalAppDeployer;
import org.springframework.cloud.deployer.spi.local.LocalDeployerProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
@RunWith(Parameterized.class)
public class LocalAppDeployerTests {

	private static LocalAppDeployer deployer = createDeployer();

	private static LocalAppDeployer createDeployer() {
		LocalDeployerProperties properties = new LocalDeployerProperties();
		properties.setJavaOpts(
				"-noverify -XX:TieredStopAtLevel=1 -Xss256K -Xms16M -Xmx256M -XX:MaxMetaspaceSize=128M -Djava.security.egd=file:/dev/./urandom");
		return new LocalAppDeployer(properties);
	}

	@Parameterized.Parameters
	public static List<Object[]> data() {
		// Repeat a couple of times to ensure it's consistent
		return Arrays.asList(new Object[2][0]);
	}

	@Test
	public void appFromJarFile() throws Exception {
		String deployed = deploy("app-with-db-in-lib-properties.jar");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(deployed);
	}

	@Test
	public void twoApps() throws Exception {
		String first = deploy("app-with-db-in-lib-properties.jar");
		String second = deploy("app-with-cloud-in-lib-properties.jar");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		assertThat(deployer.status(second).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
		deployer.undeploy(second);
	}

	@Test
	public void appFromJarFileFails() throws Exception {
		String deployed = deploy("app-with-cloud-in-lib-properties.jar", "--fail");
		Thread.sleep(500L);
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.failed);
		deployer.undeploy(deployed);
	}

	private String deploy(String jarName, String... args) {
		Resource resource = new FileSystemResource("src/test/resources/" + jarName);
		AppDefinition definition = new AppDefinition(jarName,
				Collections.<String, String>emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.singletonMap("spring.cloud.deployer.group", "test"),
				Arrays.asList(args));
		String deployed = deployer.deploy(request);
		DeploymentState state = deployer.status(deployed).getState();
		while (state == DeploymentState.deploying) {
			try {
				Thread.sleep(100L);
				state = deployer.status(deployed).getState();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		System.err.println("State: " + state);
		return deployed;
	}

	public static void main(String[] args) {
		// Use this main method for leak detection (heap and non-heap, including classes
		// loaded should be variable but stable)
		LocalAppDeployerTests deployer = new LocalAppDeployerTests();
		while (true) {
			String deployed = deployer.deploy("app-with-cloud-in-lib-properties.jar",
					"--server.port=0");
			LocalAppDeployerTests.deployer.undeploy(deployed);
		}
	}

}
