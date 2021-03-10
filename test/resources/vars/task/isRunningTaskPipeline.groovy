library('jenkins-library')

node {
    task.isRunning('test')
    task.isRunning('test.exe')
}
