# Reverse Auction Simulator

This project contains the reverse auction simulator used in `Incentive Auction Design Alternatives: A Simulation Study`. 

Before attempting to run the simulator, make sure that you can run SATFC. Follow the instructions in the [SATFC-manual](satfc/src/dist/SATFC-manual.pdf).

# Env variables
This assumes you have compiled the libraries successfully and have not moved them.

`export SATFC_CLASP_LIBRARY=<path_to_satfc>/satfc/src/dist/clasp/jna/libjnaclasp.so`

`export SATFC_SATENSTEIN_LIBRARY=<path_to_satfc>/satfc/src/dist/satenstein/jna/libjnasatenstein.so`

`export SATFC_INTERFERENCE=<Path_to_constraints_folder>`. The constraints folder should point to a folder, where each subfolder is a constraint set (containing domain.csv and interference.csv files). Used the `-CONSTRAINT-SET` flag to select the name of the subfolder to use. Likely your only subfolder will the FCC's nov 2015 constraints.

# Command Line Options

This section provides some command line options used for running simulations. The executable to run is `./FCCSimulator`.

More parameters are available in `SimulatorParameters.java`.

##### Toggling VHF

To run a simulation that only repacks the UHF band, always include the parameters `-UHF-ONLY true -INCLUDE-VHF false`.

##### Setting the Value Model
 
To run a simulation using the BD model, use the flag `-POP-VALUES true`. Note that unfortunately we are unable provide the parameters that we used to run the MCS model as these were provided to us by the authors directly. The seed controlling the value profiles is set by the `-VALUES-SEED <number>` parameter.

#### Handling Impairments

To handle impairments as described in the paper, use `-RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`. 

##### Setting the Clearing Target

Use the parameters `-MAX-CHANNEL <channel>` and `-MAX-CHANNEL-FINAL <channel>` to set a start and end stage for the auction (following the FCC's band plan). For example, a four stage auction mirroring the incentive auction would look like `-MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 36`.

##### Scoring

By default, the FCC volumes are used. To supply other volumes, use  `-VOLUMES-FILE <volume file path>`. Volumes files used in the paper are stored in `simulator/src/dist/simulator_data`. 

##### Early Stopping

To use early stopping, you need to supply forward auction revenues (in billions). For example, `-EARLY-STOPPING true -FORWARD-AUCTION-AMOUNTS 25.35,23.61,21.59,19.63,17.57,15.40,13.11,10.66,7.95 -MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 44`.

##### Feasibility Checker

The feasibility checker can be configured using the `-CONFIG-FILE <config_YAML_file_path>` argument. While undocumented, example config files can be found in `satfc/src/dist/bundles`. These config files can be configured to allow the use of external SAT and MIP solvers. See also `YAMLBundle.java` in the SATFC project. The greedy feasibility checker is special; it is activated using `-GREEDY-SOLVER-ONLY true`. You will need to download the non-SATFC solvers. See [aclib](http://aclib.net/).

The `-SWITCH-FEASIBILITY-AT-BASE` parameter is used to switch feasibility checkers after the impairment phase (so that the impairment phase consistently uses SATFC).

An example complete command looks like `-VALUES-SEED 3000 -MIP-PARALLELISM 8 -CONFIG-FILE /ubc/cs/research/arrow/satfc/bundles/default_solvers/satfc_gnovelty.yaml -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`

##### Alternative Bid Processing Algorithms

The parameter `-BID-PROCESSING` can trigger alternate bid processing algorithms. `-BID-PROCESSING NO_PRICE_DROPS_FOR_TIMEOUTS` will revisit timeouts.  The first-to-finish bid processing can be activated with `-BID-PROCESSING FIRST_TO_FINISH_SINGLE_PROGRAM` (in the paper, `-CONFIG-FILE satfc_parallel_with_wait.yaml` was also used).

# Analyzing Results

See [plotting_utils](https://github.com/newmanne/SATFC/blob/development/simulator/src/dist/scripts/plotting_utils.py), specifically `parse_experiment` and `stanard_analysis` to produce figures like in the paper.
