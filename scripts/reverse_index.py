from collections import defaultdict
import json
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--index_csv', type=str, help="csv index")
args = parser.parse_args()

cnf_to_srpk_index = defaultdict(list)
cnfs = []
preSAT_cnfs = []

with open(args.index_csv, 'r') as index_csv:
	lines = [line.strip() for line in index_csv.readlines()]
	for line in lines:
		parts = line.split(',')
		srpk = parts[0]
		cnf = parts[1]
		cnf_to_srpk_index[cnf].append(srpk)
with open('2909_CNF_to_srpk.json', 'w') as reverse_index:
	json.dump(cnf_to_srpk_index, reverse_index)