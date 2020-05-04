def call(String s) {
    // """Return a shell-escaped version of the string *s*."""
    if (s == "") {
        return "''"
    }
    def p = ~'[^\\w@%+=:,./-]'
    if ((s =~ p).find() != true) {
        return s
    }
    return "'" + s.replace("'", "'\"'\"'") + "'"
}
