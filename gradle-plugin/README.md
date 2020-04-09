The Spring Boot Thin Gradle Plugin can be built and published from here. To send a release to Maven Central:

```
$ ../mvnw deploy
```

To send it to Bintray:

```
$ ../mvnw deploy -P bintray
```

To sync with Gradle Plugin Registry you also need to POST some JSON to Bintray

```
$ curl -H "Content-Type: application/json" -u <user>:<token> https://api.bintray.com/packages/spring/jars/spring-boot-thin/versions/<version>/attributes \
  -d '[{"name":"gradle-plugin","type":"string","values":["org.springframework.boot.experimental.thin-launcher:org.springframework.boot.experimental:spring-boot-thin-gradle-plugin"]}]'
```

where `<version>` is the current (RELEASE) version, and `<user>:<token>` can be found in the Bintray UI. You need the same username and token to do the `mvn deploy` anyway (in `settings.xml`).
