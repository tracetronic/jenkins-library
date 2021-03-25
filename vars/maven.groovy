/*
 * Copyright (c) 2021 TraceTronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Reads specific information from a Maven project file (pom.xml).
 *
 * Allows to refer to a Maven installation configured in Jenkins (Global Tool Configuration).
 *
 * @param infoExpression
 *      the expression describing which information should be parsed from POM
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param mvnExec
 *      the absolute or relative file path to the Maven executable (defaults to mvn)
 * @param mavenTool
 *      the name of the Maven installation configured in Jenkins (optional)
 * @param jdkTool
 *      the name of the JDK installation configured in Jenkins (optional)
 * @return the evaluated information
 */
def getInfo(String infoExpression, String path = "pom.xml", String mvnExec = "mvn", String mavenTool = null, String jdkTool = null) {
    if (mavenTool && jdkTool) {
        return executeWithTools(
                mavenTool, jdkTool, "help:evaluate", path, "-Dexpression=${infoExpression} -q -DforceStdout")
    } else {
        return execute("help:evaluate", mvnExec, path, "-Dexpression=${infoExpression} -q -DforceStdout")
    }
}

/**
 * Reads the project name from a Maven project file (pom.xml).
 *
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param mvnExec
 *      the absolute or relative file path to the Maven executable (defaults to mvn)
 * @param mavenTool
 *      the name of the Maven installation configured in Jenkins (optional)
 * @param jdkTool
 *      the name of the JDK installation configured in Jenkins (optional)
 * @return the evaluated information
 */
def getProjectName(String path = "pom.xml", String mvnExec = "mvn", String mavenTool = null, String jdkTool = null) {
    return getInfo("project.name", mvnExec, path, mavenTool, jdkTool)
}

/**
 * Reads the project version from a Maven project file (pom.xml).
 *
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param mvnExec
 *      the absolute or relative file path to the Maven executable (defaults to mvn)
 * @param mavenTool
 *      the name of the Maven installation configured in Jenkins (optional)
 * @param jdkTool
 *      the name of the JDK installation configured in Jenkins (optional)
 * @return the evaluated information
 */
def getProjectVersion(String path = "pom.xml", String mvnExec = "mvn", String mavenTool = null, String jdkTool = null) {
    return getInfo("project.version", mvnExec, path, mavenTool, jdkTool)
}

/**
 * Executes a specific goal in a Maven project.
 *
 * @param goal
 *      the Maven goal to be executed
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param mvnExec
 *      the absolute or relative file path to the Maven executable (defaults to mvn)
 * @param args
 *      the additional Maven arguments
 * @return the command output
 */
def execute(String goal, String path = "pom.xml", String mvnExec = "mvn", String args = "") {
    return cmd("${mvnExec} -f ${path} ${goal} ${args}")
}

/**
 * Executes a specific Maven goal using a Maven installation configured in Jenkins (Global Tool Configuration).
 *
 * @param mavenTool
 *      the name of the Maven installation configured in Jenkins
 * @param jdkTool
 *      the name of the JDK installation configured in Jenkins
 * @param goal
 *      the Maven goal to be executed
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param args
 *      the additional Maven arguments
 * @return the command output
 */
def executeWithTools(String mavenTool, String jdkTool, String goal, String path = "pom.xml", String args = "") {
    def mvnHome = tool name: mavenTool
    def javaHome = tool name: jdkTool
    withEnv(["JAVA_HOME=${javaHome}", "PATH+MAVEN=${mvnHome}/bin:${env.JAVA_HOME}/bin"]) {
        return cmd("${mvnHome}/bin/mvn -f ${path} ${goal} ${args} -B")
    }
}
