def call() {
    dir('src') {
        sh '''
            pkg=$(echo "$JOB_NAME" | sed 's/^perl-//' | sed 's|/.+||' | sed 's/-/::/g')
            cpanspec -v -r $BUILD_NUMBER -s --packager "Generic RPM builder" "$pkg"
        '''
    }
}
