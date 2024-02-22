def call(dataURL, signatureURL, keyServer, keyID) {
    funcs.downloadURLAndGPGSignature(dataURL, signatureURL)
    def signatureBase = funcs.basename(signatureURL)
    sh """
    GNUPGHOME=`mktemp -d /tmp/.gpg-tmp-XXXXXXX`
    export GNUPGHOME
    eval \$(gpg-agent --homedir "\$GNUPGHOME" --daemon)
    trap 'rm -rf "\$GNUPGHOME"' EXIT
    gpg2 --verbose --homedir "\$GNUPGHOME" --keyserver ${keyServer} --recv ${keyID}
    gpg2 --verbose --homedir "\$GNUPGHOME" --verify ${signatureBase}
    """
}