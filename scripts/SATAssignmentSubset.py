import redis
import json

r = redis.StrictRedis(host='localhost', port=6379, db=0)
# all SAT keys
keys = r.keys('*:SAT*')

# prunable counter
counter = 0

for key in keys:
	print 'Checking key ' +  key
	map = r.get(key)
	j = json.loads(map)
	channelCount = len(j['assignment'])
	for key2 in keys:
		# same key skip, no need to check against itself
		if( key != key2 ):
			map2 = r.get(key2)
			j2 = json.loads(map2)
			channelCount2 = len(j2['assignment'])
			# continue only if first cache has less channels than second cache
			if( channelCount <= channelCount2 ):
				# boolean indicating whether current SAT key is prunable or not
				flag = True
				for channel in j['assignment']:
					s1 = j['assignment'][channel] # stations
					try:
						s2 = j2['assignment'][channel] # corresponding stations of same channel in assignment 2
					except Exception, e:
						flag = False
						print 'Channel ' + channel + ' in assignment 1 does not exist in assignment 2'
						break
					# break if any s1 is not subset of s2
					if(not set(s1).issubset(set(s2))):
						flag = False
						break
				if flag:
					counter += 1
					print key + ' is subset of ' + key2
					break

print str(counter) + ' prunable SAT entries out of ' + str(len(keys)) + ' total SAT entries: ' + str(counter/len(keys)) + '%' 		

			