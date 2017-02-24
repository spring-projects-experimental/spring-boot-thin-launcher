package org.springframework.boot.loader.thin;

import java.io.File;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.assertj.core.api.Condition;
import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyResolverModelTests {

	private DependencyResolver resolver = DependencyResolver.instance();

	@Test
	public void localPom() throws Exception {
		Resource resource = new FileSystemResource(new File("pom.xml"));
		Model model = resolver.readModel(resource);
		assertThat(model.getDependencies().size()).isGreaterThan(3);
	}

	@Test
	public void emptyPom() throws Exception {
		Resource resource = new ClassPathResource("META-INF/thin/empty-pom.xml");
		Model model = resolver.readModel(resource);
		assertThat(model.getDependencies().size()).isEqualTo(0);
	}

	@Test
	public void petclinic() throws Exception {
		Resource resource = new ClassPathResource("apps/petclinic/pom.xml");
		Model model = resolver.readModel(resource);
		assertThat(model.getDependencies().size()).isGreaterThan(1);
		assertThat(model.getDependencies()).filteredOn("artifactId", "bootstrap")
				.hasSize(1);
		assertThat(model.getDependencies()).filteredOn("artifactId", "bootstrap").first()
				.is(version("3.3.6"));
	}

	static Condition<Dependency> version(final String version) {
		return new Condition<Dependency>("artifact matches " + version) {
			@Override
			public boolean matches(Dependency value) {
				return value.getVersion().equals(version);
			}
		};
	}
}
