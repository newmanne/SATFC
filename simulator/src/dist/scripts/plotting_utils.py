import numpy as np
import glob
import simulatorutils
import pandas as pd
from simulatorutils import MultiBandAuctionState, VCGState
import os
import seaborn as sns
from tqdm import tqdm_notebook as tqdm
from matplotlib.ticker import FuncFormatter
import operator
import subprocess
import matplotlib.colors as colors
from statsmodels.distributions.empirical_distribution import ECDF

import tempfile
from matplotlib.patches import Rectangle
import matplotlib.patches as mpatches
import matplotlib.pyplot as plt

from collections import defaultdict
from matplotlib import rc
import humanize
import datetime as dt
import logging
logging.basicConfig(format='%(asctime)s %(levelname)s:%(message)s', level=logging.DEBUG, datefmt='%I:%M:%S')
logger = logging.getLogger()
logger.setLevel(logging.INFO)

from scipy.stats import wilcoxon

DATA_DIR = '/shared/v10/'
FIGDIR = '/apps/notebooks/figures'
COST = 'Total Cost'
EFFICIENCY = 'Total Value Loss'

DEFAULT_MARKERSIZE = 120
MARKERSIZE = DEFAULT_MARKERSIZE
sns.set(font_scale=4, style='whitegrid')
FIGSIZE = (18,12)

RASTERIZE_POINTS = False

normalized = True
# op = operator.sub 
op = operator.truediv
means_only = False

PALLET = [
    {
        'color': 'red',
        'marker': 'o',
        'facecolor': 'none',
        'linewidth': 1
    },
    {
        'color': 'blue',
        'marker': 's',
        'facecolor': 'none',
        'linewidth': 1
    },
    {
        'color': 'black',
        'marker': '^',
        'facecolor': 'none',
        'linewidth': 1
    },
    {
        'color': 'green',
        'marker': 'D',
        'facecolor': 'none',
        'linewidth': 1
    },
    {
        'color': 'purple'
    },
    {
        'color': 'orange'
    },
    {
        'color': 'pink'
    }
]

def limits_from_frame(df, reference_type='FCC', exclude_pre_fcc=False):
    df = df.reset_index().copy().set_index(['auction', 'model', 'UHF_Only'])
    reference_type = df.query(f'type == "{reference_type}"')
    
    if exclude_pre_fcc:
        efficiency_metric = EFFICIENCY
        cost_metric = COST
    else:
        efficiency_metric = EFFICIENCY + ' No Exclude'
        cost_metric = COST + ' No Exclude'

    
#     display(reference_type)
    top = (df[cost_metric] / reference_type[cost_metric]).max()
    bottom = (df[cost_metric] / reference_type[cost_metric]).min()
    left = (df[efficiency_metric] / reference_type[efficiency_metric]).min()
    right = (df[efficiency_metric] / reference_type[efficiency_metric]).max()
    print((left, right, top, bottom))
    return (left, right, bottom, top)

def special_save_fig(fig, file_name, fmt=None, dpi=300, tight=True):
    """Save a Matplotlib figure as EPS/PNG/PDF to the given path and trim it.
    """
    if not fmt:
        fmt = file_name.strip().split('.')[-1]

    if fmt not in ['eps', 'png', 'pdf']:
        raise ValueError('unsupported format: %s' % (fmt,))

    extension = '.%s' % (fmt,)
    if not file_name.endswith(extension):
        file_name += extension

    file_name = os.path.abspath(file_name)
    
    with tempfile.NamedTemporaryFile() as tmp_file:
        tmp_name = tmp_file.name + extension

    # save figure
    if tight:
        fig.savefig(tmp_name, dpi=dpi, bbox_inches='tight')
    else:
        fig.savefig(tmp_name, dpi=dpi)

    #trim it
    if fmt == 'eps':
        subprocess.call('epstool --bbox --copy %s %s' %
                        (tmp_name, file_name), shell=True)
    elif fmt == 'png':
        subprocess.call('convert %s -trim %s' %
                        (tmp_name, file_name), shell=True)
    elif fmt == 'pdf':
        subprocess.call('pdfcrop %s %s' % (tmp_name, file_name), shell=True)

def name_to_extra(d, name, include_label=True):
    retval = dict(d.get(name, {}))
    retval['label'] = retval.get('label', name)
    if not include_label:
        del retval['label']
    return retval

def plotting_code(FRAMES, normalized, d, op=operator.truediv, means=True, means_only=False, color_is_stage=False, x_col=EFFICIENCY, y_col=COST):
    cNorm  = colors.Normalize(vmin=1, vmax=9)
    cmap = plt.cm.get_cmap('tab10', 9)
    base_kwargs = dict()
    if color_is_stage:
        base_kwargs['cmap'] = cmap
        base_kwargs['norm'] = cNorm
    
    baseline = FRAMES[0]
    fig = plt.figure(figsize=FIGSIZE)
    for ii, frame in enumerate(FRAMES):
        name = frame['type'].unique()[0]
        if normalized:         
            if not means_only and ii > 0:
                scatter = plt.scatter(x=op(frame[x_col], baseline[x_col]), y=op(frame[y_col],baseline[y_col]), sizes=[MARKERSIZE]*len(frame), **name_to_extra(d, name), alpha=0.6, rasterized=RASTERIZE_POINTS, zorder=10)
                scatter.set_clip_on(False)
            if means or ii == 0:
                mean_eff = op(frame[x_col].mean(), baseline[x_col].mean())
                mean_cost = op(frame[y_col].mean(), baseline[y_col].mean())
                
                if name != baseline['type'].unique()[0]:
                    print(f"Mean value loss of {name} is {mean_eff} of reference. Mean cost is {mean_cost} of reference")
                extra_args = name_to_extra(d, name, include_label=means_only or ii==0)
                if 'marker' in extra_args:
                    del extra_args['marker']
                if 'facecolor' in extra_args:
                    del extra_args['facecolor']
                scatter = plt.scatter(x=[mean_eff], y=[mean_cost], sizes=[MARKERSIZE*8], marker='*' if ii > 0 else 'd',rasterized=RASTERIZE_POINTS, zorder=10, **extra_args)
                scatter.set_clip_on(False)
        else:
            if means:
                extra_args = name_to_extra(d, name, include_label=means_only)
                if 'marker' in extra_args:
                    del extra_args['marker']
                if 'facecolor' in extra_args:
                    del extra_args['facecolor']
                scatter = plt.scatter(x=frame[x_col].mean(), y=frame[y_col].mean(), sizes=[MARKERSIZE*5], marker='*',rasterized=RASTERIZE_POINTS, zorder=10, **extra_args)
                scatter.set_clip_on(False)
            if not means_only:
                kwargs = dict(base_kwargs)

                tmp = name_to_extra(d, name)                
                    
                scatter = plt.scatter(x=frame[x_col], y=frame[y_col], sizes=[MARKERSIZE]*len(frame), rasterized=RASTERIZE_POINTS, **tmp, zorder=10, **kwargs)
                scatter.set_clip_on(False)

    return fig

def format_normalized(df, baseline, op=operator.truediv, set_limits=True, x_col=EFFICIENCY, y_col=COST):
    '''Limits is (left, right, top, bottom) tuple or None'''
    op_to_rep = {operator.truediv: '/', operator.sub: '(Billions) -'}
    ax = plt.gca()
    baseline_name = baseline['type'].unique()[0]
    if op == operator.truediv:
        plt.xlabel(f'Normalized Value Loss')
        plt.ylabel(f'Normalized Cost')
#         plt.xlabel(f'Value Loss {op_to_rep[op]} {baseline_name} Value Loss')
#         plt.ylabel(f'Cost {op_to_rep[op]} {baseline_name} Cost')
    else:
        plt.xlabel(f'Value Loss {op_to_rep[op]} {baseline_name} Value Loss')
        plt.ylabel(f'Cost {op_to_rep[op]} FCC Cost')
    
    if set_limits:
        FUDGE = 0.005 if op is operator.truediv else 0.1e9
        plt.xlim(left=op(df[x_col], baseline[x_col]).min() - FUDGE, right=max(FUDGE, op(df[x_col], baseline[x_col]).max() + FUDGE))
        plt.ylim(bottom=op(df[y_col], baseline[y_col]).min() - FUDGE, top=max(op(df[y_col], baseline[y_col]).max() + FUDGE, FUDGE))
    
    # Add dotted lines
    plt.vlines(1, ax.get_ylim()[0], ax.get_ylim()[1], alpha=0.5, clip_on=False, linestyles='--')
    plt.hlines(1, ax.get_xlim()[0], ax.get_xlim()[1], alpha=0.5, clip_on=False,linestyles='--')
    
    if op == operator.sub:
        ax.xaxis.set_major_formatter(FuncFormatter(lambda x,y : str(x/1e9)))
        ax.yaxis.set_major_formatter(FuncFormatter(lambda x,y : str(x/1e9)))   

#     plt.axis('scaled')
    
def format_unnormalized(set_limits=True):
    ax = plt.gca()
    plt.xlabel('Value Loss (Billions)')
    plt.ylabel('Cost (Billions)')
    plt.ylim(bottom=0)
    plt.xlim(left=0)
    ax.xaxis.set_major_formatter(FuncFormatter(lambda x,y : str(x/1e9)))
    ax.yaxis.set_major_formatter(FuncFormatter(lambda x,y : str(x/1e9)))   
    
def format_figure(df, baseline, normalized, d, op=operator.truediv, limits=None, fixed_legend=False, x_col=EFFICIENCY, y_col=COST):
    if limits is not None:
        if len(limits) != 4:
            raise ValueError("Expected left right top bottom tuple")
        plt.xlim(left=limits[0], right=limits[1])
        plt.ylim(bottom=limits[2], top=limits[3])
    
    if normalized:
        format_normalized(df, baseline, op, set_limits=limits is None)
    else:
        format_unnormalized(set_limits=limits is None)
    
    if limits is None:
        print(plt.gca().get_xlim(), plt.gca().get_ylim())
        
#     plt.legend(loc='best')
    axis = plt.gca()
    handles, labels = axis.get_legend_handles_labels()
    
#     for h, l in zip(handles, labels):
#         handles = mlines.Line2D([], [], color='black', marker='*', linestyle='None',
#                           markersize=10, label='Blue stars')

    
#         c = 'red'
#     #     c = None
#     #     for k in d.keys():
#     #         if d[k]['label'] == labels[0]:
#     #             c = d[k]['color']
#     #             break
#     #     if c is None:
#     #         raise ValueError()

#         handles[0] = mpatches.Patch(color=c)
    
    
    # Sort according to efficiency
    if len(labels) > 1 and not fixed_legend:
        mean_eff = df.groupby(level=0)[EFFICIENCY].mean().sort_values().index.values
        order = []
        for l in mean_eff:
            try:
                order.append(labels.index(l))
            except Exception as e:
                order.append(labels.index(d[l]['label']))
        order = np.array(order)
    else:
#         order = np.array(list(range(len(labels))))
        order = np.argsort(labels)
        print(order)
    
#     if normalized:
#         labels[0] = '$$\textbf{lab}$$'

    
    legend = axis.legend(handles=list(np.array(handles)[order]),labels=list(np.array(labels)[order]), loc='best') 

#     legend = axis.legend(handles=list(np.array(handles)[order]),labels=list(np.array(labels)[order]), loc='best') 
    
def save_fig(name, normalized, op, means_only=False, uhf_only=False, fig=None, model=None, exclude_pre_fcc=False):
    op_to_rep = {operator.truediv: 'relative', operator.sub: 'absolute'}
    fname = name
    if normalized:
        fname += f'_{op_to_rep[op]}'
    if means_only:
        fname += '_means_only'
    if model:
        fname += '_' + model
    if uhf_only:
        fname += '_UHF_ONLY'
    if exclude_pre_fcc:
        fname += '_exclude_pre_FCC'
    special_save_fig(fig, os.path.join(FIGDIR, f'{fname}.pdf'))


def parse_experiment(folders, skip_failures=False, delete_failures=False, count_rounds=False, end_only=True, extra=None, specific_end_stage=None, return_states=False, limit=None, name=None, use_cache=True):
    if use_cache and name is not None and os.path.exists(name):
        return pd.read_csv(name, dtype={'type': str}).set_index(['type', 'auction']).sort_index()
    
    records = []
    states = []
    if count_rounds and end_only:
        raise ValueError("Count rounds only works if end only is set to false")
    for auction_folder in tqdm(folders):
        try:
            basename = os.path.basename(auction_folder)
            seed = int(basename.split('_')[-1])
            auction_type = auction_folder.split('/')[-2]
            is_vcg = 'VCG' in auction_type
            if is_vcg:
                state = VCGState(auction_folder, all_stations=STATIONS, allow_intermediate=True)
            else:
                state = MultiBandAuctionState(auction_folder, end_only=end_only, specific_end_stage=specific_end_stage)
            cost = state.total_cost(exclude_pre_fcc=True)
            value_loss = state.total_value_loss(exclude_pre_fcc=True)
            cost_no_exclude = state.total_cost(exclude_pre_fcc=False)
            value_loss_no_exclude = state.total_value_loss(exclude_pre_fcc=False)
            es = state._ending_state(exclude_pre_fcc=False)

            uhf_data = pd.read_csv('/apps/notebooks/prices.csv').rename(columns={'facility_id': 'station'}).set_index('station')[['p_open', 'population']]
            before_open = state.station_payments(positive_only=True).join(uhf_data).query('price > p_open')
            delta = (before_open['price'] - before_open['p_open']).sum()
            total = state.station_payments(positive_only=True).sum()
            # before_open / total
            fraction = float(delta/total)

            fraction_of_stations_that_freeze_above_clock_given_freeze = len(before_open) / len(state.station_payments(positive_only=True))
            pre_fcc = state.pre_fcc()
            
            record = {
                'auction': seed,
                'type': auction_type,
                'Total Cost': cost,
                'Total Value Loss': value_loss,
                'Total Cost No Exclude': cost_no_exclude,
                'Total Value Loss No Exclude': value_loss_no_exclude,
                'pre_fcc_n_stations': pre_fcc['n'],
                'pre_fcc_sum_pops': pre_fcc['pops'],
                'pre_fcc_stations': pre_fcc['stations'].tolist(),
                'UHF_Only': 'UHF_ONLY' in auction_folder,
                'model': 'pop' if '_POP' in auction_folder else 'ulrich',
                'walltime': state.walltime(),
                'cputime': state.cputime(),
                'Final Stage': 1 if is_vcg else state.ending_stage(),
                'Impairing Stations': state.impairing_stations().tolist(),
                'Mean Overpayment': state.mean_overpayment(),
                'Earliest Freeze': state.earliest_freeze(),
                'Above Open Payment Fraction': fraction, 
                'Frozen Above Open Fraction': fraction_of_stations_that_freeze_above_clock_given_freeze,
                'Top 5 Cost': (es.sort_values('price', ascending=False)['price'].cumsum() / es['price'].sum()).iloc[4],
                'Top 5 Value Loss': (pd.Series(sorted(state.unsummed_value_loss(), reverse=True)).cumsum() / state.total_value_loss()).iloc[4],
            }
            if count_rounds:
                record['n_rounds'] = state.n_rounds()
            if extra is not None:
                for k,v in extra.items():
                    record[k] = v(state)
            records.append(record)
            if return_states:
                states.append(state)
        
        except Exception as e:
            if isinstance(e, KeyboardInterrupt): # Want to be able to use stop button
                raise
            if delete_failures:
                print(f"Deleting {auction_folder}")
                os.system(f'rm -rf {auction_folder}')
            elif skip_failures:
                print(f"Skipping {auction_folder}")
            else:
                raise
                
        if limit is not None and len(records) > limit:
            break
    df = pd.DataFrame.from_records(records)
    df = df.set_index(['type', 'auction'])
    df = df.sort_index()
    
    if name is not None:
        df.to_csv(name)
    if return_states:
        return df, states
    else:
        return df

def make_frames(df, types=None, reference_types=None):
    if reference_types is None:
        reference_types = ['FCC', '29']
    if types is None:
        types = df.index.get_level_values(0).unique().values.tolist()
    else:
        types = list(types)
    q = [t for t in types if t in reference_types]
    if len(q) > 0: # Ensure FCC is always first
        types.remove(q[0])
        types = [q[0]] + types
    return [df.reset_index()[df.reset_index()['type'] == x].sort_values('auction').set_index('auction') for x in types if x in df.reset_index()['type'].unique()]

def make_folders(path, uhf_only=False):
    path += '*/*/*'
    return glob.glob(path)

def standard_pallet(types):
    d = dict()
    for i, t in enumerate(types):
        d[t] = dict(PALLET[i])
    return d

def standard_analysis(d, name, df, types=None, reference_types=None, means=None, means_only=None, limits=None, stages=None, model=False, save=True, fixed_legend=False, exclude_pre_fcc=False, title=False):
    if means is None:
        means = True
    if means_only is None:
        means_only = False
    if stages is None:
        stages = False
        
    if df['Final Stage'].nunique() > 1:
        logging.info(f"Multiple stages detected {df['Final Stage'].unique()}")
        if not stages:
            logging.warning("Not showing the difference between stages!")
        if normalized:
            # Most of the time meaningless because you actually care about AMOUNT cleared which somehow isn't in your stupid JSON
            logging.warning("Potentially you are trying a normalized comparison with different amounts of spectrum!")

    FRAMES = make_frames(df, types=types, reference_types=reference_types)
    if df['UHF_Only'].nunique() > 1 and not name.startswith('vhf'):
        raise ValueError("DF mixes UHF only and non-UHF only")
    uhf_only = df['UHF_Only'].all()
    if df['model'].nunique() > 1:
        raise ValueError(f"DF mixes value models! {df['model'].unique()}")

    idx = FRAMES[0].reset_index()['auction']
    for frame in FRAMES:
        if not np.array_equal(idx, frame.reset_index()['auction']):
            logging.warning("Elements from different treatments are missing. Taking union only!")

            idx = set(FRAMES[0].reset_index()['auction'].values)
            for frame in FRAMES:
                idx &= set(frame.reset_index()['auction'].values)
            logging.warning(f"Idx contains {len(idx)} elements")
            break

    
    if exclude_pre_fcc:
        efficiency_metric = EFFICIENCY
        cost_metric = COST
    else:
        efficiency_metric = EFFICIENCY + ' No Exclude'
        cost_metric = COST + ' No Exclude'
    
    fig = plotting_code(FRAMES, normalized, d, op=op, means_only=means_only, means=means, color_is_stage=stages, x_col=efficiency_metric, y_col=cost_metric)
    format_figure(df, FRAMES[0], normalized, d, op=op, limits=limits, fixed_legend=fixed_legend, x_col=efficiency_metric, y_col=cost_metric) 
    model = df['model'].unique()[0]
    if title:
        plt.title(model + '_' + name + ('_VHF' if not uhf_only else '') + ('_impairments' if exclude_pre_fcc else ''))
    if save:
#         plt.legend(loc='upper left')
        save_fig(name, normalized, op, means_only=means_only, uhf_only=uhf_only, fig=fig, model=model, exclude_pre_fcc=exclude_pre_fcc)
    
def dual_standard_analysis(d, name, df, types=None, reference_types=None, means=None, means_only=None, limits=None, auto_limits=True, save=True, fixed_legend=False, exclude_pre_fcc=False, title=False):
    display(df.reset_index().groupby('model')['type'].value_counts().to_frame())
    cpu_duration = humanize.naturaldelta(dt.timedelta(seconds=df['cputime'].sum()))
    wall_duration = humanize.naturaldelta(dt.timedelta(seconds=df['walltime'].sum()))
    print(f'Took {cpu_duration} CPU time')
    print(f'Took {wall_duration} wall time')
    
    if exclude_pre_fcc:
        for model in ['pop', 'ulrich']:
            for UHF_Only in [True, False]:
                print(model, 'UHF_Only' if UHF_Only else 'With VHF')
                s = df.query(f'model == "{model}" and UHF_Only == {UHF_Only}')
                if not s.empty:
                    if not s['pre_fcc_n_stations'].sum() == 0:
#                         print("N_STATIONS")
#                         display(s.groupby('type')['pre_fcc_n_stations'].describe().drop('count', axis=1))
#                         print("SUM POPS")
                        with pd.option_context('display.precision', 2):
                            display(s.groupby('type')['pre_fcc_sum_pops'].describe().drop('count', axis=1))

    
    if auto_limits and limits is None:
        limits = limits_from_frame(df, reference_type=reference_types[0] if reference_types is not None else types[0] if types is not None else 'FCC', exclude_pre_fcc=exclude_pre_fcc)
    for model, gdf in df.groupby('model'):
        print(f"Model {model}")
        uhf_only = gdf.loc[gdf['UHF_Only'] == True]
        not_uhf_only = gdf.loc[gdf['UHF_Only'] == False]
        kwargs = dict(types=types, reference_types=reference_types, means=means, means_only=means_only, limits=limits, model=model, save=save, fixed_legend=fixed_legend, exclude_pre_fcc=exclude_pre_fcc, title=title)
        if len(uhf_only) > 0:
            print(f"UHF ONLY")
            standard_analysis(d, name, uhf_only, **kwargs)
        if len(not_uhf_only) > 0:
            print(f"UHF+VHF")
            standard_analysis(d, name, not_uhf_only, **kwargs)


def same_index(df):
    master_set = None
    for _, sub_df in df.reset_index().groupby('type'):
        a = set(sub_df['auction'].unique().tolist())
        if master_set is None:
            master_set = a
        else:
            master_set &= a 
    return df.query('auction in @master_set')
