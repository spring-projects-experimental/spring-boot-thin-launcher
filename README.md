Prototype "thin" jar launcher for java apps. See https://github.com/spring-projects/spring-boot/issues/1813 for more discussion and ideas.

TODO:

* [X] Support the wrapper as a layout in Spring Boot build plugins(*)
* [X] Extract `AetherEngine` and re-use it in Spring Boot CLI(**)
* [X] Close old PR and send a new one for custom layouts in build plugin
* [X] Deploy jars to snapshot repo at repo.spring.io
* [X] Make it easy to override the dependencies at runtime (e.g. rolling upgrades of library jars for security patches)
* [X] Add a "dry run" or "download only" feature so grab the dependencies and warm up the local cache, but not run the app
* [X] Hone the dependencies in the launcher a bit (some optional stuff probably still there)
* [X] Either autogenerate the `lib.properties` or find a way to model the pom without a lot of extra machinery
* [X] Worry about the other stuff on the classpath of the launcher (e.g. spring-core)
* [X] Make it work in Cloud Foundry
* [ ] Work with Ben to make it a nice experience in Cloud Foundry
* [X] Support for boms
* [X] Support for exclusions
* [ ] Support for configuring launcher via manifest and/or properties file
* [ ] Support for configuring wrapper via env vars  and/or properties file
* [X] Generate lib.properties during build (e.g. to support Gradle)
* [ ] Experiment with "container" apps and multi-tenant/ephemeral child contexts
* [ ] Deployment time support for the dry run to assist with CI pipelines

(*) Implemented in Spring Boot, not in this project.
(**) Implemented in a pull request to Spring Boot, not in this
project.

## Getting Started

Build this project locally:

```
$ ./mvnw clean install
```

Then run the "app" jar:

```
$ java -jar ./app/target/*.jar
```

(It starts an empty Spring Boot app with Tomcat.)

## How does it Work?

Inspect the "app" jar. It is just a regular jar file with the app
classes in it and two extra features:

1. The `ThinJarWrapper` class has been added.
2. A `META-INF/lib.properties` which lists the dependencies of the app.

When the app runs the main method is in the `ThinJarWrapper`. It's job
is to download another jar file that you just built (the "launcher"),
or locate it in your local Maven repo if it can. The wrapper downloads
the launcher (if it needs to), or else uses the cached version in your
local Maven repository.

The launcher then takes over and reads the `lib.properties`,
downloading the dependencies (and all transitives) as necessary, and
setting up a new class loader with them all on the classpath. It then
runs the application's own main method with that class loader.  If the
`lib.properties` is not there then the `pom.xml` is used instead (it
can be in the root of the jar or in the standard `META-INF/maven`
location).

The app jar in the demo is build using the Spring Boot plugin and a
custom `Layout` (so it only builds with Spring Boot 1.5.x),

## Caching

All jar files are cached in the local Maven repository, so if you are
building and running the same app repeatedly, it should be faster
after the first time, or if the local repo is already warm.

The local repository can be re-located by setting a System property "thin.root". For example to use the current directory:

```
$ java -Dthin.root=. -jar app/target/*.jar
```

This will download all the dependencies to `${thin.root}/repository`,
and look for Maven settings in `${thin.root}/settings.xml`.

You can also do a "dry run", just to warm up the cache and not run the
app, by setting a System property "thins.dryrun" (to any value). In
fact, since you don't need the application code for this (except the
`META-INF/lib.properties`), you could run only the launcher, or the
wrapper, which might be a useful trick for laying down a file system
layer in a container image, for example.


## Upgrades

You can upgrade all the libraries by changing the `lib.properties`.

## Packaging

The thin-launcher provides its own custom layout for the Spring Boot
plugins. If this layout is used then the jar built by Spring Boot will
be executable and thin.

### Maven

```xml
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
				<version>${spring-boot.version}</version>
				<configuration>
					<layout>THIN</layout>
				</configuration>
				<dependencies>
					<dependency>
						<groupId>org.springframework.boot.experimental</groupId>
						<artifactId>spring-boot-thin-launcher</artifactId>
						<version>${wrapper.version}</version>
					</dependency>
				</dependencies>
			</plugin>
```

### Gradle

```groovy
buildscript {
	ext {
		springBootVersion = '1.5.0.BUILD-SNAPSHOT'
		wrapperVersion = '0.0.1.BUILD-SNAPSHOT'
	}
	repositories {
		mavenLocal()
		mavenCentral()
	}
	dependencies {
		classpath("org.springframework.boot.experimental:spring-boot-thin-gradle-plugin:${wrapperVersion}")
		classpath("org.springframework.boot:spring-boot-gradle-plugin:${springBootVersion}")
	}
}
springBoot { layout = 'THIN' }
```


## Creating the Metadata

### Maven

The `pom.xml` works just fine, so Maven projects can just rely on
that. If you want to generate a properties file there are a few
options.

There's an existing maven plugin that can list dependencies into a
properties file. We could support it's format as well as or instead of
lib.properties. Example:

```xml
			<plugin>
				<groupId>org.apache.servicemix.tooling</groupId>
				<artifactId>depends-maven-plugin</artifactId>
				<executions>
					<execution>
						<id></id>
						<phase>prepare-package</phase>
						<goals>
							<goal>generate-depends-file</goal>
						</goals>
						<inherited>false</inherited>
						<configuration>
						</configuration>
					</execution>
				</executions>
			</plugin>
```

Also there is the `effective-pom`, which is easy to generate and can be transformed using XSLT (for example).

### Gradle

There doesn't seem to be an equivalent plugin in Gradle land, so we provide a thin launcher plugin
specifically for creating the `lib.properties` file. It is activated just by  e.g:

```groovy
buildscript {
	ext {
		springBootVersion = '1.5.0.BUILD-SNAPSHOT'
		wrapperVersion = '0.0.1.BUILD-SNAPSHOT'
	}
	repositories {
		mavenLocal()
		mavenCentral()
	}
	dependencies {
		classpath("org.springframework.boot.experimental:spring-boot-thin-gradle-plugin:${wrapperVersion}")
		classpath("org.springframework.boot:spring-boot-gradle-plugin:${springBootVersion}")
	}
}

...
apply plugin: 'spring-boot'
apply plugin: 'org.springframework.boot.experimental.thin-launcher'
```

### Generating a POM

Instead of the `lib.properties` you can generate a pom in Gradle:

```groovy
apply plugin: 'maven'

task writePom << {
	pom {}.withXml{ 
		dependencyManagement.pomConfigurer.configurePom (it.asNode())
	}.writeTo("$buildDir/resources/main/pom.xml")
}

jar.dependsOn = [writePom]
```

There are some problems with the generated poms (e.g. exclusions are
not generated correctly). Using the dependency management section of
the build config works a little better, e.g instead of this:

```groovy
configurations { compile.exclude module: 'spring-boot-starter-tomcat' }
```

(per the Spring Boot user guide), use this:

```groovy
dependencyManagement {
	dependencies {
		dependency("org.springframework.boot:spring-boot-starter-web:${springBootVersion}") { exclude 'org.springframework.boot:spring-boot-starter-tomcat' }
	}
}
```
