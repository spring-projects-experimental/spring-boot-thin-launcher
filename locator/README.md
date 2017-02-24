This module is a tiny utility for merging resources in a shaded
jar. It works a bit like the built in `ServicesResourceTransformer` in
that it relocates class names, but it also allows the user to
parameterise the resource location.

Example:

```xml
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-shade-plugin</artifactId>
	<configuration>
		<keepDependenciesWithProvidedScope>true</keepDependenciesWithProvidedScope>
		<shadedClassifierName>exec</shadedClassifierName>
		<shadedArtifactAttached>true</shadedArtifactAttached>
		<transformers>
			<transformer
				implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
				<mainClass>org.springframework.boot.loader.thin.ThinJarLauncher</mainClass>
			</transformer>
			<transformer
				implementation="org.apache.maven.plugins.shade.resource.ComponentsXmlResourceTransformer" />
			<transformer
				implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer" />
			<transformer
				implementation="org.springframework.boot.loader.thin.maven.RelocatingAppendingResourceTransformer">
				<resource>META-INF/sisu/javax.inject.Named</resource>
			</transformer>
		</transformers>
		<relocations>
			<relocation>
				<pattern>ch.qos</pattern>
				<shadedPattern>hidden.ch.qos</shadedPattern>
			</relocation>
			<relocation>
				<pattern>org.slf4j</pattern>
				<shadedPattern>hidden.org.slf4j</shadedPattern>
			</relocation>
			<relocation>
				<pattern>org.eclipse</pattern>
				<shadedPattern>hidden.org.eclipse</shadedPattern>
			</relocation>
			<relocation>
				<pattern>com.google</pattern>
				<shadedPattern>hidden.com.google</shadedPattern>
			</relocation>
		</relocations>
		<filters>
			<filter>
				<artifact>*:*</artifact>
				<excludes>
					<exclude>META-INF/maven/**</exclude>
				</excludes>
			</filter>
		</filters>
	</configuration>
	<executions>
		<execution>
			<id>shade-runtime-dependencies</id>
			<phase>package</phase>
			<goals>
				<goal>shade</goal>
			</goals>
		</execution>
	</executions>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot.experimental</groupId>
			<artifactId>spring-boot-thin-launcher-shade-locator</artifactId>
			<version>${locator.version}</version>
		</dependency>
	</dependencies>
</plugin>
```

Version 1.0.0.RELEASE is available in Maven Central.
