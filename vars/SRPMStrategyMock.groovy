// This strategy calls make srpm in the local directory,
// expecting an SRPM to be built in the local directory.
def call(Map config) {
    return {
		def specfile = funcs.findSpecfile()
		def buildslave_release = funcs.slaveRelease()
		def buildslave_machine = funcs.slaveArch()
		mock(
			"Fedora",
			buildslave_release,
			buildslave_machine,
			[
				"--buildsrpm",
				"--spec",
				specfile,
				"--sources",
				"./",
				"--resultdir=./"
			]
		)
    }
}
