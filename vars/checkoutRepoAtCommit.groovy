def call(Map config) {
	// repo
	// commit
	// outdir is the directory where the repo will be checked out.
	dir(config.outdir) {
		checkout(
			[
				$class: 'GitSCM',
				branches: [[name: config.commit]],
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
				userRemoteConfigs: [[url: config.repo]]
			]
		)
	}
}
