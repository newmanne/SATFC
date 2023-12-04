# Reverse Auction Simulator

This project contains the reverse auction simulator used in `Incentive Auction Design Alternatives: A Simulation Study`. 

Before attempting to run the simulator, make sure that you can run SATFC. Follow the instructions in the [SATFC-manual](../satfc/src/dist/SATFC-manual.pdf). Then, to compile from source, run `gradlew installDist` in the root of the repository. Note that we use the [Gradle](https://gradle.org/) build system for dependency resolution. The simulator executable will appear in `simulator/build/install/FCCSimulator/bin`.

# Env variables
The simulations rely on certain env variables being set to point to Clasp and SATenstein. The paths below assume you have compiled the libraries successfully as per the manual and have not moved them.

`export SATFC_CLASP_LIBRARY=<path_to_satfc>/satfc/src/dist/clasp/jna/libjnaclasp.so`

`export SATFC_SATENSTEIN_LIBRARY=<path_to_satfc>/satfc/src/dist/satenstein/jna/libjnasatenstein.so`

`export SATFC_INTERFERENCE=<Path_to_constraints_folder>`. The constraints folder should point to a folder, where each subfolder is a constraint set (containing domain.csv and interference.csv files). Used the `-CONSTRAINT-SET` flag to select the name of the subfolder to use. Likely the only subfolder you will be interested in are the [FCC's nov 2015 constraints](https://github.com/newmanne/SATFC/tree/development/simulator/src/dist/simulator_data/interference_data/nov2015).

# Command Line Options

This section provides some command line options used for running simulations, including example strings to run simulations that would generate the data for the figures in the paper. The executable to run is `./FCCSimulator`. 

We explain some of the parameters below; more parameters are available in `SimulatorParameters.java` and `MultiBandSimulatorParameters.java`.

#### Regional Simulations

Simulations involving every station can take several hours; multi-stage simulations can take several hour per stage. For testing, you may prefer to run with only a subset of stations. One way to choose such a subset of stations is to specify a starting city and the number of links to follow from stations in this city on the interference graph. Each station within this range will be included the simulations. For example, to study an auctions only including stations within 2 hops of a city in NYC, you can run: `-START-CITY 'NEW YORK' -CITY-LINKS 2 -IGNORE-CANADA true`. The final toggle disables all Canadian stations. Another option is to use the `-STATIONS-TO-USE-FILE` option to specify a CSV file, with the header `FacID`, that lists the stations that should be excluded.

#### Setting the Output Directory

The command line flag `-SIMULATOR-OUTPUT-FOLDER` is used to set the output folder for a simulation.

##### Toggling VHF

To run a simulation that only repacks the UHF band, always include the parameters `-UHF-ONLY true -INCLUDE-VHF false`.

Example simulation commands:
- (UHF-Only) ` -VALUES-SEED 4000 -MIP-PARALLELISM 8  -MAX-CHANNEL 36 -LOCK-VHF-UNTIL-BASE true -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true`
- (UHF+VHF) `-VALUES-SEED 4000 -MIP-PARALLELISM 8  -MAX-CHANNEL 36 -LOCK-VHF-UNTIL-BASE true -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true`

##### Setting the Value Model
 
To run a simulation using the BD model, use the flag `-POP-VALUES true`. Note that unfortunately we are unable provide the parameters that we used to run the MCS model from the paper `Doraszelski, Ulrich, et al. Ownership concentration and strategic supply reduction. No. w23034. National Bureau of Economic Research, 2017.` as these were provided to us by the authors directly; please contact the authors if you want to run their model. Note that the file format for this model should have the following format, where each line has a station's parameters.

```
FacID,MeanCF,MeanCFMultiples,MeanLogStick
77452,-0.3,7.2,-2.7
```

The seed controlling the value profiles is set by the `-VALUES-SEED <number>` parameter. You should simulate many value seeds, using the same seeds across the different auction designs being compared.

#### Handling Impairments

To handle impairments as described in the paper, use `-RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`. 

##### Setting the Clearing Target

Use the parameters `-MAX-CHANNEL <channel>` and `-MAX-CHANNEL-FINAL <channel>` to set a start and end stage for the auction (following the FCC's band plan). For example, a four stage auction mirroring the incentive auction would look like `-MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 36`.

Example simulation commands:
* (4-stages) `-VALUES-SEED 2000 -MIP-PARALLELISM 8 -MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (3-stages) `-VALUES-SEED 2000 -MIP-PARALLELISM 8 -MAX-CHANNEL 31 -MAX-CHANNEL-FINAL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (2-stages) `-VALUES-SEED 2000 -MIP-PARALLELISM 8 -MAX-CHANNEL 32 -MAX-CHANNEL-FINAL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (1-stage) `-VALUES-SEED 2000 -MIP-PARALLELISM 8 -MAX-CHANNEL 36 -MAX-CHANNEL-FINAL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`


##### Scoring

By default, the FCC volumes are used. To supply other volumes, use  `-VOLUMES-FILE <volume file path>`. Volumes files used in the paper are stored [here](./src/dist/simulator_data/volumes.csv).

Example simulation commands:
* (FCC) `-VALUES-SEED 1000 -MIP-PARALLELISM 8 -VOLUMES-FILE simulator_data/volumes.csv -MAX-CHANNEL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (Population) `-VALUES-SEED 1000 -MIP-PARALLELISM 8 -VOLUMES-FILE simulator_data/half_pop_score.csv -MAX-CHANNEL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (Interference) `-VALUES-SEED 1000 -MIP-PARALLELISM 8 -VOLUMES-FILE simulator_data/half_int_score.csv -MAX-CHANNEL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (Uniform) `-VALUES-SEED 1000 -MIP-PARALLELISM 8 -VOLUMES-FILE simulator_data/uniform.csv -MAX-CHANNEL 36 -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`


##### Early Stopping

To use early stopping, you need to supply forward auction revenues (in billions). For example, `-EARLY-STOPPING true -FORWARD-AUCTION-AMOUNTS 25.35,23.61,21.59,19.63,17.57,15.40,13.11,10.66,7.95 -MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 44`.

Example simulation commands:
- (Early Stopping) `-VALUES-SEED 25000 -MIP-PARALLELISM 8 -EARLY-STOPPING true -FORWARD-AUCTION-AMOUNTS 25.35,23.61,21.59,19.63,17.57,15.40,13.11,10.66,7.95 -MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 44 -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
- (Single-stage Oracle) `-VALUES-SEED 25000 -MIP-PARALLELISM 8   -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true -MAX-CHANNEL <corresponding_end_channel> -MAX-CHANNEL-FINAL <corresponding_end_channel>` (note that corresponding_end_channel here is based on the stage that the previous simulation ends at)


##### Feasibility Checker

The feasibility checker can be configured using the `-CONFIG-FILE <config_YAML_file_path>` argument. Some example config files can be found in `satfc/src/dist/bundles`. These config files can be altered to allow the use of external SAT and MIP solvers. See also `YAMLBundle.java` in the SATFC project. The greedy feasibility checker is special; it is activated using `-GREEDY-SOLVER-ONLY true`. 

Using third-party feasibility checkers is somewhat involved. In order to use third-party solvers, you will first have to download and install [aclib](http://aclib.net/). After, you will need to move the [run_wrapper](src/dist/scripts/satfc_wrapper.py) into the aclib folder and then update the path to Runsolver (contained within aclib) as well as the `ALG_DIR` variable.

The relevant config files are here for [gnovelty](../satfc/src/dist/bundles/satfc_gnovelty.yaml), [CPLEX](../satfc/src/dist/bundles/cplex.yaml), and [Gurobi](../satfc/src/dist/bundles/gurobi.yaml). You will need to modify the paths in these files as appropriate. CPLEX and Gurobi are commerical software that you will need to install independently and acquire licenses for. You will also need to set the `GUROBI_HOME` and `CPLEX_DIR` environment variables to (your platform may vary) something like `gurobi600/linux64` and `ILOG/CPLEX_Studio129/cplex/bin/x86-64_linux`. In the case of CPLEX, you may also need to add `ILOG/CPLEX_Studio129/cplex/bin/x86-64_linux` to your PATH variable.

The `-SWITCH-FEASIBILITY-AT-BASE` parameter is used to switch feasibility checkers after the impairment phase (so that the impairment phase consistently uses SATFC).

Example simulation commands:
* (CPLEX/Gurobi/Gnovelty) `-VALUES-SEED 3000 -MIP-PARALLELISM 8 -CONFIG-FILE <relevant_bundle_file>.yaml -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (Greedy) `-VALUES-SEED 3000 -MIP-PARALLELISM 8 -GREEDY-SOLVER-ONLY true -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (SATFC) `-VALUES-SEED 3000 -MIP-PARALLELISM 8  -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`


##### Alternative Bid Processing Algorithms

The parameter `-BID-PROCESSING` can trigger alternate bid processing algorithms. `-BID-PROCESSING NO_PRICE_DROPS_FOR_TIMEOUTS` will revisit timeouts.  The first-to-finish bid processing can be activated with `-BID-PROCESSING FIRST_TO_FINISH_SINGLE_PROGRAM` (in the paper, `-CONFIG-FILE satfc_parallel_with_wait.yaml` was also used).

Example simulation commands:
* (using first-to-finish algorithm) `-VALUES-SEED 6000 -MIP-PARALLELISM 8 -BID-PROCESSING FIRST_TO_FINISH_SINGLE_PROGRAM -MAX-CHANNEL 36 -CONFIG-FILE /ubc/cs/research/arrow/satfc/bundles/satfc_parallel_with_wait.yaml -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`
* (default) `-VALUES-SEED 6000 -MIP-PARALLELISM 8  -MAX-CHANNEL 36 -CONFIG-FILE /ubc/cs/research/arrow/satfc/bundles/satfc_parallel_with_wait.yaml -UHF-ONLY true -INCLUDE-VHF false -POP-VALUES true -RAISE-CLOCK-TO-FULL-PARTICIPATION true -LOCK-VHF-UNTIL-BASE true`

# Analyzing Results

See [plotting_utils](src/dist/scripts/plotting_utils.py), specifically `parse_experiment` and `standard_analysis` to produce figures like in the paper.

For example, here is code that would plot the scoring rule figure (assuming `plotting_utils` is imported, and that the scoring simulations are organized into the folders "FCC", "Interference", "Population", and "Uniform", with each of those folders containing one subfolder per simulation output). You should vary the `FIGDIR` variable (at the top of `plotting_utils`) to point to your desired output folder.

```python
scoring_df = parse_experiment(make_folders('<PATH_TO_RESULTS>'), skip_failures=False)
TYPES = ['FCC', 'Interference', 'Population', 'Uniform']
d = standard_pallet(TYPES)
d['FCC']['label'] = 'Incentive Auction'
dual_standard_analysis(d, 'scoring', scoring_df.loc[TYPES], types=TYPES)
```
