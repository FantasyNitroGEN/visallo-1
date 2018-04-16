#!/bin/bash -eu

wget --header "Cookie: oraclelicense=accept-securebackup-cookie" -O /opt/jdk-8u161-linux-x64.tar.gz http://download.oracle.com/otn-pub/java/jdk/8u161-b12/2f38c3b165be4555a1fa6e98c45e0808/jdk-8u161-linux-x64.tar.gz
tar -xzf /opt/jdk-8u161-linux-x64.tar.gz -C /opt
rm /opt/jdk-8u161-linux-x64.tar.gz
ln -s /opt/jdk1.8.0_161 /opt/jdk

JAI_VERSION=1.1.3
JAI_IMAGEIO_VERSION=1.1

(cd /opt/jdk
  curl -L http://download.java.net/media/jai/builds/release/$(echo ${JAI_VERSION} | sed -e 's/\./_/g')/jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin -O
  sed -i -e 's|more <<EOF|cat > /dev/null <<EOF|' \
         -e 's/agreed=$/agreed=1/' \
         jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  chmod u+x jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  ./jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin && rm ./jai-$(echo ${JAI_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin

  curl -L http://download.java.net/media/jai-imageio/builds/release/${JAI_IMAGEIO_VERSION}/jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin -O
  chmod u+x jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  sed -i -e 's|more <<EOF|cat > /dev/null <<EOF|' \
         -e 's/agreed=$/agreed=1/' \
         -e 's/^tail +/tail -n +/' \
         jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
  ./jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin && rm jai_imageio-$(echo ${JAI_IMAGEIO_VERSION} | sed -e 's/\./_/g')-lib-linux-amd64-jdk.bin
)
