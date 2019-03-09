#!/bin/bash -eu

wget --header "Cookie: oraclelicense=accept-securebackup-cookie" -O /opt/jdk-8u201-linux-x64.tar.gz https://download.oracle.com/otn-pub/java/jdk/8u201-b09/42970487e3af4f5aa5bca3f542482c60/jdk-8u201-linux-x64.tar.gz
tar -xzf /opt/jdk-8u201-linux-x64.tar.gz -C /opt
rm /opt/jdk-8u201-linux-x64.tar.gz
ln -s /opt/jdk1.8.0_201 /opt/jdk

JAI_VERSION=1_1_3
JAI_IMAGEIO_VERSION=1.1

(cd /opt/jdk
  curl -L http://download.java.net/media/jai/builds/release/1_1_3/jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin -O
  sed -i -e 's|more <<EOF|cat > /dev/null <<EOF|' \
         -e 's/agreed=$/agreed=1/' \
         jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  chmod u+x jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  ./jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin && rm ./jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin

  curl -L http://download.java.net/media/jai-imageio/builds/release/1.1/jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin -O
  chmod u+x jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  sed -i -e 's|more <<EOF|cat > /dev/null <<EOF|' \
         -e 's/agreed=$/agreed=1/' \
         -e 's/^tail +/tail -n +/' \
         jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  ./jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin && rm jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
)
