************ Details about the solver ************
Name: DCCASat+march_rw
Version: CSSC2014
Authors: Chuan Luo [1], Shaowei Cai [2], Wei Wu [1], Kaile Su [3]
[1] School of Electronics Engineering and Computer Science, Peking University, Beijing, China.
[2] State Key Laboratory of Computer Science, Institute of Software, Chinese Academy of Sciences, Beijing, China.
[3] Institute for Integrated and Intelligent Systems, Griffith University, Brisbane, Australia.
**************************************************

****** The corresponding paper of DCCASat ********
[1] Chuan Luo, Shaowei Cai, Wei Wu, Kaile Su: Double Configuration Checking in Stochastic Local Search for Satisfiability. To appear in Proc. of AAAI 2014.
**************************************************

************ How to build the solver *************
Execute the following command:
./build.sh
**************************************************

************ How to use the solver ***************
First, change the current directory to cssc/
Then, copy the binaries 'DCCASat', 'march_rw' and 'DCCASat+march_rw' to the parent directory DCCASat+march_rw/
Finally, execute the following command:
solvers/DCCASat+march_rw/DCCASat+march_rw -inst <inst> -seed <seed> -sls_time_ratio <sls_time_ratio>
**************************************************

