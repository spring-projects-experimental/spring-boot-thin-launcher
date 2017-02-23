package org.springframework.boot.loader.thin;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.eclipse.aether.graph.Dependency;
import org.junit.Test;

import org.springframework.boot.loader.thin.ArchiveUtils;
import org.springframework.boot.loader.thin.DependencyResolver;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyResolverTests {

	private DependencyResolver resolver = DependencyResolver.instance();

	@Test
	public void localPom() throws Exception {
		Resource resource = new FileSystemResource(new File("pom.xml"));
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies)
				.filteredOn(d -> d.getArtifact().getArtifactId().equals("maven-settings"))
				.hasSize(1);
	}

	@Test
	public void emptyPom() throws Exception {
		Resource resource = new ClassPathResource("META-INF/thin/empty-pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isEqualTo(0);
	}

	@Test
	public void petclinic() throws Exception {
		Resource resource = new ClassPathResource("apps/petclinic/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		// System.err.println(dependencies);
		assertThat(dependencies.size()).isGreaterThan(20);
		// Resolved from a placeholder version
		assertThat(dependencies).filteredOn("artifact.artifactId", "bootstrap").first()
				.matches(d -> "3.3.6".equals(d.getArtifact().getVersion()),
						"correct version");
		// Transitive from a starter
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-starter")
				.first()
				.matches(d -> "1.4.2.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void pomWithBom() throws Exception {
		Resource resource = new ClassPathResource("apps/cloud/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(20);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-cloud-context")
				.first()
				.matches(d -> "1.1.7.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void pomInJar() throws Exception {
		Resource resource = new UrlResource(
				"jar:file:src/test/resources/app-with-web-in-lib-properties.jar!/META-INF/maven/com.example/app/pom.xml");
		assertThat(resource.exists()).isTrue();
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
	}

	@Test
	public void propertiesWithDatabase() throws Exception {
		Resource resource = new UrlResource(ArchiveUtils
				.getArchive("src/test/resources/app-with-web-and-cloud-config.jar")
				.getUrl()).createRelative("META-INF/maven/com.example/app/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(
						new ClassPathResource("apps/db/META-INF/thin.properties")));
		assertThat(dependencies).size().isGreaterThan(3);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-jdbc").first()
				.matches(d -> "4.2.8.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
		// thin.properties changes bom version
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.matches(d -> "1.3.8.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
		;
	}

	@Test
	public void libsWithProfile() throws Exception {
		Resource resource = new UrlResource(ArchiveUtils
				.getArchive("src/test/resources/app-with-web-and-cloud-config.jar")
				.getUrl()).createRelative("META-INF/maven/com.example/app/pom.xml");
		Properties properties = PropertiesLoaderUtils.loadProperties(
				new ClassPathResource("apps/eureka/META-INF/thin.properties"));
		PropertiesLoaderUtils.fillProperties(properties,
				new ClassPathResource("apps/eureka/META-INF/thin-extra.properties"));
		List<Dependency> dependencies = resolver.dependencies(resource, properties);
		assertThat(dependencies).size().isGreaterThan(3);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.matches(d -> "1.4.1.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void dependenciesWithPlaceholders() throws Exception {
		Resource resource = new ClassPathResource("apps/placeholders/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.matches(d -> "1.3.8.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void dependenciesWithParent() throws Exception {
		Resource resource = new ClassPathResource("apps/parent-properties/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		// Weird combo of spring version picked from the wrong property (but it resolves,
		// which is the test)
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-core").first()
				.matches(d -> "4.1.3.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void dependenciesWithProjectVariables() throws Exception {
		Resource resource = new ClassPathResource("apps/projectvariables/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.matches(d -> "1.3.8.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void dependenciesWithParentOverride() throws Exception {
		Resource resource = new ClassPathResource(
				"apps/parent-properties-override/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-core").first()
				.matches(d -> "4.3.5.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

	@Test
	public void dependencyManagementPom() throws Exception {
		Resource resource = new ClassPathResource("apps/dep-man/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(3);
		// pom changes spring-context
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-context")
				.first()
				.matches(d -> "4.3.5.RELEASE".equals(d.getArtifact().getVersion()),
						"correct version");
	}

}
