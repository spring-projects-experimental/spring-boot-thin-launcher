Prototype "thin" jar launcher for java apps. See https://github.com/spring-projects/spring-boot/issues/1813 for more discussion and ideas.

TODO:

* [ ] Support the wrapper as a layout in Spring Boot build plugins
* [ ] Deploy jars to snapshot repo at repo.spring.io
* [ ] Make it easy to override the dependencies at runtime (e.g. rolling upgrades of library jars for security patches)
* [ ] Add a "dry run" or "download only" feature so grab the dependencies and warm up the local cache, but not run the app
* [ ] Extract `AetherEngine` and re-use it in Spring Boot CLI
* [ ] Hone the dependencies in the launcher a bit (some optional stuff probably still there)
* [ ] Either autogenerate the `lib.properties` or find a way to model the pom without a lot of extra machinery

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

## Upgrades

You can upgrade all the libraries by changing the `lib.properties`.
