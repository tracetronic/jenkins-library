library('jenkins-library')

node {
    task filePath: 'test.exe'
    task filePath: 'c:\\test.exe', args: ['arg1', 'arg2'], startDir: 'c:\\test', wait: true, restart: true
    task('c:\\test.exe', ['arg1', 'arg2'], 'c:\\test', true, true)
}
