#!/bin/bash
#PBS -N SATFCWorker
#PBS -l walltime=720:00:00
#PBS -l nodes=1:ppn=8
#PBS -t 1-2

if [[ -n "$PBS_O_WORKDIR" ]]; then
    cd $PBS_O_WORKDIR
fi

. ~/.bashrc

cd /ubc/cs/research/arrow/satfc/simulator/workers
SATFC="/ubc/cs/research/arrow/satfc/releases/SATFC_Current/bin/SATFC"
PARAMS="-INTERFERENCES-FOLDER ${SATFC_INTERFERENCE} -SIMULATOR-WORKER true -REDIS-QUEUE send -REDIS-PORT 8888 -REDIS-HOST paxos -LOG-FILE SATFC_${PBS_JOBNAME}.log"

echo "Executing:"
echo "$SATFC $PARAMS"
eval $SATFC $PARAMS

