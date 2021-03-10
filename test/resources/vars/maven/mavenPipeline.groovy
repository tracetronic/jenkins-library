library('jenkins-library')

node {
    maven.getProjectName()
    maven.getProjectName('pom.xml')
    maven.getProjectName('pom.xml', 'M3', 'JDK8')

    maven.getProjectVersion()
    maven.getProjectVersion('pom.xml')
    maven.getProjectVersion('pom.xml', 'M3', 'JDK8')

    maven.getInfo("project.url")
    maven.getInfo("project.url", 'pom.xml')
    maven.getInfo("project.url", 'pom.xml', 'M3', 'JDK8')
}
