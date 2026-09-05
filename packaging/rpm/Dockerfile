ARG FEDORA_IMAGE=registry.fedoraproject.org/fedora@sha256:3d020c33fbb50af70acaf673e0e6cad94ccef310e9fce6856112e077a5402117
FROM ${FEDORA_IMAGE} AS runtime
RUN dnf -y install python3 strace findutils diffutils tar gzip curl shadow-utils && dnf clean all
WORKDIR /work
FROM runtime AS builder
RUN dnf -y install rpm-build rpmlint redhat-rpm-config binutils cpio && dnf clean all
