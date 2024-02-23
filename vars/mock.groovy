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
config_opts['releasever'] = '$releasewithoutname'

config_opts['dist'] = '$release'  # only useful for --resultdir variable subst
config_opts['extra_chroot_dirs'] = ['/run/lock']

config_opts['target_arch'] = '$arch'
config_opts['legal_host_arches'] = ('$arch',)

config_opts['package_manager'] = '{% if releasever|int >= 40 %}dnf5{% else %}dnf{% endif %}'

config_opts['use_bootstrap'] = False

config_opts['description'] = 'Fedora {{ releasever }}'

config_opts['chroot_setup_cmd'] = 'install @buildsys-build /usr/bin/pigz /usr/bin/lbzip2'
config_opts['macros']['%__gzip'] = '/usr/bin/pigz'
config_opts['macros']['%__bzip2'] = '/usr/bin/lbzip2'

config_opts['isolation'] = 'simple'
config_opts['rpmbuild_networking'] = False
config_opts['use_host_resolv'] = False

config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
config_opts['plugin_conf']['nosync'] = True

config_opts['cache_topdir'] = '$cache_topdir'
config_opts['plugin_conf']['root_cache_enable'] = True
config_opts['plugin_conf']['root_cache_opts'] = {}
config_opts['plugin_conf']['root_cache_opts']['age_check'] = True
config_opts['plugin_conf']['root_cache_opts']['max_age_days'] = 30
config_opts['plugin_conf']['root_cache_opts']['dir'] = '%(cache_topdir)s/%(root)s/root_cache/'
config_opts['plugin_conf']['root_cache_opts']['compress_program'] = 'pigz'
config_opts['plugin_conf']['root_cache_opts']['decompress_program'] = 'unpigz'
config_opts['plugin_conf']['root_cache_opts']['extension'] = '.gz'
config_opts['plugin_conf']['root_cache_opts']['exclude_dirs'] = ['./proc', './sys', './dev', './tmp/ccache', './var/cache/yum', './builddir', './build' ]

config_opts['dnf.conf'] = """
[main]
keepcache=1
system_cachedir=/var/cache/dnf
debuglevel=0
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=1
assumeyes=1
best=1
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=1800
protected_packages=
user_agent={{ user_agent }}

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

config_opts['basedir'] = '$basedir'
config_opts['root'] = '$root'
config_opts['releasever'] = '$releasewithoutname'
config_opts['fedorareleasever'] = '$fedorareleasever'
config_opts['qubeskeyver'] = '$qubeskeyver'

config_opts['dist'] = 'q{{ releasever }}'
config_opts['extra_chroot_dirs'] = ['/run/lock']

config_opts['target_arch'] = '$arch'
config_opts['legal_host_arches'] = ('$arch',)

config_opts['package_manager'] = '{% if releasever|int >= 40 %}dnf5{% else %}dnf{% endif %}'

config_opts['use_bootstrap'] = False

config_opts['description'] = 'Qubes OS {{ releasever }}'

config_opts['chroot_setup_cmd'] = 'install @buildsys-build /usr/bin/pigz /usr/bin/lbzip2'
config_opts['macros']['%__gzip'] = '/usr/bin/pigz'
config_opts['macros']['%__bzip2'] = '/usr/bin/lbzip2'

config_opts['isolation'] = 'simple'
config_opts['rpmbuild_networking'] = False
config_opts['use_host_resolv'] = False

config_opts['plugin_conf']['ccache_enable'] = False
config_opts['plugin_conf']['generate_completion_cache_enable'] = False
config_opts['plugin_conf']['nosync'] = True


config_opts['cache_topdir'] = '$cache_topdir'
config_opts['plugin_conf']['root_cache_enable'] = True
config_opts['plugin_conf']['root_cache_opts'] = {}
config_opts['plugin_conf']['root_cache_opts']['age_check'] = True
config_opts['plugin_conf']['root_cache_opts']['max_age_days'] = 30
config_opts['plugin_conf']['root_cache_opts']['dir'] = '%(cache_topdir)s/%(root)s/root_cache/'
config_opts['plugin_conf']['root_cache_opts']['compress_program'] = 'pigz'
config_opts['plugin_conf']['root_cache_opts']['decompress_program'] = 'unpigz'
config_opts['plugin_conf']['root_cache_opts']['extension'] = '.gz'
config_opts['plugin_conf']['root_cache_opts']['exclude_dirs'] = ['./proc', './sys', './dev', './tmp/ccache', './var/cache/yum', './builddir', './build' ]

config_opts['dnf.conf'] = """
[main]
keepcache=1
system_cachedir=/var/cache/dnf
debuglevel=0
reposdir=/dev/null
logfile=/var/log/yum.log
retries=20
obsoletes=1
gpgcheck=0
assumeyes=1
best=1
syslog_ident=mock
syslog_device=
install_weak_deps=0
metadata_expire=1800
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

[dragonfear]
name=dragonfear
baseurl=http://dnf-updates.dragonfear/fc\\$releasever/
gpgcheck=0
metadata_expire=30
"""
EOF

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

    local jaildir="$MOCK_JAILDIR"
    if test -z "$jaildir" ; then
        echo 'No MOCK_BASEDIR variable configured on this slave.  Aborting.' >&2
        exit 56
    fi

    local cachedir="$MOCK_CACHEDIR"
    if test -z "$cachedir" ; then
        echo 'No MOCK_CACHEDIR variable configured on this slave.  Aborting.' >&2
        exit 56
    fi

    mkdir -p "$jaildir" "$cachedir"
    local jail="fedora-$release-$arch-generic"
    local lockfile="$jaildir/$jail.lock"
    local tmpcfg=$(mktemp "$jaildir/tmp-$jail-XXXXXX.cfg")
    local cfgfile="$jaildir/$jail.cfg"

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
        $configurator "$tmpcfg" "$release" "$arch" "$jaildir" "$jail" "$cachedir" || cfgret=$?
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
    def quotedargs = args.collect{ shellQuote(it) }.join(" ")
    lock("mock-$distro-$release-$arch") {
        timestamps {
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
    }
}
