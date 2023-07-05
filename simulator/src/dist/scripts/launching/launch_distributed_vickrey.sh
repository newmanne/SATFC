#!/bin/bash
#PBS -N FCCSimulator
#PBS -l walltime=720:00:00
#PBS -l nodes=1:ppn=16

if [[ -n "$PBS_O_WORKDIR" ]]; then
    cd $PBS_O_WORKDIR
fi

. ~/.bashrc

/ubc/cs/research/arrow/satfc/releases/FCCSimulator_Current/bin/FCCSimulator -INFO-FILE /ubc/cs/research/arrow/satfc/simulator/IncentiveAuctionSimulator/data/simulator.csv -CONSTRAINT-SET 032416SC46U -MAX-CHANNEL 29 -SOLVER-TYPE DISTRIBUTED -SEND-QUEUE send -LISTEN-QUEUE listen -INTERFERENCES-FOLDER ${SATFC_INTERFERENCE} -REDIS-PORT 8888 -REDIS-HOST paxos -OUTPUT-DIR /ubc/cs/research/arrow/satfc/simulator/output -BASE-CLOCK 300000
