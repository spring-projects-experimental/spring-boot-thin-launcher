A "thin" jar launcher for java apps. Version 1.0.21.RELEASE is in Maven Central. See https://github.com/spring-projects/spring-boot/issues/1813 for more discussion and ideas. [![ci.spring.io](https://ci.spring.io/api/v1/teams/spring-team/pipelines/spring-boot-thin-launcher/badge)](https://ci.spring.io/teams/spring-team/pipelines/spring-boot-thin-launcher)

## Getting Started

The thin-launcher provides its own custom layout for the Spring Boot
plugins. If this layout is used then the jar built by Spring Boot will
be executable and thin.

NOTE: if you are using a snapshot version of the thin launcher you
either need to build it locally or include the snapshot repository
declarations. You can use https://start.spring.io to find suitable
repository declarations for Maven and Gradle, or look at the samples
in this project.


With Maven, build a Spring Boot application and add the layout. This
means adding it to the Spring Boot plugin declaration:

```xml
<plugin>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-maven-plugin</artifactId>
	<version>${spring-boot.version}</version>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot.experimental</groupId>
			<artifactId>spring-boot-thin-layout</artifactId>
			<version>${wrapper.version}</version>
		</dependency>
	</dependencies>
</plugin>
```

and in Gradle for Spring Boot up to 1.5.x (you can use older versions of Spring Boot for the app, but the plugin has to be 1.5.x or later):

```groovy
buildscript {
	ext {
		springBootVersion = '1.5.6.RELEASE'
		wrapperVersion = '1.0.21.RELEASE'
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
apply plugin: 'maven'
apply plugin: 'org.springframework.boot.experimental.thin-launcher'
```

and for Spring Boot 2.0.x:


```groovy
buildscript {
	ext {
		springBootVersion = '2.0.1.RELEASE'
		wrapperVersion = '1.0.21.RELEASE'
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
apply plugin: 'maven'
apply plugin: 'io.spring.dependency-management'
apply plugin: 'org.springframework.boot'
apply plugin: 'org.springframework.boot.experimental.thin-launcher'
```

In Gradle you also need to generate a `pom.xml` or a `thin.properties` (unless you want to maintain it by hand). A `pom.xml` will be generated automatically by the "thinPom" task in the Thin Gradle plugin. It does this by calling out to the maven plugin and the dependency management plugin; the maven plugin is always present, and the dependency management plugin is present if you are using the Spring Boot plugin. To generate a `pom.xml` remember to apply the maven and Thin Gradle plugins.

NOTE: Gradle has a new `maven-publish` plugin that works with the new "standard" configurations (e.g. `runtimeOnly` replaces `runtime`). It doesn't work with the thin launcher plugin yet.

The generated pom goes in the normal maven place by default under `META-INF/maven`. You can configure the output directory by setting the "output" property of the "thinPom" task.

You can customize the generated `pom.xml`, or switch it off, by creating your own task in `build.gradle` and forcing the jar task to depend on it instead of "thinPom", or by simply not using the Thin Gradle plugin. Example (which just duplicates the default):

```groovy
task createPom {
	doLast {
		pom {
			withXml(dependencyManagement.pomConfigurer)
		}.writeTo("build/resources/main/META-INF/maven/${project.group}/${project.name}/pom.xml")
	}
}

jar.dependsOn = [createPom]
```

Instead of or as well as a `pom.xml` you could generate a `thin.properties` using `gradle thinProperties` (the task is always registered by the Thin Gradle plugin but is not executed by default). By default it shows up in `META-INF` in the built resources, so you need to run it before the jar is built, either manually, or via a task dependency, e.g.

```groovy
jar.dependsOn = [thinProperties]
```

The generated properties file is "computed" (it contains all the transitive dependencies), so if you have that, the dependencies from the `pom.xml` will be ignored.

If you look at the jar file produced by the build you will see that it
is "thin" (a few KB), but executable with `java -jar ...`.

## How does it Work?

Inspect the app jar that you built (or one of the samples in this
project) and notice that it is only a few KB. It is just a regular jar
file with the app classes in it and one or two extra features. The things
it needs to operate are:

1. The `ThinJarWrapper` class has been added.
2. Either a `pom.xml` and/or a `META-INF/thin.properties` which lists the dependencies of the app.

When the app runs the main method per the manifest is the
`ThinJarWrapper`. Its job is to locate another jar file (the
"launcher"). The wrapper downloads the launcher (if it needs to), or
else uses the cached version in your local Maven repository.

The launcher then takes over and reads the `pom.xml` (if present) and
the `thin.properties`, downloading the dependencies (and all
transitives) as necessary, and setting up a new class loader with them
all on the classpath. It then runs the application's own main method
with that class loader. The `pom.xml` can be in the root of the jar or
in the standard `META-INF/maven` location.

The app jar in the demo is built using the Spring Boot plugin and a
custom `Layout` (so it only builds with Spring Boot 1.5.x and above).

## Caching JARs

All jar files are cached in the local Maven repository, so if you are
building and running the same app repeatedly, it should be faster
after the first time, or if the local repo is already warm.

The local repository can be re-located by setting a System property
"thin.root". For example to use the current directory:

```
$ java -Dthin.root=. -jar app/target/*.jar
```

This will download all the dependencies to `${thin.root}/repository`,
and look for Maven settings in `${thin.root}/settings.xml`.

You can also do a "dry run", just to warm up the cache and not run the
app, by setting a System property or command line argument
"thin.dryrun" (to any value except "false"). In fact, since you don't
need the application code for this (except the
`META-INF/thin.properties`), you could run only the launcher, or the
wrapper whioch is contained in the launcher for convenience. This is a
useful trick for laying down a file system layer in a container image,
for example.

> NOTE: options for the `ThinJarLauncher` that are listed as
> `-Dthin.*` can also be provided as command line arguments
> (`--thin.*` per Spring Boot conventions), or as environment
> variables (`THIN_*` capitalized and underscored). The command line
> options are removed before passing down to the Boot app. The
> `ThinJarWrapper` also accepts system properties, environment
> variables or command line flags for its (smaller) set of optional
> arguments.

## Build Tools

### Maven

In addition to the Spring Boot layout there is an optional Maven
plugin which can be used to do the dry run (download and cache the
dependencies) for the current project, or for any project that has an
executable thin jar in the same format. The "app" sample in this repo
declares this plugin and inserts it into the "package" lifecycle:

```xml
<plugin>
	<groupId>org.springframework.boot.experimental</groupId>
	<artifactId>spring-boot-thin-maven-plugin</artifactId>
	<version>${wrapper.version}</version>
	<executions>
		<execution>
			<id>resolve</id>
			<goals>
				<goal>resolve</goal>
			</goals>
			<inherited>false</inherited>
		</execution>
	</executions>
</plugin>
```

After running the build, there is a deployable warm-cache and a copy
of the executable jar at `target/thin/root` (by default):

```
$ cd samples/app
$ mvn package
$ cd target/thin/root
$ java -Dthin.root=. -jar app-0.0.1-SNAPSHOT.jar
```

The "simple" sample has the same feature, but it also downloads and
warms up the cache for the "app" sample, so you could use the same
build to run both apps if you felt like it.

The Maven plugin also has a `properties` mojo, so you can create or update
`thin.properties` from the dependencies of the project directly. By default it creates a
`thin.properties` in `src/main/resources/META-INF`, but you can change the output
directory with the plugin configuration. Example:

```
$ cd samples/app
$ mvn spring-boot-thin:properties -Dthin.output=.
```

By default the `thin.properties` is "computed" (i.e. it contains all transitive
dependencies), but you can switch to just the declared dependencies using the "compute"
configuration flag in the plugin (or `-Dthin.compute=false` on the command line).

### Gradle

The same features are available to Gradle users by adding a plugin:

```groovy
buildscript {
    ...
    dependencies {
        classpath("org.springframework.boot.experimental:spring-boot-thin-gradle-plugin:${wrapperVersion}")
        ...
    }
}

...
apply plugin: 'org.springframework.boot.experimental.thin-launcher'

```

The plugin creates 2 tasks for every jar task in the project, one that
reolves the dependencies, and one that copies the jar into the same
location to make it easy to launch.  A "dry run" can be executed in
Gradle by calling the "thinResolve" task defined by the plugin, e.g.

```
$ cd samples/simple
$ gradle thinResolve
$ cd build/thin/deploy
$ java -Dthin.root=. -jar simple-0.0.1-SNAPSHOT.jar
```

The default location for the cache is `build/thin/root` but this was
changed in the `build.gradle` for that sample:

```
thinResolvePrepare {
	into new File("${buildDir}/thin/deploy")
}
```

> NOTE: The "thinResolve" and "thinResolvePrepare" tasks are the
> default names for a single jar project. If your jar task is not
> called "jar", then the names are appended with the jar task name
> (capitalized), e.g. "thinResolveMyJar" for a task called
> "myJar"). If you have multiple jar tasks in the project, then each
> one has its own resolve tasks.

## Deploying to Cloud Foundry (or Heroku)

The thin launcher (1.0.4 and above) adds an empty "lib" entry to the jar so that it matches the default detection algorithm for a Java application with the standard Java buildpack. As of version v4.12 of the Java buildpack the dependencies will be computed during staging (in the "compile" step of the buildpack), so you don't incur that cost on startup.

You can also save the staging cost, and resolve the dependencies locally before you push the app.

```
$ java -jar target/demo-0.0.1.jar --thin.dryrun --thin.root=target/thin/.m2
$ (cd target/thin; jar -xf ../demo-0.0.1,jar)
$ cf push myapp -p target/thin
```

(Note the use of a subdirectory `.m2` to hold the local repository cache - this works because the root is the default `HOME` directory in a Cloud Foundry app.)

The Maven plugin has a "resolve" task with a flag unpack (or `-Dthin.unpack` on the command line) that creates the cache in the precise form that you need to push to Cloud Foundry. The unpack flag is false by default, so remember to set it if you want to use Maven to prepare the push.


## Command Line Options

You can set a variety of options on the command line or with system properties (`-D...`). The `thin.*` properties are all removed from the command line before calling the main class, so the main class doesn't have to know how it was launched.

| Option | Default | Description |
|--------|---------|-------------|
| `thin.main` | Start-Class in MANIFEST.MF| The main class to launch (for a Spring Boot app, usually the one with `@SpringBootApplication`)|
| `thin.dryrun` | false | Only resolve and download the dependencies. Don't run any main class. N.B. any value other than "false" (even empty) is true. |
| `thin.offline` | false | Switch to "offline" mode. All dependencies must be available locally (e.g. via a previous dry run) or there will be an exception. |
| `thin.force` | false | Force dependency resolution to happen, even if dependencies have been computed, and marked as "computed" in `thin.properties`. |
| `thin.classpath` | false | Only print the classpath. Don't run the main class. Two formats are supported: "path" and "properties". For backwards compatibility "true" or empty are equivalent to "path". |
| `thin.root` | `${user.home}/.m2` | The location of the local jar cache, laid out as a maven repository. The launcher creates a new directory here called "repository" if it doesn't exist. |
| `thin.archive` | the same as the target archive | The archive to launch. Can be used to launch a JAR file that was build with a different version of the thin launcher, for instance, or a fat jar built by Spring Boot without the thin launcher. |
| `thin.parent` | `<empty>` | A parent archive to use for dependency management and common classpath entries. If you run two apps with the same parent, they will have a classpath that is the same, reading from left to right, until they actually differ. |
| `thin.location` | `file:.,classpath:/` | The path to directory containing thin properties files (as per `thin.name`), as a comma-separated list of resource locations (directories). These locations plus the same paths relative /META-INF will be searched. |
| `thin.name` | "thin" | The name of the properties file to search for dependency specifications and overrides. |
| `thin.profile` |<empty> | Comma-separated list of profiles to use to locate thin properties. E.g. if `thin.profile=foo` the launcher searches for files called `thin.properties` and `thin-foo.properties`. |
| `thin.library` | `org.springframework.boot.experimental:spring-boot-thin-launcher:1.0.21.RELEASE` | A locator for the launcher library. Can be Maven coordinates (with optional `maven://` prefix), or a file (with optional `file://` prefix). |
| `thin.repo`    | `https://repo.spring.io/libs-snapshot` (also contains GA releases) | Base URL for the `thin.library` if it is in Maven form (the default). |
| `thin.launcher` | `org.springframework.boot.thin.ThinJarLauncher` | The main class in the `thin.library`. If not specified it is discovered from the manifest `Main-Class` attribute. |
| `thin.parent.first` | true | Flag to say that the class loader is "parent first" (i.e. the system class loader will be used as the default). This is the "standard" JDK class loader strategy. Setting it to false is similar to what is normally used in web containers and application servers. |
| `thin.parent.boot` | true | Flag to say that the parent class loader should be the boot class loader not the "system" class loader. The boot loader normally includes the JDK classes, but not the target archive, nor any agent jars added on the command line. |
| `thin.debug` | false | Flag to switch on some slightly verbose logging during the dependency resolution. Can also be switched on with `debug` (like in Spring Boot).|
| `thin.trace` | false | Super verbose logging of all activity during the dependency resolution and launch process. Can also be switched on with `trace`.|

Any other `thin.properties.*` properties are used by the launcher to override or supplement the ones from `thin.properties`, so you can add additional individual dependencies on the command line using `thin.properties.dependencies.*` (for instance).

## Downstream Tools

The default behaviour of the `ThinJarWrapper` is to locate and launch the `ThinJarLauncher`, but it can also run any main class you like by using the `thin.library` and `thin.launcher` properties. One of the main reasons to provide this feature is to be able to support "tools" that process the application jar (or whatever), for example to generate metadata, create file system layers, etc. To create a new tool, make an executable jar (it can even be thin) with a `Main-Class` in its manifest, and point to it with `thin.library`. The launched main class will find the same command line as the launched jar, but with `--thin.library` removed if it was there. It will also find a system property `thin.source` containing the location of the launched jar, or the original `thin.archive` if that was provided on the command line (this is the archive that contains the data to process normally). If the tool jar is thin, i.e. if the main class is `ThinJarWrapper`, then the `thin.archive` command line argument and system property will also be removed (to prevent an infinite loop, where the wrapper just runs itself over and over).

An example of a tool jar is the `spring-boot-thin-tools-converter` (see below). You could use that as a prototype if you wanted to create your own.

## HOWTO Guides

### How to Externalize the Properties File

Example command line showing to pick up an external properties file:

```
$ cat config/thin.properties
dependencies.spring-boot-starter-web: org.springframework.boot:spring-boot-starter-web
$ java -jar app.jar --thin.location=file:./config
```

### How to Create a Docker File System Layer

Precompute the dependencies:

```
$ java -jar app.jar --thin.root=m2 --thin.dryrun
$ java -jar app.jar --thin.classpath=properties > thin.properties
```

Then build a docker image using a `Dockerfile` based on this:

```
FROM openjdk:8-jdk-alpine
VOLUME /tmp

ADD m2 m2
ADD app.jar app.jar
ADD thin.properties thin.properties

ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar app.jar --thin.root=/m2" ]

EXPOSE 8080
```

The step to add a `thin.properties` is optional, as is its calculation (you could maintain a hand-written properties file inside the JAR as well).


## How to Change Dependencies

You can change the runtime dependencies, by changing the
`thin.properties` in the jar. You can also read a local
`thin.properties` from the current working directory, or set a System
property `thin.name` to change the local file name (defaults to
`thin`). There is also a `thin.profile` (comma separated list) which
is appended to `thin.name`, so additional libraries can be added using
`thin-{profile}.properties`. Profile-specific properties are loaded
last so they take precedence. Example to pick up an extra set of
dependencies in `thin-rabbit.properties`:

```
$ java -jar myapp.jar --thin.profile=rabbit
```

Profile-specific `thin.properties` can be saved in the jar file
(conventionally in `META-INF`), or in the current working directory by
default.

NOTE: You can add or override `thin.properties` entries on the command
line or with System properties using key names in `thin.properties.*`
(the prefix `thin.properties.` is stripped).

## How to Upgrade Spring Boot or Spring Cloud

If your main pom (or properties) file uses boms to manage dependency
versions, you can change the version of the bom using
`thin.properties`. E.g.

```
boms.spring-boot-dependencies=org.springframework.boot:spring-boot-dependencies:1.5.6.RELEASE
...
```

If your main pom uses properties to manage dependencies (e.g. via the
Spring Boot starter parent), you can change the value of the property
using `thin.properties`. E.g.

```
spring-boot.version=1.5.6.RELEASE
spring-cloud.version=Dalston.SR3
```

where the pom has

```
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>${spring-cloud.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## How to Exclude a Transitive Dependency

You can exclude and remove dependencies
by prepending a key in the properties file with `exclusions.`. E.g.

```
dependencies.spring-boot-starter-web=org.springframework.boot:spring-boot-starter-web
dependencies.spring-boot-starter-jetty=org.springframework.boot:spring-boot-starter-jetty
exclusions.spring-cloud-starter-tomcat=org.springframework.boot:spring-cloud-starter-tomcat
```

## How to Convert a Thin Jar to a Fat Jar

There is a converter tool that you can use as a library in place of the launcher. It works by copying all of the libraries from a `thin.root` into the new jar. Example:

```
$ java -jar myapp.jar --thin.dryrun --thin.root=target/thin/root
$ java -jar myapp.jar --thin.library=org.springframework.boot.experimental:spring-boot-thin-tools-converter:1.0.21.RELEASE
$ java -jar myapp-exec.jar
```

## Building

To build this project locally, use the maven wrapper in the top level

```
$ ./mvnw clean install
```

Then run the "app" jar:

```
$ java -jar ./app/target/*.jar
```

(It starts an empty Spring Boot app with Tomcat.)

You can also build the samples independently.


## Classpath Computation

The launcher has some optional arguments that result in classpath
computations, instead of running the Boot app. E.g.

```
$ java -jar myapp.jar --thin.classpath=path
```

prints out (on stdout) a class path in the form that can be used
directly in `java -cp`. So this is a way to run the app from its main
method (which is faster than using the launcher):

```
$ CLASSPATH=`java -jar myapp.jar --thin.classpath=path`
$ java -cp "$CLASSPATH:myapp.jar" demo.MyApplication
```

You can also compute the classpath using explicit name and profile parameters:

```
$ java -jar myapp.jar --thin.classpath=path --thin.name=app --thin.profile=dev
```

will look for `app.properties` and `app-dev.properties` to list the dependencies.

You can also specify a "parent" archive which is used to calculate a
prefix for the classpath. Two apps that share a parent then have the
same prefix, and can share classes using `-Xshare:on`. For example:

```
$ CP1=`java -jar myapp.jar --thin.classpath=path`
$ CP2=`java -jar otherapp.jar --thin.classpath=path --thin.parent=myapp.jar`

$ java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:off \
  -XX:DumpLoadedClassList=app.classlist \
  -noverify -cp $CP1:myapp.jar demo.MyApplication
$ java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:dump \
  -XX:SharedArchiveFile=app.jsa -XX:SharedClassListFile=app.classlist \
  -noverify -cp $CP1

$ java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on \
  -XX:SharedArchiveFile=app.jsa -noverify -cp $CP1:myapp.jar demo.MyApplication 
$ java -XX:+UnlockCommercialFeatures -XX:+UseAppCDS -Xshare:on \
  -XX:SharedArchiveFile=app.jsa -noverify -cp $CP1:otherapp.jar demo.OtherApplication
```

the two apps at the end are sharing class data from `app.jsa` and will
also start up faster (e.g. 6s startup goes down to 4s for
a vanilla Eureka Server).

The thin launcher can be used to pre-compute its own dependency graph in the form of a
properties file, which also speeds up the launch a bit, even if you still have to resolve
all the jars (remotely or from the cache). To compute the dependency graph and output the
result in the form of a properties file, just use the `thin.classpath=properties` flag on
startup, e.g.

```
$ java -jar myapp.jar --thin.classpath=properties > thin.properties
$ java -jar myapp.jar
```

In this example the second startup will be slightly faster, depending
on the size of the classpath, but up to a few hundred milliseconds on
afast server, and more in a constrained environment.

It also works fine with profiles, so, for example, if `myapp.jar`
contains a `META-INF/thin-rapid.properties` you could do this:

```
$ java -jar myapp.jar --thin.profile=rapid --thin.compute > thin-super.properties
$ java -jar myapp.jar --thin.profile=super
```

Note that the generated `thin.properties` in these examples contains the property value
`computed=true`. This tells the dependency graph calculator that the dependencies provided
do not need to have their transitive dependencies or versions computed. It is possible to
combine more than one properties file if they have different values of the `computed`
flag, but if they both also contain dependencies then only the computed ones will be
used. Note that this means you can compute a profile using `--thin.classpath=properties`
and use it as a cache, speeding up startup without affecting any other settings that might
be in other `thin.properties`.

## How to Change the Maven Local Repository

You can change the location of the local Maven repository, used to
resolve and cache artifacts, using the standard Maven `settings.xml`
file (with a top level element called `<localRepository/>`). You can
also use a system property `maven.repo.local` (or `maven.home` which
defaults to `${user.home}/.m2`) when you launch the thin jar, but not
a command line flag. The Maven plugin responds to the `settings.xml`
and also to `-Dmaven.repo.local` as a Maven command line flag.

## How to Configure a Proxy

The dependency resolution uses Maven libraries, and should respect the
proxy settings in your `settings.xml`. The initial download of the
launcher by the `ThinJarWrapper` uses regular JDK libraries so you
need to specify the normal `-D`
[args for networking as well](https://docs.oracle.com/javase/8/docs/api/java/net/doc-files/net-properties.html),
unless you have the launcher already cached locally.

## Using a Mirror for Maven Central

The thin launcher itself parses your local Maven `settings.xml` and
uses the mirror settings there. To download the launcher itself, and
bootstrap the process, you need to explicitly provide a `thin.repo` to
the wrapper (the same as the mirror). You can do this on the command
line when running the jar, using all the usual mechanisms. To run the
build plugins `resolve` goals you can make the thin launcher jar 
a dependency of the plugin, to ensure it is cached locally before the
plugin runs. E.g.

```
<pluginManagement>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot.experimental</groupId>
            <artifactId>spring-boot-thin-maven-plugin</artifactId>
            <version>${wrapper.version}</version>
            <dependencies>
                <dependency>
                    <groupId>org.springframework.boot.experimental</groupId>
                    <artifactId>spring-boot-thin-launcher</artifactId>
                    <classifier>exec</classifier>
                    <version>${wrapper.version}</version>
                </dependency>
            </dependencies>
        </plugin>
    </plugins>
</pluginManagement>
```

Or else you can set a project, system property or environment variable. E.g.

```
$ ./mvnw spring-boot-thin:resolve -Dthin.repo=http://localhost:8081/repository/maven-central
```

or 

```
$ ./gradlew thinResolve -P thin.repo=http://localhost:8081/repository/maven-central
```

System properties (`thin.repo`) and environment variables (`THIN_REPO`) work too.

## License
This project is Open Source software released under the
[Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

