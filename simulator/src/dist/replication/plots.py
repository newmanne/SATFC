import sys
import os
sys.path.append(os.path.join(os.environ.get('SATFC', '/home/ubuntu/SATFC'), 'simulator/src/dist/scripts'))
from plotting_utils import *
import glob
from pathlib import Path

if __name__ == '__main__':
    # Change as necessary
    # You can comment out any figures you are not reproducing
    outputs_dir = '/home/ubuntu/outputs' 

    fig_dir = os.path.join(outputs_dir, 'figures')
    Path(fig_dir).mkdir(parents=True, exist_ok=True)
   
    # Figure 5
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_5')))
    df = df.reset_index().drop('type', axis=1)
    df['type'] = df.apply(lambda x: 'No VHF' if x['UHF_Only'] else 'VHF', axis=1)
    df['UHF_Only'] = True
    df = df.set_index(['type', 'auction'])
    TYPES = ['VHF', 'No VHF']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_5', df, types=TYPES, fig_dir=fig_dir, exclude_pre_fcc=True)

    # Figure 6
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_6')))
    TYPES = ['one_stage', 'two_stages', 'three_stages', 'four_stages']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_6', df, types=TYPES, fig_dir=fig_dir)

    # Figure 7
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_7')))
    TYPES = ['early_stopping', 'single_stage']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_7', df, types=TYPES, fig_dir=fig_dir)

    # Figure 8
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_8')))
    TYPES = ['fcc', 'interference', 'population', 'uniform']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_8', df, types=TYPES, fig_dir=fig_dir)

    # Figure 9
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_9')))
    TYPES = ['satfc', 'greedy', 'gnovelty', 'gurobi', 'cplex']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_9', df, types=TYPES, fig_dir=fig_dir, exclude_pre_fcc=True)
    
    # Figure 10
    df = parse_experiment(make_folders(os.path.join(outputs_dir, 'figure_10')))
    TYPES = ['incentive_auction', 'first_to_finish']
    dual_standard_analysis(standard_pallet(TYPES), 'figure_10', df, types=TYPES, fig_dir=fig_dir, exclude_pre_fcc=True)