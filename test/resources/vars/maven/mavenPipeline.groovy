library('jenkins-library')

node {
    maven.getProjectName()
    maven.getProjectName('pom.xml')
    maven.getProjectName('pom.xml', "mvn")
    maven.getProjectName('pom.xml', "mvn", 'M3', 'JDK8')

    maven.getProjectVersion()
    maven.getProjectVersion('pom.xml')
    maven.getProjectVersion('pom.xml', "mvn")
    maven.getProjectVersion('pom.xml', "mvn", 'M3', 'JDK8')

    maven.getInfo("project.url")
    maven.getInfo(infoExpression: "project.url")
    maven.getInfo("project.url", 'pom.xml')
    maven.getInfo(infoExpression: "project.url", path: 'pom.xml')
    maven.getInfo("project.url", 'pom.xml', "mvn")
    maven.getInfo(infoExpression: "project.url", path: 'pom.xml', mvnExec: "mvn")
    maven.getInfo("project.url", 'pom.xml', "mvn", 'M3', 'JDK8')
    maven.getInfo(infoExpression: "project.url", path: 'pom.xml', mavenTool: "M3")
    maven.getInfo(infoExpression: "project.url", path: 'pom.xml', mavenTool: "M3", javaTool: 'JDK8')
}
