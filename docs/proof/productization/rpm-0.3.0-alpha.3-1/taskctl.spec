# Package the already parity-tested native artifact. Never compile or rewrite it.
%global upstream_version 0.3.0-alpha.3
%global debug_package %{nil}
%global __brp_strip %{nil}
%global __brp_strip_comment_note %{nil}
%global __brp_strip_static_archive %{nil}
%global _build_id_links none
# Bootstrap is a canonical cross-platform template, not a Fedora-specific fork.
%global __brp_mangle_shebangs_exclude_from ^%{_datadir}/taskctl/bootstrap/taskctl$

Name:           taskctl
Version:        0.3.0~alpha.3
Release:        %{?packaging_release}%{!?packaging_release:1}%{?dist}
Summary:        Repository task graphs and verified transitions
# taskctl's project license; linked runtime/dependency notices are shipped verbatim.
License:        Apache-2.0
URL:            https://github.com/brule-io/taskctl
Source0:        https://github.com/brule-io/taskctl/releases/download/v%{upstream_version}/taskctl-%{upstream_version}-native-linux-x86_64.tar.gz
Source1:        taskctl-rpm-support.tar.gz
Source2:        taskctl-rpm-sources.sha256
ExclusiveArch:  x86_64
BuildRequires:  coreutils
BuildRequires:  tar
BuildRequires:  gzip
Requires:       /bin/sh

%description
Tasks form a causal prerequisite graph with bounded intent and closure evidence.
Independent planning indexes associate work by scope. This package installs
the canonical Linux native executable globally. Repository-pinned ./taskctl
continues to select its own locked archive independently of this system package.
The alpha protocol is not frozen. taskctl is licensed under Apache-2.0.

%prep
%setup -q -T -c
(cd "$(dirname %{SOURCE0})" && sha256sum --check %{SOURCE2})
mkdir -p native support
tar -xzf %{SOURCE0} -C native
tar -xzf %{SOURCE1} -C support
cd native
sha256sum --check files.sha256

%build
# No compiler, downloads, dynamic generation or protocol-specific build path.

%install
install -Dpm 0755 native/taskctl %{buildroot}%{_libexecdir}/taskctl/taskctl
install -Dpm 0755 support/taskctl-global %{buildroot}%{_bindir}/taskctl
install -d %{buildroot}%{_datadir}/taskctl/bootstrap
install -pm 0644 native/distribution.json native/distribution.properties support/toolchain.lock %{buildroot}%{_datadir}/taskctl/
install -pm 0644 native/bootstrap/* %{buildroot}%{_datadir}/taskctl/bootstrap/
chmod 0755 %{buildroot}%{_datadir}/taskctl/bootstrap/taskctl
install -Dpm 0644 support/taskctl.1 %{buildroot}%{_mandir}/man1/taskctl.1
# Preserve canonical guide bytes and their relative license links. License
# payloads keep their independent %%license classification below.
install -d %{buildroot}%{_docdir}/%{name}
cp -pr native/docs %{buildroot}%{_docdir}/%{name}/
install -pm 0644 native/README.md support/README.rpm.md %{buildroot}%{_docdir}/%{name}/
ln -s ../../licenses/%{name}/LICENSE %{buildroot}%{_docdir}/%{name}/LICENSE
ln -s ../../licenses/%{name}/NOTICE.md %{buildroot}%{_docdir}/%{name}/NOTICE.md

%check
# RPM brp processing must not change the proven executable.
cmp native/taskctl %{buildroot}%{_libexecdir}/taskctl/taskctl
for file in bootstrap/taskctl bootstrap/taskctl.ps1 bootstrap/taskctl.bat distribution.json distribution.properties; do
    cmp native/$file %{buildroot}%{_datadir}/taskctl/$file
done

%files
%license native/LICENSE native/NOTICE.md native/THIRD-PARTY-NOTICES.zip
%doc %{_docdir}/%{name}/
%{_bindir}/taskctl
%dir %{_libexecdir}/taskctl
%{_libexecdir}/taskctl/taskctl
%{_datadir}/taskctl/
%{_mandir}/man1/taskctl.1*

%changelog
* Sun Sep 06 2026 taskctl maintainers - 0.3.0~alpha.3-1
- Package the canonical Linux native artifact without rebuilding it.
