import sys


def printInstance(S):

    S = S.replace('\n','')

    parts = S.split('_')

    data_foldername = parts[0]

    domains = {}
    previous_assignment = {}
    for part in parts[1:]:
        stationchannels = part.split(';')

        station = int(stationchannels[0])

        previous_channel = int(stationchannels[1])
        if previous_channel > 0:
            if station in previous_assignment:
                raise Exception('Station %d is already in previous assignment.' % station)
            else:
                previous_assignment[station] = previous_channel

        domain = []
        for channelstring in stationchannels[2].split(','):
            channel = int(channelstring)
            if channel in domain:
                raise Exception("Channel %d is already in station %d's domain." % (channel,station))
            else:
                domain.append(channel)

        if station in domains:
            raise Exception('Station %d is already present in domains.' % station)
        else:
            domains[station] = domain

    print data_foldername
    print ';'.join(['%d:%s' % (station,','.join([str(c) for c in domains[station]])) for station in domains])

        

if len(sys.argv) > 1:
    for arg in sys.argv[1:]:
        printInstance(arg)
else:
    for line in sys.stdin:
        printInstance(line)

