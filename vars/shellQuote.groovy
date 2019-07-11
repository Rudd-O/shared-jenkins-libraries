def call(string) {
    writeFile file: ".unquoted", text: string
    sh(
        script: '''#!/usr/bin/python3
import sys, shlex, os
sys.stdout.write(shlex.quote(open(".unquoted").read()))
os.unlink(".unquoted")
''',
        returnStdout: true
    )
    
}
