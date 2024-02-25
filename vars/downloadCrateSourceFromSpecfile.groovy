def call(Map config) {
        // config.sha256sum validates the URL's contents.
        script {
            def specfile = funcs.findSpecfile()
            def originalFilename = downloadURLFromSpecfile  sha256sum: config.sha256sum,
                                                            specfile: specfile
            def finalFilename = funcs.basename(funcs.getrpmfield(specfile, "Source"))
            fileOperations([fileRenameOperation(source: originalFilename, destination: finalFilename)])
        }
}
