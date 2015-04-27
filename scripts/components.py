# Takes in a TAE csv file with a separate entry for every component and stiches together an output CSV that combines all of the results
import csv
import argparse
import re
from collections import defaultdict

def merge_results(results):
	return "UNSAT" if any([result == "UNSAT" for result in results]) else "TIMEOUT" if any([result == "TIMEOUT" for result in results]) else "SAT"

parser = argparse.ArgumentParser()
parser.add_argument('-csv', type=str, required=True)
parser.add_argument('-output', type=str, required=True)
args = parser.parse_args()


srpk_to_rows = defaultdict(list)

with open(args.csv, 'r') as data:
	reader = csv.reader(data)
	for i, row in enumerate(reader):
		if i == 0:
			header = row
		else: 
			name = row[4]
			if not "component" in name:
				continue
			srpk_name = re.findall(r'(.*)_component.*', name)[0]
			srpk_to_rows[srpk_name].append(row)

#parallel = []
sum_of_runtimes = []
java_exec = []

for srpk, srpk_rows in srpk_to_rows.items():
	srpk_rows.sort(key = lambda row : int(re.findall(r'.*_component(.*)', row[4])[0])) #sort by component number
	# 4 is name, 5 is result, 6 is runtime
	runtimes = [float(srpk_row[6]) for srpk_row in srpk_rows]
	results = [srpk_row[5] for srpk_row in srpk_rows]
	unsat_runtimes = [float(srpk_row[6]) for srpk_row in srpk_rows if srpk_rows[5] == "UNSAT"]
	total_runtime = sum(runtimes)
	result = merge_results(results)
	
	#parallel_row = list(srpk_rows[0])
	#parallel_row[0] = args.output + "_parallel"
	#parallel_row[4] = srpk 
	#parallel_row[5] = result
	#parallel_row[6] = max(runtimes) if (result == "SAT" or result == "TIMEOUT") else min(runtimes) 
	#parallel.append(parallel_row)

	java_exec_row = list(srpk_rows[0])
	java_exec_row[0] = args.output + "_java_exec"
	java_exec_row[4] = srpk 
	java_exec_runtime = 0.0
	for r in srpk_rows:
		java_exec_runtime += float(r[6])
		if r[5] == "UNSAT":
			break
				
	java_exec_row[5] = result
	java_exec_row[6] = java_exec_runtime
	java_exec.append(java_exec_row)

#with open(args.output + "_parallel.csv", 'w') as out:
#	writer = csv.writer(out)
#	writer.writerows([header])
#	writer.writerows(parallel)
with open(args.output + "_java_exec.csv", 'w') as out:
	writer = csv.writer(out)
	writer.writerows([header])
	writer.writerows(java_exec)
