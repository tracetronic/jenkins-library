/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */
package vars

import groovy.testSupport.PipelineSpockTestBase
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import spock.lang.Shared

import java.time.Duration

class MavenPipelineIT extends PipelineSpockTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenPipelineIT.class)

    private static final String DOCKER_ROOT = 'vars/maven/docker'

    @Shared
    GenericContainer jenkinsContainer

    def setupSpec() {
        jenkinsContainer = new GenericContainer<>(
                new ImageFromDockerfile()
                        .withFileFromClasspath('Dockerfile', "${DOCKER_ROOT}/Dockerfile")
                        .withFileFromClasspath('plugins.txt', "${DOCKER_ROOT}/plugins.txt")
                        .withFileFromPath('src', new File('src').toPath())
                        .withFileFromPath('vars', new File('vars').toPath())
                        .withFileFromPath('resources', new File('resources').toPath())
                        .withFileFromPath('.git', new File('.git').toPath()))
                .withClasspathResourceMapping("${DOCKER_ROOT}/casc", '/usr/share/jenkins/ref/casc', BindMode.READ_ONLY)
                .withLogConsumer(new Slf4jLogConsumer(LOGGER))
                .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5)))
    }

    def cleanup() {
        jenkinsContainer.stop()
    }

    def 'Test Maven container pipeline'() {
        when:
        jenkinsContainer.withClasspathResourceMapping(
                "${DOCKER_ROOT}/pom.xml", '/tests/maven/pom.xml', BindMode.READ_ONLY)
        jenkinsContainer.withClasspathResourceMapping(
                "${DOCKER_ROOT}/Jenkinsfile", '/workspace/Jenkinsfile', BindMode.READ_ONLY)
        jenkinsContainer.start()
        then:
        jenkinsContainer.getLogs().contains("Finished: SUCCESS")
    }
}
