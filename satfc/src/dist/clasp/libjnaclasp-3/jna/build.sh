#!/bin/bash

# Parse args (optional- only needed in libtbb is not installed and you have to point to a local copy)
TBB_INCLUDE=''
TBB_LIB=''

if [ ! -z "$1" ]
  then
    TBB_INCLUDE="TBB_INCLUDE=$1"
fi
if [ ! -z "$2" ]
  then
    TBB_LIB="TBB_LIB=$2"
fi

CLASP=../clasp-3
CURRENT=`pwd`

#configure clasp
echo -n "Cleaning previous make... "
make clean
echo "Done."
echo "CLASP----------------------------------------------"
cd $CLASP
./configure.sh $TBB_INCLUDE $TBB_LIB --config=jna --with-mt CXXFLAGS="-O3 -DNDEBUG -fPIC"
#./configure.sh --config=jna CXXFLAGS="-g -fPIC"
cd build/jna_mt
make clean

# copy my modified headers and code to handle interrupts
#origh=$CURRENT/$CLASP/libclasp/clasp/solve_algorithms.h
#origc=$CURRENT/$CLASP/libclasp/src/solve_algorithms.cpp
#cp $origh $origh.bak
#cp $origc $origc.bak
#cp $CURRENT/solve_algorithms.h $origh
#cp $CURRENT/solve_algorithms.cpp $origc
make -j4
#mv $origh.bak $origh
#mv $origc.bak $origc
echo "---------------------------------------------------"
echo "JNA_CLASP------------------------------------------"
cd $CURRENT
# copy the libs
cp $CLASP/build/jna_mt/libclasp/lib/libclasp.a ./
cp $CLASP/build/jna_mt/libprogram_opts/lib/libprogram_opts.a ./

cd $CURRENT
#use the FLAGS that were set in the clasp configure.sh
cp $CLASP/build/jna_mt/.CONFIG ./CLASP_CONFIG
echo "PROJECT_ROOT := $CLASP" > PROJECT_ROOT

make -j4

make clean1

rm CLASP_CONFIG
rm PROJECT_ROOT
echo "---------------------------------------------------"
