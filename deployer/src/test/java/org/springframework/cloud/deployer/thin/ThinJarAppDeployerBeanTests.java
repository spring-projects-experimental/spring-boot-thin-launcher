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

import javax.sql.DataSource;

import org.junit.Test;

import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinJarAppDeployerBeanTests {

	private static ThinJarAppDeployer deployer = new ThinJarAppDeployer();

	@Test
	public void getBeans() throws Exception {
		String deployed = deploy("app-with-db-in-lib-properties.jar");
		assertThat(deployer.getBeansOfType(deployed, DataSource.class)).isNotEmpty();
		deployer.undeploy(deployed);
	}

	@Test
	public void getBean() throws Exception {
		String deployed = deploy("app-with-db-in-lib-properties.jar");
		assertThat(deployer.getBean(deployed, DataSource.class)).isNotNull();
		deployer.undeploy(deployed);
	}

	String deploy(String jarName, String... args) {
		Resource resource = new FileSystemResource("src/test/resources/" + jarName);
		AppDefinition definition = new AppDefinition(jarName,
				Collections.<String, String>emptyMap());
		AppDeploymentRequest request = new AppDeploymentRequest(definition, resource,
				Collections.<String, String>emptyMap(), Arrays.asList(args));
		String deployed = deployer.deploy(request);
		return deployed;
	}

}
