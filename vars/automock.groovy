def call(String distro, String myRelease) {
    def stuff = ""
    def release = ""
    def arch = ""
    if (myRelease.indexOf(":") != -1) {
            stuff = myRelease.split(':')
            release = stuff[0]
            arch = stuff[1]
    } else {
            release = myRelease
            arch = "x86_64"
    }
    ArrayList srpms = findFiles(glob: 'src/*.src.rpm').collect { it.getPath() }
    if (srpms.size() == 0) {
        throw new Exception("No source RPMs found")
    }
    dir("out/${release}") {
    }
    ArrayList args = [
        "--define=build_number ${BUILD_NUMBER}",
        "--resultdir=out/${release}",
        "--rebuild"
    ]
    mock(distro, release, arch, args, srpms)
}
