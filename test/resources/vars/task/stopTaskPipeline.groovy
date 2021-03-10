library('jenkins-library')

node {
    task.stop(123)
    task.stop('test.exe')
    task.stop('c:\\test.exe')
}
