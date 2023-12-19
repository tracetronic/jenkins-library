/*
 * Copyright (c) 2021 - 2023 tracetronic GmbH
 *
 * SPDX-License-Identifier: MIT
 */

/**
 * Reads specific information from a Maven project file (pom.xml).
 * @param params the parameter map
 * @return the evaluated information
 */
def getInfo(Map params = [:]) {
    String infoExpression = params.get('infoExpression')
    if (!infoExpression) {
        error("The info expression is required!")
        return
    }
    String goal = 'help:evaluate'
    String args = params.get('args')
    if (args) {
        args += " -Dexpression=${infoExpression} -q -DforceStdout"
    } else {
        args = "-Dexpression=${infoExpression} -q -DforceStdout"
    }
    params.put('goal', goal)
    params.put('args', args)

    return execute(params)
}

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
        return execute("help:evaluate", path, mvnExec, "-Dexpression=${infoExpression} -q -DforceStdout")
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
    return getInfo("project.name", path, mvnExec, mavenTool, jdkTool)
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
    return getInfo("project.version", path, mvnExec, mavenTool, jdkTool)
}

/**
 * Executes a specific goal in a Maven project.
 * @param params the parameter map
 * @return the command output
 */
def execute(Map params = [:]) {
    if (params.containsKey('mvnExec') && params.containsKey('mavenTool')) {
        error('mvnExec and mavenTool passed - please choose one definition!')
        return
    }
    String goal = params.get('goal')
    if (!goal) {
        error("The maven goal to execute is required!")
        return
    }

    String path = params.get('path', 'pom.xml')
    String args = params.get('args', '')

    if (!params.containsKey('mvnExec') && !params.containsKey('mavenTool')) {
        return execute(goal, path, "mvn", args)
    }
    if (params.containsKey("mvnExec")) {
        return execute(goal, path, params.get('mvnExec'), args)
    }
    if (params.containsKey('mavenTool')) {
        if (params.containsKey('javaTool')) {
            return executeWithTools(params.get('mavenTool'), params.get('javaTool'), goal, path, args)
        } else {
            return executeWithTool(params.get('mavenTool'), goal, path, args)
        }
    }
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
 * Executes a specific Maven goal using a Maven and Java installation configured in Jenkins (Global Tool Configuration).
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

/**
 * Executes a specific Maven goal using a Maven installation configured in Jenkins (Global Tool Configuration).
 *
 * @param mavenTool
 *      the name of the Maven installation configured in Jenkins
 * @param goal
 *      the Maven goal to be executed
 * @param path
 *      the absolute or relative file path to POM (defaults to pom.xml)
 * @param args
 *      the additional Maven arguments
 * @return the command output
 */
def executeWithTool(String mavenTool, String goal, String path = "pom.xml", String args = "") {
    if (!env.JAVA_HOME) {
        error('JAVA_HOME is not set and no Java tool installation is referenced.')
        return
    }
    def mvnHome = tool name: mavenTool
    withEnv(["PATH+MAVEN=${mvnHome}/bin:${env.JAVA_HOME}/bin"]) {
        return cmd("${mvnHome}/bin/mvn -f ${path} ${goal} ${args} -B")
    }
}
