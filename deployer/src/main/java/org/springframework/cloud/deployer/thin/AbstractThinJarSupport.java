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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.loader.thin.ThinJarLauncher;
import org.springframework.cloud.deployer.spi.app.AppDeployer;
import org.springframework.cloud.deployer.spi.core.AppDeploymentRequest;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
public class AbstractThinJarSupport {

	private static final String JMX_DEFAULT_DOMAIN_KEY = "spring.jmx.default-domain";

	private Map<String, ThinJarAppWrapper> apps = new LinkedHashMap<>();

	private String name = "thin";

	private String[] profiles = new String[0];

	public AbstractThinJarSupport() {
		this("thin");
	}

	public AbstractThinJarSupport(String name, String... profiles) {
		this.name = name;
		this.profiles = profiles;
	}

	public String deploy(AppDeploymentRequest request) {
		ThinJarAppWrapper wrapper = new ThinJarAppWrapper(request.getResource(), getName(request),
				getProfiles(request));
		String id = wrapper.getId();
		if (!apps.containsKey(id)) {
			apps.put(id, wrapper);
		}
		else {
			wrapper = apps.get(id);
		}
		wrapper.run(getProperties(request), request.getCommandlineArguments());
		return id;
	}

	protected Map<String, String> getProperties(AppDeploymentRequest request) {
		Map<String, String> properties = new LinkedHashMap<>(
				request.getDefinition().getProperties());
		String group = request.getDeploymentProperties()
				.get(AppDeployer.GROUP_PROPERTY_KEY);
		if (group == null) {
			group = "deployer";
		}
		String deploymentId = String.format("%s.%s", group,
				request.getDefinition().getName());
		properties.put(JMX_DEFAULT_DOMAIN_KEY, deploymentId);
		properties.put("endpoints.shutdown.enabled", "true");
		properties.put("endpoints.jmx.unique-names", "true");
		if (group != null) {
			properties.put("spring.cloud.application.group", group);
		}
		return properties;
	}

	private String[] getProfiles(AppDeploymentRequest request) {
		if (request.getDeploymentProperties()
				.containsKey(AppDeployer.PREFIX + ThinJarLauncher.THIN_PROFILE)) {
			return StringUtils
					.commaDelimitedListToStringArray(request.getDeploymentProperties()
							.get(AppDeployer.PREFIX + ThinJarLauncher.THIN_PROFILE));
		}
		return this.profiles;
	}

	private String getName(AppDeploymentRequest request) {
		if (request.getDeploymentProperties()
				.containsKey(AppDeployer.PREFIX + ThinJarLauncher.THIN_NAME)) {
			return request.getDeploymentProperties()
					.get(AppDeployer.PREFIX + ThinJarLauncher.THIN_NAME);
		}
		return this.name;
	}

	public void cancel(String id) {
		if (apps.containsKey(id)) {
			apps.get(id).cancel();
		}
	}

	protected ThinJarAppWrapper getWrapper(String id) {
		return apps.get(id);
	}

}