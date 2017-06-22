import pandas as pd
import numpy as np
import os
import argparse

# TODO: cross reference constraints 46U
# TODO: upload open benchmarkPrices

def stations_from_constraint_set(UHF=True):
	stations = []
	INTERFERENCE = '/ubc/cs/research/arrow/satfc/instances/interference-data/032416SC46U'
	DOMAINS = os.path.join(INTERFERENCE, 'Domain.csv')
	with open(DOMAINS, 'r') as f:
		lines = [line.strip() for line in f.readlines()]
		for line in lines:
			splits = map(int, line.split(',')[1:])
			station = splits[0]
			uhf = any(c >= 14 for c in splits[1:])
			if (not UHF) or (UHF and uhf):
				stations.append(station)
	return stations

def main():
	BASEDIR = '/ubc/cs/research/arrow/satfc/simulator/data'
	parser = argparse.ArgumentParser()
	parser.add_argument('--values', type=str, help="values csv file columns FacID,Value (in mils)", default='values.csv')
	parser.add_argument('--output', type=str, help='name of output file', required=True)
	parser.add_argument('--UHF', type=bool, help='UHF Only', default=True)
	args = parser.parse_args()

	value_file = args.values
	output_file = args.output
	if args.UHF:
		print "Limiting to UHF"

	os.chdir(BASEDIR)

	stations = stations_from_constraint_set(UHF=args.UHF)
	print "Constraints mention %d stations" % (len(stations))

	parameters_df = pd.read_csv('parameters_modified.csv')
	parameters_df = parameters_df[parameters_df['Call'].notnull()]
	parameters_df = parameters_df[parameters_df['FacID'].isin(stations)]
	print "Found info on %d stations in the info file" % len(parameters_df)

	BAD_STATES = ['PR', 'VI']
	parameters_df = parameters_df[~parameters_df['St'].isin(BAD_STATES)]
	print "Removing states %s: %d stations" % (BAD_STATES, len(parameters_df))
		
	columns = ['FacID', 'Call', 'Country', 'Ch']
	parameters_df = parameters_df[columns]

	print "Breakdown by country:", parameters_df['Country'].value_counts().to_dict()

	values_df = pd.read_csv(value_file)
	values_df = values_df[['FacID', 'Value']]
	n_read = len(values_df)
	values_df = values_df[values_df['FacID'].isin(stations)]
	print "Read values for %d stations, %d of these stations are known" % (n_read, len(values_df))

	open_prices_df = pd.read_csv('open_prices.csv')
	open_prices_df = open_prices_df.rename(columns={'Facility ID':'FacID'})
	open_prices_df = open_prices_df[open_prices_df['FacID'].isin(stations)]
	open_prices_df = open_prices_df[open_prices_df['Go Off-Air'] != 'Not Needed']
	open_prices_df = open_prices_df[~open_prices_df['DMA'].isin(['Virgin Islands', 'Puerto Rico'])]
	print "Found %d stations with opening benchmarkPrices" % len(open_prices_df)

	values_df = values_df[values_df['FacID'].isin(open_prices_df['FacID'])]
	combined_df = pd.merge(parameters_df, values_df, on='FacID', how='left')
	print "Matched values to %d stations" % len(combined_df['Value'].dropna())
	
	combined_df = combined_df[(combined_df['Country'] == 'CA') | (~combined_df['Value'].isnull())]
	print "%d stations after dropping US stations without values (either we have no value, or they are known to be unconstrained" % len(combined_df)

	# Sanity check - all US stations have values
	assert combined_df[~combined_df['Value'].isnull()]['Country'].unique() == ['US']

	volumes_df = open_prices_df[['FacID', 'Scaled Volume']].rename(columns={'Scaled Volume':'Volume'})
	print "Read volumes for %d stations" % len(volumes_df)
	combined_df = pd.merge(combined_df, volumes_df, on='FacID', how='left')
	print "Matched volumes to %d stations" % (len(combined_df['Volume'].dropna()))

	combined_df = combined_df.rename(columns={'volume':'Volume','Ch':'Channel', 'value':'Value'})
	combined_df['Channel'] = combined_df['Channel'].astype(np.int32)
	combined_df['Volume'] = combined_df['Volume'].astype(np.float64)
	combined_df.to_csv(output_file, index=False)

	print combined_df['Country'].value_counts().to_dict()

if __name__ == '__main__':
	main()
