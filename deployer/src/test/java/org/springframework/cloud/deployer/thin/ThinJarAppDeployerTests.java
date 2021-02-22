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

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.deployer.spi.app.DeploymentState;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinJarAppDeployerTests {

	private static ThinJarAppDeployer deployer = new ThinJarAppDeployer();

	// Counter for performance testing
	private final static int COUNT = 2;

	@Test
	@RepeatedTest(COUNT)
	public void appFromJarFile() throws Exception {
		String deployed = deploy("empty");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(deployed);
	}

	@Test
	@RepeatedTest(COUNT)
	public void appFromTargetClasses() throws Exception {
		String deployed = deploy(
				new FileSystemResource("../samples/simple/target/classes"), "other");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(deployed);
	}

	@Test
	@RepeatedTest(COUNT)
	public void appFromPom() throws Exception {
		String deployed = deploy(new FileSystemResource("src/test/resources/apps/app"),
				"app");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(deployed);
	}

	@Test
	@RepeatedTest(COUNT)
	public void appFromDirectoryWithProperties() throws Exception {
		String deployed = deploy(new FileSystemResource("src/test/resources/apps/props"),
				"props");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(deployed);
	}

	@Test
	@RepeatedTest(COUNT)
	public void twoApps() throws Exception {
		String first = deploy("empty");
		String second = deploy("cloud");
		// Deployment is blocking so it either failed or succeeded.
		assertThat(deployer.status(first).getState()).isEqualTo(DeploymentState.deployed);
		assertThat(deployer.status(second).getState())
				.isEqualTo(DeploymentState.deployed);
		deployer.undeploy(first);
		deployer.undeploy(second);
	}

	@Test
	@RepeatedTest(COUNT)
	public void appFromJarFileFails() throws Exception {
		String deployed = deploy("cloud", "--fail");
		assertThat(deployer.status(deployed).getState())
				.isEqualTo(DeploymentState.failed);
		deployer.undeploy(deployed);
	}

	String deploy(String jarName, String... args) {
		Resource resource = new FileSystemResource("../samples/tests/target/it/" + jarName
				+ "/target/" + jarName + "-0.0.1-SNAPSHOT.jar");
		return deploy(resource, jarName, args);
	}

	String deploy(Resource resource, String name, String... args) {
		AppDefinition definition = new AppDefinition(name,
				Collections.<String, String>emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.<String, String>emptyMap(), Arrays.asList(args));
		String deployed = deployer.deploy(request);
		return deployed;
	}

	public static void main(String[] args) {
		// Use this main method for leak detection (heap and non-heap, including classes
		// loaded should be variable but stable)
		ThinJarAppDeployerTests deployer = new ThinJarAppDeployerTests();
		while (true) {
			String deployed = deployer.deploy("cloud");
			ThinJarAppDeployerTests.deployer.undeploy(deployed);
		}
	}

}
