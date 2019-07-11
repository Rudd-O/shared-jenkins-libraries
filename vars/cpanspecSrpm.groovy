def call() {
        sh '''
            pkg=$(basename "$PWD" | sed 's/^perl-//' | sed 's/-/::/g')
            cd src
            cpanspec -v -r $BUILD_NUMBER -s --packager "Generic RPM builder" "$pkg"
        '''
}
