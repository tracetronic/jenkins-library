FROM maven:3.9-eclipse-temurin-11 as maven

FROM jenkins/jenkinsfile-runner:adoptopenjdk-11-hotspot

# Setup Maven
COPY --from=maven /usr/share/maven/ /usr/share/maven/
COPY --from=maven /usr/local/bin/mvn-entrypoint.sh /usr/local/bin/mvn-entrypoint.sh
RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG /root/.m2

# Copy shared library
COPY src /opt/git/jenkins-library/src
COPY vars /opt/git/jenkins-library/vars
COPY resources /opt/git/jenkins-library/resources
COPY .git /opt/git/jenkins-library/.git

# Allow local checkouts
ENV JAVA_OPTS="-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true"

# Pre-install plugins
#COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN cd /app/jenkins && jar -cvf jenkins.war *
#RUN java -jar /app/bin/jenkins-plugin-manager.jar --war /app/jenkins/jenkins.war --plugin-file /usr/share/jenkins/ref/plugins.txt && rm /app/jenkins/jenkins.war
