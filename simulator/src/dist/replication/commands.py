import argparse
import os


def main(output, satfc_root):
    commands = []
    num_sims = 50

    binary_path = os.path.join(satfc_root, 'simulator/build/install/FCCSimulator/bin/FCCSimulator')

    def make_command(seed, outdir_suffix, command):
        seed_str = f'Seed_{seed}'
        extra_suffix = '_POP'
        if '-UHF-ONLY true' in command:
            extra_suffix = '_UHF_ONLY'
        cmd = f'{binary_path} -VALUES-SEED {seed} -SIMULATOR-OUTPUT-FOLDER {os.path.join(output, outdir_suffix + extra_suffix, seed_str)} -POP-VALUES true -MIP-PARALLELISM 8   {command}'
        commands.append(cmd)

    # Figure 5: VHF
    for i in range(4000, 4000 + num_sims):
        make_command(i, 'figure_5/uhf', '-MAX-CHANNEL 36 -UHF-ONLY true -INCLUDE-VHF false')
        make_command(i, 'figure_5/vhf', '-MAX-CHANNEL 36')

    # Figure 6: Multiple Stages
    for i in range(2000, 2000 + num_sims):
        make_command(i, 'figure_6/four_stages', '-MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 36')
        make_command(i, 'figure_6/three_stages', '-MAX-CHANNEL 31 -MAX-CHANNEL-FINAL 36')
        make_command(i, 'figure_6/two_stages', '-MAX-CHANNEL 32 -MAX-CHANNEL-FINAL 36')
        make_command(i, 'figure_6/one_stage', '-MAX-CHANNEL 36 -MAX-CHANNEL-FINAL 36')

    # Figure 7: Early Stopping vs Single Stage
    for i in range(25000, 25000 + num_sims):
        make_command(i, 'figure_7/early_stopping', '-EARLY-STOPPING true -FORWARD-AUCTION-AMOUNTS 25.35,23.61,21.59,19.63,17.57,15.40,13.11,10.66,7.95 -MAX-CHANNEL 29 -MAX-CHANNEL-FINAL 44 -UHF-ONLY true -INCLUDE-VHF false')
        ### Note: Once you have run all of the above simulations, note the stage that each simulation ended on. Then modify and generate the below command. You may have a different ending stage on a seed-by-seed basis.
        # make_command(i, 'figure_7/single_stage', '-UHF-ONLY true -INCLUDE-VHF false   -MAX-CHANNEL <corresponding_end_channel> -MAX-CHANNEL-FINAL <corresponding_end_channel>')

    # Figure 8: Scoring Rules
    for i in range(1000, 1000 + num_sims):
        volume_file = os.path.join(satfc_root, 'simulator/src/dist/simulator_data/volumes.csv')
        make_command(i, 'figure_8/fcc', f'-VOLUMES-FILE {volume_file} -MAX-CHANNEL 36')
        pop_volume_file = os.path.join(satfc_root, 'simulator/src/dist/simulator_data/half_pop_score.csv')
        make_command(i, 'figure_8/population', f'-VOLUMES-FILE {pop_volume_file} -MAX-CHANNEL 36')
        int_volume_file = os.path.join(satfc_root, 'simulator/src/dist/simulator_data/half_int_score.csv')
        make_command(i, 'figure_8/interference', f'-VOLUMES-FILE {int_volume_file} -MAX-CHANNEL 36')
        uniform_volume_file = os.path.join(satfc_root, 'simulator/src/dist/simulator_data/uniform.csv')
        make_command(i, 'figure_8/uniform', f'-VOLUMES-FILE {uniform_volume_file} -MAX-CHANNEL   ')

    # Figure 9: Feasibility Checkers
    for i in range(3000, 3000 + num_sims):
        make_command(i, 'figure_9/satfc', '-MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true')
        make_command(i, 'figure_9/greedy', '-GREEDY-SOLVER-ONLY true -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true')
        gnovelty_config = os.path.join(satfc_root, 'satfc/src/dist/bundles/satfc_gnovelty.yaml')
        make_command(i, 'figure_9/gnovelty', f'-CONFIG-FILE {gnovelty_config} -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true')
        gurobi_config = os.path.join(satfc_root, 'satfc/src/dist/bundles/gurobi.yaml')
        make_command(i, 'figure_9/gurobi', f'-CONFIG-FILE {gurobi_config} -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true')
        cplex_config = os.path.join(satfc_root, 'satfc/src/dist/bundles/cplex.yaml')
        make_command(i, 'figure_9/cplex', f'-CONFIG-FILE {cplex_config} -MAX-CHANNEL 36 -SWITCH-FEASIBILITY-AT-BASE true')

    # Figure 10: First-to-finish Algorithm
    for i in range(6000, 6000 + num_sims):


        wait_config = os.path.join(satfc_root, 'satfc/src/dist/bundles/satfc_parallel_wait.yaml')
        make_command(i, 'figure_10/first_to_finish', f'-BID-PROCESSING FIRST_TO_FINISH_SINGLE_PROGRAM -MAX-CHANNEL 36 -CONFIG-FILE {wait_config} -UHF-ONLY true -INCLUDE-VHF false')
        make_command(i, 'figure_10/fcc', f'-MAX-CHANNEL 36 -CONFIG-FILE {wait_config} -UHF-ONLY true -INCLUDE-VHF false')

    # Output command list
    with open('commands.txt', 'w') as f:
        for command in commands:
            f.write("%s\n" % command)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=str, default='/home/ubuntu/outputs')
    parser.add_argument("--satfc_root", type=str, default='/home/ubuntu/SATFC')
    args = parser.parse_args()
    main(args.output, args.satfc_root)