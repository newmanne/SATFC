# Use this script to go from SATFC metrics output to a TAE CSV style file

import json
import csv
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('-metrics', type=str, required=True)
parser.add_argument('-taename', type=str, required=True)
parser.add_argument('-output', type=str, required=True)
args = parser.parse_args()
f = open(args.metrics, 'r')
rows = []
for line in f.readlines():
    obj = json.loads(line)
    row = {'TAE Name' : args.taename, 'TAE Configuration': 'DEFAULT', 'Cutoff': '60.0', 'Seed': '-1', 'Additional Run Data': ''}
    row['Instance'] = obj['name'].replace('.srpk', '')
    row['Run Result'] = obj['result']
    row['Run Time'] = obj['runtime']
    rows.append(row)
f.close()

with open(args.output, 'w') as csvfile:
	fieldnames = ['TAE Name','TAE Configuration','Cutoff','Seed','Instance','Run Result','Run Time','Additional Run Data']
	writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
	writer.writeheader()
	writer.writerows(rows)
