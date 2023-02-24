def call(String repo, String commit, String outdir) {
	// outdir is the directory where the repo will be checked out.
	dir(outdir) {
		checkout(
			[
				$class: 'GitSCM',
				branches: [[name: commit]],
				extensions: [
					[$class: 'CleanBeforeCheckout'],
					[
						$class: 'SubmoduleOption',
						disableSubmodules: false,
						parentCredentials: false,
						recursiveSubmodules: true,
						trackingSubmodules: false
					],
				],
				submoduleCfg: [],
				userRemoteConfigs: [[url: repo]]
			]
		)
	}
}
