package org.springframework.boot.loader.thin;

import java.io.File;
import java.util.List;
import java.util.Properties;

import org.assertj.core.api.Condition;
import org.eclipse.aether.graph.Dependency;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class DependencyResolverTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private DependencyResolver resolver = DependencyResolver.instance();

	@After
	public void clear() {
		System.clearProperty("maven.home");
	}

	@Test
	@Ignore("Some issues with reactor build here when top level version changes")
	public void localPom() throws Exception {
		Resource resource = new FileSystemResource(new File("pom.xml"));
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies).filteredOn("artifact.artifactId", "maven-settings")
				.hasSize(1);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-test")
				.isEmpty();
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
				.is(version("3.3.6"));
		// Transitive from a starter
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-starter")
				.first().is(version("1.4.2.RELEASE"));
		// Test scope
		assertThat(dependencies)
				.filteredOn("artifact.artifactId", "spring-boot-starter-test").isEmpty();
	}

	@Test
	public void mavenProfiles() throws Exception {
		Resource resource = new ClassPathResource("apps/profiles/pom.xml");
		Properties props = new Properties();
		props.setProperty("thin.profile", "secure");
		List<Dependency> dependencies = resolver.dependencies(resource, props);
		assertThat(dependencies.size()).isGreaterThan(4);
		// Transitive from a starter
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-starter")
				.first().is(version("2.1.0.RELEASE"));
		assertThat(dependencies)
				.filteredOn("artifact.artifactId", "spring-boot-starter-security")
				.isNotEmpty();
		// Test scope
		assertThat(dependencies)
				.filteredOn("artifact.artifactId", "spring-boot-starter-test").isEmpty();
	}

	@Test
	public void preresolved() throws Exception {
		Resource resource = new ClassPathResource("apps/petclinic-preresolved/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(new ClassPathResource(
						"apps/petclinic-preresolved/META-INF/thin.properties")));
		// System.err.println(dependencies);
		assertThat(dependencies.size()).isGreaterThan(20);
		// Resolved from a placeholder version
		assertThat(dependencies).filteredOn("artifact.artifactId", "bootstrap").first()
				.is(version("3.3.6"));
		// Transitive from a starter
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-starter")
				.first().is(version("1.4.2.RELEASE"));
		assertThat(dependencies).filteredOn("artifact.artifactId", "bootstrap").first()
				.is(resolved());
	}

	@Test
	public void preresolvedClassifier() throws Exception {
		Resource resource = new ClassPathResource("apps/preresolved-classifier/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(new ClassPathResource(
						"apps/preresolved-classifier/META-INF/thin.properties")));
		// System.err.println(dependencies);
		assertThat(dependencies.size()).isEqualTo(2);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-test")
				.first().is(version("2.1.0.RELEASE"));
		assertThat(dependencies).filteredOn("artifact.classifier", "tests").first()
				.is(resolved());
	}

	@Test
	public void pomWithBom() throws Exception {
		Resource resource = new ClassPathResource("apps/cloud/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(20);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-cloud-context")
				.first().is(version("1.1.7.RELEASE"));
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-test")
				.isEmpty();
	}

	@Test
	public void exclusions() throws Exception {
		Resource resource = new ClassPathResource("apps/exclusions/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(new ClassPathResource(
						"apps/exclusions/META-INF/thin.properties")));
		assertThat(dependencies.size()).isGreaterThan(20);
		assertThat(dependencies).filteredOn("artifact.artifactId", "jetty-server").first()
				.is(version("9.3.11.v20160721"));
		assertThat(dependencies).filteredOn("artifact.artifactId", "tomcat-embed-core")
				.isEmpty();
	}

	@Test
	public void inclusions() throws Exception {
		Resource resource = new ClassPathResource("apps/inclusions/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(new ClassPathResource(
						"apps/inclusions/META-INF/thin.properties")));
		assertThat(dependencies.size()).isGreaterThan(20);
		assertThat(dependencies).filteredOn("artifact.artifactId", "jackson-core")
				.isNotEmpty();
	}

	@Test
	public void classifier() throws Exception {
		Resource resource = new ClassPathResource("apps/classifier/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(2);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-test")
				.hasSize(2);
	}

	@Test
	public void excludeLast() throws Exception {
		Resource resource = new ClassPathResource("apps/excludelast/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(new ClassPathResource(
						"apps/excludelast/META-INF/thin.properties")));
		assertThat(dependencies.size()).isGreaterThan(20);
		assertThat(dependencies).filteredOn("artifact.artifactId", "amqp-client").first()
				.is(version("3.6.3"));
		assertThat(dependencies).filteredOn("artifact.artifactId", "http-client")
				.isEmpty();
	}

	@Test
	public void provided() throws Exception {
		Resource resource = new ClassPathResource("apps/provided/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(
						new ClassPathResource("apps/provided/META-INF/thin.properties")));
		assertThat(dependencies.size()).isGreaterThan(15);
		assertThat(dependencies).filteredOn("artifact.artifactId", "tomcat-embed-core")
				.first().is(version("8.5.5"));
	}

	@Test
	public void testOverride() throws Exception {
		Resource resource = new ClassPathResource("apps/test/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(
						new ClassPathResource("apps/test/META-INF/thin.properties")));
		assertThat(dependencies.size()).isGreaterThan(15);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot-starter-web")
				.first().is(version("1.5.3.RELEASE"));
	}

	@Test
	public void excluded() throws Exception {
		Resource resource = new ClassPathResource("apps/excluded/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(
						new ClassPathResource("apps/excluded/META-INF/thin.properties")));
		assertThat(dependencies).filteredOn("artifact.artifactId", "tomcat-embed-core")
				.isEmpty();
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
				.is(version("4.2.8.RELEASE"));
		// thin.properties changes bom version
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.is(version("1.3.8.RELEASE"));
		;
	}

	@Test
	public void propertiesInline() throws Exception {
		Resource resource = new ClassPathResource("apps/inline/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource,
				PropertiesLoaderUtils.loadProperties(
						new ClassPathResource("apps/inline/META-INF/thin.properties")));
		assertThat(dependencies).size().isGreaterThan(3);
		// thin.properties has placeholder for bom version
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.is(version("1.3.8.RELEASE"));
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
				.is(version("1.4.1.RELEASE"));
	}

	@Test
	public void dependenciesWithPlaceholders() throws Exception {
		Resource resource = new ClassPathResource("apps/placeholders/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.is(version("1.3.8.RELEASE"));
	}

	@Test
	public void dependenciesWithParent() throws Exception {
		Resource resource = new ClassPathResource("apps/parent-properties/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		// Weird combo of spring version picked from the wrong property (but it resolves,
		// which is the test)
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-core").first()
				.is(version("4.1.3.RELEASE"));
	}

	@Test
	public void dependenciesWithProjectVariables() throws Exception {
		Resource resource = new ClassPathResource("apps/projectvariables/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-boot").first()
				.is(version("1.3.8.RELEASE"));
	}

	@Test
	public void dependenciesWithParentOverride() throws Exception {
		Resource resource = new ClassPathResource(
				"apps/parent-properties-override/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(10);
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-core").first()
				.is(version("4.3.5.RELEASE"));
	}

	@Test
	public void dependencyManagementPom() throws Exception {
		Resource resource = new ClassPathResource("apps/dep-man/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(3);
		// pom changes spring-context
		assertThat(dependencies).filteredOn("artifact.artifactId", "spring-context")
				.first().is(version("4.3.5.RELEASE"));
	}

	@Test
	public void missing() throws Exception {
		// LogUtils.setLogLevel(Level.DEBUG);
		Resource resource = new ClassPathResource("apps/missing/pom.xml");
		expected.expect(RuntimeException.class);
		expected.expectMessage("spring-web:jar:X.X.X");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(20);
	}

	@Test
	@Ignore("Set up a secure server and declare it in your settings.xml to run this test. Point it at your .m2/repository so it can resolve the app sample from this project.")
	public void authentication() throws Exception {
		FileSystemUtils.deleteRecursively(new File("target/root"));
		System.setProperty("maven.home", "target/root");
		Resource resource = new ClassPathResource("apps/authentication/pom.xml");
		List<Dependency> dependencies = resolver.dependencies(resource);
		assertThat(dependencies.size()).isGreaterThan(16);
	}

	static Condition<Dependency> version(final String version) {
		return new Condition<Dependency>("artifact matches " + version) {
			@Override
			public boolean matches(Dependency value) {
				return value.getArtifact().getVersion().equals(version);
			}
		};
	}

	static Condition<Dependency> resolved() {
		return new Condition<Dependency>("artifact is resolved") {
			@Override
			public boolean matches(Dependency value) {
				return value.getArtifact().getFile() != null;
			}
		};
	}

}
