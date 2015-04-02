#!/bin/bash
tar -xvf clasp-2.1.3-source.tar.gz
cd jna
./build.sh
mv libjnaclasp.so ../
cd ../
rm -Rf clasp-2.1.3
