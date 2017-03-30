A "thin" jar launcher for java apps. See https://github.com/spring-projects/spring-boot/issues/1813 for more discussion and ideas.

## Getting Started

The thin-launcher provides its own custom layout for the Spring Boot
plugins. If this layout is used then the jar built by Spring Boot will
be executable and thin.

Build a Spring Boot application and add the layout. In Maven this
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

and in Gradle

```groovy
buildscript {
	ext {
		springBootVersion = '1.5.2.RELEASE'
		wrapperVersion = '1.0.0.BUILD-SNAPSHOT'
	}
	repositories {
		mavenLocal()
		mavenCentral()
	}
	dependencies {
		classpath("org.springframework.boot.experimental:spring-boot-thin-gradle-layout:${wrapperVersion}")
		classpath("org.springframework.boot:spring-boot-gradle-plugin:${springBootVersion}")
	}
}
```

In Gradle you also need to generate a `pom.xml` (unless you want to maintain it by hand). You can do that with the "maven" plugin, for example:

```groovy
apply plugin: 'maven'

task createPom {
	doLast {
		pom {
			withXml(dependencyManagement.pomConfigurer)
		}.writeTo("build/resources/main/META-INF/maven/${project.group}/${project.name}/pom.xml")
	}
}
group = 'com.example'
version = '0.0.1-SNAPSHOT'
jar.dependsOn = [createPom]
```

The generated pom can go in the root of the jar, or in the normal maven place (which is what is configured in the sample above).

If you look at the jar file produced by the build you will see that it
is "thin" (a few KB), but executable with `java -jar ...`.


## How does it Work?

Inspect the app jar that you built (or one of the samples in this
project) and notice that it is only a few KB. It is just a regular jar
file with the app classes in it and two extra features:

1. The `ThinJarWrapper` class has been added.
2. Either a `pom.xml` and/or a `META-INF/thin.properties` which lists the dependencies of the app.

When the app runs the main method per the manifest is the
`ThinJarWrapper`. Its job is to download another jar file (the
"launcher"). The wrapper downloads the launcher (if it needs to), or
else uses the cached version in your local Maven repository.

The launcher then takes over and reads the `pom.xml` (if present) and
the `thin.properties`, downloading the dependencies (and all
transitives) as necessary, and setting up a new class loader with them
all on the classpath. It then runs the application's own main method
with that class loader. The `pom.xml` can be in the root of the jar or
in the standard `META-INF/maven` location.

The app jar in the demo is build using the Spring Boot plugin and a
custom `Layout` (so it only builds with Spring Boot 1.5.x),

## Caching

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
> `ThinJarWrapper` accepts system properties or environment variables
> for its (smaller) set of optional arguments.

## Upgrades and Profiles

You can upgrade all the libraries by changing the
`thin.properties`. You can also read a local `thin.properties` from
the current working directory, or set a System property `thin.name` to
change the local file name (defaults to `thin`). There is also a
`thin.profile` (comma separated list) which is appended to
`thin.name`, so additional libraries can be added using
`thin-{profile}.properties`. Profile-specific properties are loaded
last so they take precedence. You can exclude and remove dependencies
by prepending a key in the properties file with `exclusions.`.

NOTE: You can add or override `thin.properties` entries on the command
line or with System properties using key names in `thin.properties.*`
(the prefix `thin.properties.` is stripped).

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

After running the build, there is a deployable warm-cache at
`target/thin/root` (by default):

```
$ cd samples/app
$ mvn package
$ cd target/thin/root
$ java -Dthin.root=. -jar app-0.0.1-SNAPSHOT.jar
```

The "simple" sample has the same feature, but it also downloads and
warms up the cache for the "app" sample, so you could use the same
build to run both apps if you felt like it.

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

A "dry run" can be executed in Gradle by calling the "thinResolve"
task defined by the plugin, e.g.

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

## Command Line Options

You can set a variety of options on the command line with system properties (`-D...`). The `thin.*` properties are all removed from the command line before calling the main class, so the main class doesn't have to know how it was launched.

| Option | Default | Description |
|--------|---------|-------------|
| `thin.main` | Start-Class in MANIFEST.MF| The main class to launch (for a Spring Boot app, usually the one with `@SpringBootApplication`)|
| `thin.dryrun` | false | Only resolve and download the dependencies. Don't run any main class. N.B. any value other than "false" (even empty) is true. |
| `thin.classpath` | false | Only print the classpath. Don't run and main class.  N.B. any value other than "false" (even empty) is true. |
| `thin.root | `${user.home}/.m2/repository` | The location of the local jar cache, laid out as a maven repository. |
| `thin.archive` | the same as the target archive | The archive to launch. Can be used to launch a JAR file that was build with a different version of the thin launcher, for instance, or a fat jar built by Spring Boot without the thin launcher. |
| `thin.parent` | `<empty>` | A parent archive to use for dependency management and common classpath entries. If you run two apps with the same parent, they will have a classpath that is the same, reading from left to right, until they actually differ. |
| `thin.location` | `file:.,classpath:/` | The path to thin properties files (as per `thin.name`), as a comma-separated list of resource locations (directories). These locations plus relative /META-INF will be searched. |
| `thin.name` | "thin" | The name of the properties file to search for dependency specifications and overrides. |
| `thin.profile` |<empty> | Comma-separated list of profiles to use to locate thin properties. E.g. if `thin.profile=foo` the launcher searches for files called `thin.properties` and `thin-foo.properties`. |
| `thin.parent.first` | true | Flag to say that the class loader is "parent first" (i.e. the system class loader will be used as the default). This is the "standard" JDK class loader strategy. Setting it to false is similar to what is normally used in web containers and application servers. |
| `thin.parent.boot` | true | Flag to say that the parent class loader should be the boot class loader not the "system" class loader. The boot loader normally includes the JDK classes, but not the target archive, nor any agent jars added on the command line. |
| `debug` | false | Flag to switch on some slightly verbose logging during the dependency resolution. |
| `trace` | false | Super verbose logging of all activity during the dependency resolution and launch process. |

Any other `thin.*` properties are used by the launcher to override or supplement the ones from `thin.properties`, so you can add additional individual dependencies on the command line using `thin.dependencies.*`.

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
$ java -jar myapp.jar --thin.classpath
```

prints out (on stdout) a class path in the form that can be used
directly in `java -cp`. So this is a way to run the app from its main
method (which iis slightly faster than using the launcher):

```
$ CLASSPATH=`java -jar myapp.jar --thin.classpath`
$ java -cp "$CLASSPATH:myapp.jar" demo.MyApplication
```

You can also compute the classpath using explicit name and profile parameters:

```
$ java -jar myapp.jar --thin.classpath --thin.name=app --thin.profile=dev
```

will look for `app.properties` and `app-dev.properties` to list the dependencies.

You can also specify a "parent" archive which is used to calculate a
prefix for the classpath. Two apps that share a parent then have the
same prefix, and can share classes using `-Xshare:on`. For example:

```
$ CP1=`java -jar myapp.jar --thin.classpath`
$ CP2=`java -jar otherapp.jar --thin.classpath --thin.parent=myapp.jar`

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

## License
This project is Open Source software released under the
http://www.apache.org/licenses/LICENSE-2.0.html[Apache 2.0 license].

