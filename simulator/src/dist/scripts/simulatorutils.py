from __future__ import print_function

import warnings
warnings.simplefilter(action='ignore', category=FutureWarning)

import pandas as pd
import os
import glob
import json
import humanize
import traceback
import re
import itertools
from pandas import json_normalize
import logging
import numpy as np
idx = pd.IndexSlice


class VCGState(object):

    def __init__(self, folder, all_stations=set(), allow_intermediate=False):
        self.folder = folder

        outcome_file = self._get_outcome_file()
        with open(outcome_file, 'r') as f:
            self.outcome = json.load(f)

        if not self.outcome['status'] == 'Optimal':
            raise ValueError("Allocation is not optimal!")

        self.values_df = pd.read_csv(os.path.join(self.folder, 'values.csv'), index_col='FacID')

        rows = []
        for station, channel in self.outcome['assignment'].items():
            station = int(station)
            value = self.values_df.ix[int(station)]['UHFValue']
            rows.append(dict(FacID=station, payment=0, channel=channel, runtime=0, value=value))


        #Quick sanity check...
        # diff = abs(self.total_value_on_air() - pd.DataFrame.from_records(rows, index='FacID')['value'].sum())
        # if diff > 100:
        #     raise ValueError("DIFFERENCE in %s between objective and assignment of %s" % (self.folder, humanize.intword(diff)))

        incomplete_count = 0
        stations_with_prices = all_stations - set(map(int,list(self.outcome['assignment'].keys())))
        for station in stations_with_prices:
            try:
                x = json.load(open(os.path.join(self.folder, "%d.json" % (station))))
            except:
                incomplete_count += 1
                if allow_intermediate:
                    logging.warning(f'Station {station} is missing from results')
                    continue
                else:
                    raise ValueError(f"Station {station} is missing from results")

            if not x['status'] == 'Optimal':
                raise ValueError("MIP for station %d not optimal!" % (station))

            objVal = float(x['objectiveValue']) * 1e3
            value = self.values_df.ix[int(station)]['UHFValue']
            price = objVal - self.total_value_on_air()

            cputime, walltime = self._timing(x, os.path.join(self.folder, "%d.log" % (station)))
            # TODO: When VHF, add values here
            rows.append(dict(FacID=station, payment=-price, channel=-1, walltime=walltime, cputime=cputime, value=value))

        if not allow_intermediate:
            assert len(rows) == len(all_stations)

        if incomplete_count > 0:
            logging.info(f'INCOMPLETE: {len(stations_with_prices) - incomplete_count}/{len(stations_with_prices)}')

        self.vcg_df = pd.DataFrame.from_records(rows, index='FacID')

    def _timing(self, outcome, logfile):
        try:
            cputime = outcome['cputime']
            walltime = outcome['walltime']
        except KeyError:
            with open(logfile, 'r') as logfile:
                logfile_content = logfile.read()
                m = re.search(r'Total \(root\+branch&cut\) = (.*) sec', logfile_content)
                cputime = float(m.group(1))
                m = re.search(r'Runtime was (.*)\n', logfile_content)
                walltime = float(m.group(1))
        return cputime, walltime

    def cputime(self):
        """Here we count the cputime of the initial optimization + prices"""
        return self._timing(self.outcome, os.path.join(self.folder, 'vcg.log'))[0] + self.vcg_df['cputime'].sum()

    def total_value_loss(self):
        method_A = self.vcg_df[self.vcg_df['channel'] == -1]['value'].sum()
        method_B = self.vcg_df['value'].sum() - self.total_value_on_air()
        if abs(method_A - method_B) > 5:
            print(f"Methods differ {method_A} {method_B}")
        return method_A

    def walltime(self):
        """Due to parallelism, how to count this? According to your resources, I suppose"""
        return 42

    def total_value_on_air(self):
        return self.outcome['objectiveValue'] * 1e3

    def total_cost(self):
        return self.vcg_df[self.vcg_df['channel'] == -1]['payment'].sum()

    def _get_outcome_file(self):
        return os.path.join(self.folder, 'vcg.json')

    def summarize(self):
        print("Total value: %s" % (humanize.intword(self.total_value())))
        print("Total cost: %s" % (humanize.intword(self.total_cost())))

    def station_payments(self):
        return self.vcg_df['payment']


PATTERN_STAGE = re.compile('stage_(?P<stage>\d+)_round_(?P<round>\d+)\.json')
PATTERN_STAGE_2 = re.compile('state_(?P<round>\d+)\.json')

class MultiBandAuctionState(object):
    """
      "price": 0,
      "participation": "EXITED_NOT_PARTICIPATING",
      "option": "UHF",
      "vacancies": {},
      "reductionCoefficients": {},
      "offers": {},
      "values": {
    """

    def __init__(self, folder, end_only=False, specific_end_stage=None):
        if specific_end_stage is None:
            specific_end_stage = 99

        na_values = ['Not Needed']
        satfc_root = os.environ.get('SATFC', '/apps/satfc')
        self.open_prices = pd.read_csv(os.path.join(satfc_root, 'simulator/src/dist/simulator_data/actual_data/open_prices.csv'), dtype={'Go Off-Air': np.float64}, na_values=na_values).rename(columns={"Facility ID": "station"}).set_index('station')[['Go Off-Air', 'Interference -Free Population']].rename(columns={'Interference -Free Population': 'Population'})

        self.folder = folder

        state_file_path = os.path.join(self.folder, 'state/*.json')
        self.state_files = glob.glob(state_file_path)
        if len(self.state_files) == 0:
            raise ValueError("No state files found in %s" % state_file_path)

        if end_only:
            terminal_rounds = pd.DataFrame.from_records(map(self._name_to_round, self.state_files)).groupby(0)[1].max().reset_index().values.tolist()
            self.state_files = [s for s in self.state_files if self._name_to_round(s) in terminal_rounds and self._name_to_round(s)[0] <= specific_end_stage]
        frames = []
        global_states = []
        for state_file in self.state_files:
            with open(state_file) as f:
                round_state = json.loads(f.read())

            station_states = list(round_state['state'].values())
            s_ids = list(round_state['state'].keys())
            round_station_df = pd.DataFrame.from_records(json_normalize(station_states))
            stage_number, round_number = self._name_to_round(state_file)
            round_station_df['round'] = round_number
            round_station_df['stage'] = stage_number
            round_station_df['station'] = list(map(int, s_ids))

            frames.append(round_station_df)

            global_states.append(round_state)


        self.df = pd.concat(frames)
        self.df['participation'] = self.df['participation'].astype('category')
        self.df['option'] = self.df['option'].astype('category')
        self.df.set_index(['stage', 'round', 'station'], inplace=True)

        self.global_states = pd.DataFrame.from_records(global_states)

        if end_only:
            ending_state = self._ending_state()
            if ending_state['participation'].value_counts().to_dict().get('BIDDING', 0) != 0:
                raise ValueError("Requested end state, but auction isn't completed!")

    def ending_stage(self):
        return self.df.index.get_level_values(level=0).max()

    def _ending_state(self, exclude_pre_fcc=False):
        max_stage = self.df.index.get_level_values(level=0).max()
        max_round_for_max_stage = self.df.query('stage == %d' % max_stage).index.get_level_values('round').max()
        ending_state = self.df.query('stage == %d and round == %d' % (max_stage, max_round_for_max_stage))
        if exclude_pre_fcc:
            ending_state = ending_state.join(self.open_prices).query('price <= `Go Off-Air`')
        return ending_state

    def impairing_stations(self):
        '''Return stations impairing when the auction ends'''
        df = self._ending_state()
        impairing_stations = df.query('impaired').reset_index()['station'].values
        return impairing_stations

    def impairing_value(self):
        df = self._ending_state()
        return df.query('impaired')['values.UHF'].sum()

    def pre_fcc(self):
        ending_state = self._ending_state(exclude_pre_fcc=False)
        pre_fcc = ending_state.join(self.open_prices).query('price > `Go Off-Air`')
        n_stations = len(pre_fcc)
        pops = pre_fcc['Population'].sum()
        return {
            'n': n_stations,
            'pops': pops,
            'stations': pre_fcc.reset_index()['station'].unique(),
        }

    def _name_to_round(self, f):
        "Convert a filename for a state json into a (stage, round) number"
        #stage_1_round_24.json
        try:
            return list(map(int, PATTERN_STAGE.match(os.path.basename(f)).groups()))
        except:
            # Legacy single-stage
            return (1, int(PATTERN_STAGE_2.match(os.path.basename(f)).groups()[0]))
        # return int(os.path.basename(f).replace('.json', '').split('_')[1])

    def clearing_cost_by_stage(self):
        return self.df.reset_index().groupby('stage').apply(lambda grp: grp[grp['round'] == grp['round'].max()]).groupby('stage')['price'].sum().to_frame('Clearing Cost')

    def total_cost(self, exclude_pre_fcc=False):
        ending_state = self._ending_state(exclude_pre_fcc=exclude_pre_fcc)
        return ending_state['price'].sum()

    def total_value_on_air(self):
        raise
        # # Actually think this through...
        # end_state = self._ending_state()
        # return end_state['values.UHF'].sum() - self.total_value_of_winners()

    def participation(self):
        """Return the number of participating stations"""
        end_state = self._ending_state()
        return len(end_state[end_state['participation'] != 'EXITED_NOT_PARTICIPATING'])

    def unsummed_value_loss(self, exclude_pre_fcc=False):
        """Accounts for all value loss, even moving bands"""
        end_state = self._ending_state(exclude_pre_fcc=exclude_pre_fcc)
        winners = end_state[end_state['price'] > 0]
        final_band_value_col = winners['option'].apply(lambda x: 'values.' + str(x))
        final_band_values = winners.lookup(final_band_value_col.index, final_band_value_col.values)
        home_bands = winners[['values.HVHF', 'values.LVHF', 'values.UHF']].idxmax(axis=1)
        home_band_values = winners.lookup(home_bands.index, home_bands.values)
        value_loss = (home_band_values - final_band_values)
        return value_loss

    def total_value_loss(self, exclude_pre_fcc=False):
        return self.unsummed_value_loss(exclude_pre_fcc=exclude_pre_fcc).sum()

    def owner_profit(self):
        """Payment - Value Loss"""
        end_state = self._ending_state()
        participating = end_state
        #participating = end_state[end_state['participation'] != 'EXITED_NOT_PARTICIPATING']
        final_band_value_col = participating['option'].apply(lambda x: 'values.' + str(x))
        final_band_values = pd.Series(index=final_band_value_col.index, data=participating.lookup(final_band_value_col.index, final_band_value_col.values))

        home_bands = participating[['values.HVHF', 'values.LVHF', 'values.UHF']].idxmax(axis=1)
        home_band_values = pd.Series(index=home_bands.index, data=participating.lookup(home_bands.index, home_bands.values))
        band_loss = (home_band_values - final_band_values)
        band_loss.index = band_loss.index.droplevel().droplevel()

        payments = self.station_payments()['price']

        retval = payments - band_loss.reindex(payments.index, fill_value=0)
        if any(retval < 0):
            raise ValueError("Negative profits?")
        return retval

    def owner_profit_uhf(self):
        end_state = self._ending_state()
        participating = end_state
        final_band_value_col = participating['option'].apply(lambda x: 'values.' + str(x))
        final_band_values = pd.Series(index=final_band_value_col.index, data=participating.lookup(final_band_value_col.index, final_band_value_col.values))
        final_band_values = final_band_values.reset_index().drop(['stage', 'round'], axis=1).set_index('station')[0]

        hb = participating.reset_index().set_index('station')['values.UHF']
        band_loss = hb - final_band_values

        payments = self.station_payments()['price']
        retval = payments - band_loss
        return retval

    def owner_utility(self):
        """Profit if you sold the station, value if you get to keep it"""
        end_state = self._ending_state()
        final_band_value_col = end_state['option'].apply(lambda x: 'values.' + str(x))
        final_band_values = pd.Series(index=final_band_value_col.index, data=end_state.lookup(final_band_value_col.index, final_band_value_col.values))
        return (self.owner_profit() + final_band_values).to_frame('utility').reset_index().set_index('station').drop(['stage', 'round'], axis=1)

    def walltime(self):
        return self.global_states['uhfproblemWallTime'].sum()

    def cputime(self):
        return self.global_states['uhfproblemCPUTime'].sum()

    def problem_distribution(self):
        return self.state['feasibilityDistribution']

    def timeouts(self):
        raise ValueError()

    def summarize(self):
        print(self.folder)
        print("Total value on air: %s" % (humanize.intword(self.total_value_on_air())))
        print("Total cost: %s" % (humanize.intword(self.total_cost())))

    def station_payments(self, positive_only=False):
        payments = self._ending_state()['price'].reset_index().drop(['stage', 'round'], axis=1).set_index('station')
        if positive_only:
            return payments[payments['price'] > 0]
        else:
            return payments

    def station_hb_values(self):
        v = self._ending_state()['values.UHF'].reset_index().drop(['stage', 'round'], axis=1).set_index('station').drop(self.station_payments(positive_only=True).index)
        return v
        #home_band_values = pd.Series(index=home_bands.index, data=end_state.lookup(home_bands.index, home_bands.values))
        #return home_band_values


    def winners(self):
        '''For UHF-only, this gives the stations that go off-air. Otherwise, more complicated since winners might just switch bands'''
        return self.station_payments(positive_only=True).index.values

    def n_rounds(self):
        return self.df.reset_index().groupby('stage')['round'].max().sum()
#         return self.df.reset_index()[['stage', 'round']].drop_duplicates().count().iloc[0] - 1 # Account for 0th round

    def mean_overpayment(self):
        end_state = self._ending_state()
        winners = end_state[end_state['price'] > 0]
        final_band_value_col = winners['option'].apply(lambda x: 'values.' + str(x))
        final_band_values = winners.lookup(final_band_value_col.index, final_band_value_col.values)
        home_bands = winners[['values.HVHF', 'values.LVHF', 'values.UHF']].idxmax(axis=1)
        home_band_values = winners.lookup(home_bands.index, home_bands.values)
        value_loss = (home_band_values - final_band_values)
        payments = winners['price']
        return (payments / value_loss).mean()

    def earliest_freeze(self):
        # TODO: Not quite right for multi-stage
        return self.df.query('price > 0')['catchupPoint.benchmarkPrices.OFF'].max()(base)
