def mockShellLib() {
    return '''
function config_mocklock_fedora() {
    local outputfile="$1"
    local release="$2"
    local arch="$3"
    local basedir="$4"
    local root="$5"
    local cache_topdir="$6"
    local releasewithoutname=$(echo "$release" | sed 's/fc//')

    cat > "$outputfile" <<EOF
config_opts['print_main_output'] = True

config_opts['basedir'] = '$basedir'
config_opts['root'] = '$root'
config_opts['target_arch'] = '$arch'
config_opts['legal_host_arches'] = ('$arch',)
# rpmdevtools was installed to support rpmdev-bumpspec below
# python-setuptools was installed to allow for python builds
config_opts['chroot_setup_cmd'] = 'install @buildsys-build autoconf automake gettext-devel libtool git rpmdevtools python-setuptools python3-setuptools /usr/bin/python3 shadow-utils /bin/sh rpm'
config_opts['extra_chroot_dirs'] = ['/run/lock']
config_opts['isolation'] = 'simple'
config_opts['rpmbuild_networking'] = True
config_opts['use_host_resolv'] = False
config_opts['dist'] = '$release'  # only useful for --resultdir variable subst
config_opts['releasever'] = '$releasewithoutname'
config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
# generates problems in f37+
config_opts['plugin_conf']['nosync'] = False
config_opts['use_bootstrap'] = False
config_opts['package_manager'] = 'dnf'

config_opts['cache_topdir'] = '$cache_topdir'
config_opts['plugin_conf']['root_cache_enable'] = True
config_opts['plugin_conf']['root_cache_opts'] = {}
config_opts['plugin_conf']['root_cache_opts']['age_check'] = True
config_opts['plugin_conf']['root_cache_opts']['max_age_days'] = 30
config_opts['plugin_conf']['root_cache_opts']['dir'] = '%(cache_topdir)s/%(root)s/root_cache/'
config_opts['plugin_conf']['root_cache_opts']['compress_program'] = 'gzip'
config_opts['plugin_conf']['root_cache_opts']['decompress_program'] = 'gunzip'
config_opts['plugin_conf']['root_cache_opts']['extension'] = '.gz'
config_opts['plugin_conf']['root_cache_opts']['exclude_dirs'] = ['./proc', './sys', './dev', './tmp/ccache', './var/cache/yum', './builddir', './build' ]

config_opts['dnf.conf'] = """
[main]
keepcache=1
debuglevel=0
cachedir=/var/cache/yum
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=1
assumeyes=1
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=0
mdpolicy=group:primary
best=1

# repos
[fedora]
name=fedora
mirrorlist=http://mirrors.fedoraproject.org/mirrorlist?repo=fedora-\\$releasever&arch=\\$basearch
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-fedora-\\$releasever-primary
gpgcheck=1

[updates]
name=updates
mirrorlist=http://mirrors.fedoraproject.org/mirrorlist?repo=updates-released-f\\$releasever&arch=\\$basearch
failovermethod=priority
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-fedora-\\$releasever-primary
gpgcheck=1

[dragonfear]
name=dragonfear
baseurl=http://dnf-updates.dragonfear/fc\\$releasever/
gpgcheck=0
metadata_expire=30
"""
EOF
}

function config_mocklock_qubes() {
    local outputfile="$1"
    local release="$2"
    local arch="$3"
    local basedir="$4"
    local root="$5"
    local cache_topdir="$6"
    local releasewithoutname=$(echo "$release" | sed 's/q//')

    if [ "$release" == "q4.1" ] ; then
        local fedorareleasever=32
        local qubeskeyver=4
    elif [ "$release" == "q4.2" ] ; then
        local fedorareleasever=37
        local qubeskeyver=4.2
    else
        echo Do not know what the matching Fedora release version is for Qubes $release >&2
        exit 55
    fi

    cat > "$outputfile" <<EOF
config_opts['print_main_output'] = True

config_opts['releasever'] = '$releasewithoutname'
config_opts['fedorareleasever'] = '$fedorareleasever'
config_opts['qubeskeyver'] = '$qubeskeyver'

config_opts['target_arch'] = 'x86_64'
config_opts['legal_host_arches'] = ('x86_64',)

config_opts['basedir'] = '$basedir'
config_opts['root'] = '$root'

config_opts['description'] = 'Qubes OS {{ releasever }}'

config_opts['chroot_setup_cmd'] = 'install systemd bash coreutils tar dnf qubes-release rpm-build'

config_opts['dist'] = 'q{{ releasever }}'
config_opts['extra_chroot_dirs'] = [ '/run/lock', ]
config_opts['isolation'] = 'simple'
config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
# generates problems in f37+
config_opts['plugin_conf']['nosync'] = False
config_opts['use_bootstrap'] = False
config_opts['package_manager'] = 'dnf'

config_opts['dnf.conf'] = """
[main]
keepcache=1
debuglevel=0
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=0
assumeyes=1
best=1
module_platform_id=platform:f{{ fedorareleasever }}
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=0
best=1
protected_packages=
user_agent={{ user_agent }}

# repos

[fedora]
name=fedora
metalink=https://mirrors.fedoraproject.org/metalink?repo=fedora-{{ fedorareleasever }}&arch={{ target_arch }}
gpgkey=file:///usr/share/distribution-gpg-keys/fedora/RPM-GPG-KEY-fedora-{{ fedorareleasever }}-primary
gpgcheck=1
skip_if_unavailable=False

[updates]
name=updates
metalink=https://mirrors.fedoraproject.org/metalink?repo=updates-released-f{{ fedorareleasever }}&arch={{ target_arch }}
gpgkey=file:///usr/share/distribution-gpg-keys/fedora/RPM-GPG-KEY-fedora-{{ fedorareleasever }}-primary
gpgcheck=1
skip_if_unavailable=False

[qubes-dom0-current]
name = Qubes Dom0 Repository (updates)
metalink = https://yum.qubes-os.org/r{{ releasever }}/current/dom0/fc{{ fedorareleasever }}/repodata/repomd.xml.metalink
skip_if_unavailable=False
enabled = 1
metadata_expire = 6h
gpgcheck = 1
gpgkey = file:///usr/share/distribution-gpg-keys/qubes/qubes-release-{{ qubeskeyver }}-signing-key.asc

"""
EOF
}

function mocklock() {
    local release="$1"
    shift
    local arch="$1"
    shift

    local basedir="$MOCK_CACHEDIR"
    if test -z "$basedir" ; then
        echo 'No MOCK_CACHEDIR variable configured on this slave.  Aborting.' >&2
        exit 56
    fi

    mkdir -p "$basedir"
    local jail="fedora-$release-$arch-generic"
    local lockfile="$basedir/$jail.lock"
    local tmpcfg=$(mktemp "$basedir"/XXXXXX)
    local cfgfile="$basedir/$jail.cfg"
    local cache_topdir="$HOME/.cache/mock"

    (
        flock 9

        local configurator=
        if [[ $release == q* ]] ; then
            configurator=config_mocklock_qubes
        elif [[ $release == fc* ]] ; then
            configurator=config_mocklock_fedora
        else
            echo "Don't know how to mock $release" >&2
            exit 56
        fi

        local cfgret=0
        $configurator "$tmpcfg" "$release" "$arch" "$basedir" "$jail" "$cache_topdir" || cfgret=$?
        if [ "$cfgret" != "0" ] ; then rm -f "$tmpcfg" ; exit "$cfgret" ; fi

        if cmp "$cfgfile" "$tmpcfg" >&2 ; then
            rm -f "$tmpcfg"
        else
            mv -f "$tmpcfg" "$cfgfile"
            echo Reconfigured "$cfgfile" >&2
        fi

        echo "Running process in mock jail" >&2
        /usr/bin/mock --no-bootstrap-image -r "$cfgfile" "$@" < /dev/null
        exit $?

    ) 9> "$lockfile"

}
'''
}

def call(String distro, String release, String arch, ArrayList args, ArrayList srpms) {
    if (distro == "Fedora") {
        release = "fc" + release
    } else if (distro == "Qubes OS") {
        release = "q" + release
    } else {
        throw new Exception("Unknown distro ${distro}")
    }
    def quotedargs = args.collect{ shellQuote(it) }.join(" ")
    for (srpm in srpms) {
        def mocklock = "mocklock " + release + " " + arch + " " + quotedargs + " " + shellQuote(srpm)
        def cmd = """#!/bin/bash -e
        """ + mockShellLib() + mocklock
        sh(
            script: cmd,
            label: mocklock
        )
    }
}
