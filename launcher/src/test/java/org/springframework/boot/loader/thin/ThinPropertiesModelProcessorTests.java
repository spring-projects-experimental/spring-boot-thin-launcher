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
package org.springframework.boot.loader.thin;

import org.apache.maven.model.Dependency;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class ThinPropertiesModelProcessorTests {

	@Test
	public void coords() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:1.0");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEqualTo("1.0");
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("jar");
	}

	@Test
	public void snapshot() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:1.0-SNAPSHOT");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEqualTo("1.0-SNAPSHOT");
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("jar");
	}

	@Test
	public void releaseTrain() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:Foo.RELEASE");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEqualTo("Foo.RELEASE");
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("jar");
	}

	@Test
	public void versionless() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEmpty();
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("jar");
	}

	@Test
	public void extension() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:zip:weird:1.0");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEqualTo("1.0");
		assertThat(artifact.getClassifier()).isEqualTo("weird");
		assertThat(artifact.getExtension()).isEqualTo("zip");
	}

	@Test
	public void versionlessExtension() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:zip:weird");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEmpty();
		assertThat(artifact.getClassifier()).isEqualTo("weird");
		assertThat(artifact.getExtension()).isEqualTo("zip");
	}

	@Test
	public void extensionOnly() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:zip:1.0");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEqualTo("1.0");
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("zip");
	}

	@Test
	public void versionlessExtensionOnly() {
		DefaultArtifact artifact = ThinPropertiesModelProcessor
				.artifact("com.example:foo:zip");
		assertThat(artifact.getGroupId()).isEqualTo("com.example");
		assertThat(artifact.getArtifactId()).isEqualTo("foo");
		assertThat(artifact.getVersion()).isEmpty();
		assertThat(artifact.getClassifier()).isEqualTo("");
		assertThat(artifact.getExtension()).isEqualTo("zip");
	}

	@Test
	public void classifierMatches() {
		Dependency artifact = ThinPropertiesModelProcessor.dependency(
				ThinPropertiesModelProcessor.artifact("com.example:foo:zip:weird:1.0"));
		Dependency other = ThinPropertiesModelProcessor.dependency(
				ThinPropertiesModelProcessor.artifact("com.example:foo:zip:weird:2.0"));
		assertThat(ThinPropertiesModelProcessor.isSameArtifact(artifact, other)).isTrue();
	}

	@Test
	public void classifierEmpty() {
		Dependency artifact = ThinPropertiesModelProcessor
				.dependency(ThinPropertiesModelProcessor.artifact("com.example:foo:zip"));
		Dependency other = ThinPropertiesModelProcessor
				.dependency(ThinPropertiesModelProcessor.artifact("com.example:foo:zip"));
		assertThat(ThinPropertiesModelProcessor.isSameArtifact(artifact, other)).isTrue();
	}

}
