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

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class MavenSettingsReaderTests {

	private MavenSettingsReader reader = new MavenSettingsReader("src/test/resources");

	@Test
	public void canReadSettings() {
		MavenSettings settings = reader.readSettings();
		assertThat(settings).isNotNull();
	}

	@Test
	public void proxyConfiguration() {
		DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
		MavenSettingsReader.applySettings(reader.readSettings(), session);
		RemoteRepository repository = new RemoteRepository.Builder("my-server", "default",
				"http://maven.example.com").build();
		Proxy proxy = session.getProxySelector().getProxy(repository);
		repository = new RemoteRepository.Builder(repository).setProxy(proxy).build();
		assertThat(proxy.getHost()).isEqualTo("proxy.example.com");
		AuthenticationContext authenticationContext = AuthenticationContext
				.forProxy(session, repository);
		assertThat(authenticationContext.get(AuthenticationContext.USERNAME))
				.isEqualTo("user");
		assertThat(authenticationContext.get(AuthenticationContext.PASSWORD))
				.isEqualTo("password");
	}

}
