#!/bin/bash

cd ./DCCASat+march_rw_sources/
make
cp ./DCCASat+march_rw ../
make cleanup

cd ../DCCASat_with_cutoff_and_for_random_instances_sources/
make
cp ./DCCASat ../
make cleanup

cd ../march_rw_sources/
make
cp ./march_rw ../
make clean

