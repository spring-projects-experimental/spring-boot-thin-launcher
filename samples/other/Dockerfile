FROM openjdk:8-jdk-alpine

ADD target/thin/root/repository m2/repository
ADD target/other-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar app.jar --thin.root=/m2" ]

EXPOSE 8080
