def call(Map config) {
        // config.sha256sum validates the URL's contents.
        def specfile = config.specfile
        if (specfile == null) {
                specfile = funcs.findSpecfile()
        }
        def url = funcs.getrpmsources(specfile)[0]
        funcs.downloadURLWithSHA256Checksum(url, config.sha256sum, config.outfilename)
}
