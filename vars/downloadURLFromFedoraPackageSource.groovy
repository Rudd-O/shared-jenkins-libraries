def call(Map config) {
        // config.sha256sum validates the URL's contents.
        def specfile = null
        if (config != null) {
                specfile = config.specfile
        }
        if (specfile == null) {
                specfile = funcs.findSpecfile()
        }
        def url = funcs.getrpmsources(specfile)[0]
        def fn = funcs.basename(url)
        def sum = ""
        def prefix = "SHA512 ($fn) = "
        for (line in readFile("sources").split("\n")) {
                if (line.startsWith(prefix)) {
                        sum = line.minus(prefix)
                }
        }
        if (sum == "") {
                error("No sum for ${fn} found in file sources.")
        }
        funcs.downloadURLWithSHA512Checksum(url, sum)
}
