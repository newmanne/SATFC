#!/bin/bash
# Compiles the UBCSAT/SATenstein JNA library .so file for SATFC.

cd ubcsat_stein
mkdir build
cd build
echo "Building UBCSAT ..."
cmake ..
make jnaubcsat
echo "Moving UBCSAT library ..."
mkdir -p ../../jna
mv libjnaubcsat.so ../../jna
cd ../..