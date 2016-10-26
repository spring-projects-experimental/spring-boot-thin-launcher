Prototype "thin" jar launcher for java apps. See https://github.com/spring-projects/spring-boot/issues/1813 for more discussion and ideas.

TODO:

* [X] Support the wrapper as a layout in Spring Boot build plugins(*)
* [X] Extract `AetherEngine` and re-use it in Spring Boot CLI(*)
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
* [ ] Fix dry run in Spring Boot PR

(*) Implemented in a pull request to Spring Boot, not in this
project: https://github.com/spring-projects/spring-boot/issues/1813.

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
runs the application's own main method with that class loader.

The app jar in the demo is build using the Maven assembly plugin. You
could copy paste the configuration and it would probably work with
your app. It would be nice to be able to support it from Spring Boot
as a build plugin option.

## Caching

All jar files are cached in the local Maven repository, so if you are
building and running the same app repeatedly, it should be faster
after the first time, or if the local repo is already warm.

The local repository can be re-located by setting a System property "main.root". For example to use the current directory:

```
$ java -Dmain.root=. -jar app/target/*.jar
```

This will download all the dependencies to `${main.root}/repository`,
and look for Maven settings in `${main.root}/settings.xml`.

You can also do a "dry run", just to warm up the cache and not run the
app, by setting a System property "main.dryrun" (to any value). In
fact, since you don't need the application code for this (except the
`META-INF/lib.properties`), you could run only the launcher, or the
wrapper, which might be a useful trick for laying down a file system
layer in a container image, for example.


## Upgrades

You can upgrade all the libraries by changing the `lib.properties`.
