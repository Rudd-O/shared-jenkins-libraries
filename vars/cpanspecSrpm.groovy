def call() {
    dir('src') {
        sh '''
            pkg=$(basename "$PWD" | sed 's/^perl-//' | sed 's/-/::/g')
            cpanspec -v -r $BUILD_NUMBER -s --packager "Generic RPM builder" "$pkg"
        '''
    }
}
