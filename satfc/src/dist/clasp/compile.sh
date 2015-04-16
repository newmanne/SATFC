#!/bin/bash
#Compiles the clasp JNA library .so file for SATFC.

tar -xzvf libjnaclasp-3.tar.gz
cd libjnaclasp-3/jna
./build.sh
mkdir -p ../../jna
mv libjnaclasp.so ../../jna
cd ../../
rm -Rf libjnaclasp-3
