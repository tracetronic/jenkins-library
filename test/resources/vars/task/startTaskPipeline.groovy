library('jenkins-library')

node {
    task.start('test.exe')
    task.start('c:\\test.exe', ['arg1', 'arg2'], 'c:\\test', true)
}
