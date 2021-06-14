The Spring Boot Thin Gradle Plugin can be built and published from here. To send a release to Maven Central:

```
$ ../mvnw deploy
```

To sync with Gradle Plugin Registry you also need to publish the plugin with Gradle:

```
$ cd publish
$ export GRADLE_PUBLISH_VERSION=<version>
$ export GRADLE_PUBLISH_SECRET=...
$ export GRADLE_PUBLISH_KEY=...
$ ./gradlew publishExisting
```

where `<version>` is the current (RELEASE) version, and `<secret>` and `<key>` can be found in the Gradle Plugin Registry UI.
