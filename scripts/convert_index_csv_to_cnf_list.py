def writeFile(fname, cnfs):
	with open(fname, 'w') as out:
		for cnf in cnfs:
			out.write(cnf + '\n')

component_cnfs = set()
full_instance_cnfs = set()
presat_cnfs = set()
with open('2909_CNF_Index.csv', 'r') as index:
    lines = [line.strip() for line in index.readlines()]
    for line in lines:
        srpk, cnf = line.split(',')
        if 'component' in srpk:
            component_cnfs.add(cnf.replace('cnfs/',''))
        elif 'StationSubsetSATCertifier' in srpk:
        	presat_cnfs.add(cnf.replace('cnfs/',''))
        else:
        	full_instance_cnfs.add(cnf.replace('cnfs/',''))

writeFile('2909_Full_Instance_CNFs', full_instance_cnfs)
writeFile('2909_Component_CNFs', component_cnfs)
writeFile('2909_PreSAT_CNFs', presat_cnfs)