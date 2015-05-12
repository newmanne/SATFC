# coding: utf-8
import redis
import argparse
import queue_stats

def load_queue(r, queue_name, file_name, log=True):
	if log:
		print "BEFORE"
		queue_stats.metrics(r, args.qname)
	with open(file_name, 'r') as srpkfile:
	    lines = [line.strip() for line in srpkfile.readlines()]
	    r.rpush(queue_name, *lines)
	if log:
		print "AFTER"
		queue_stats.metrics(r, args.qname)	

if __name__ == '__main__':
	# Load up a redis queue with jobs
	parser = argparse.ArgumentParser()
	parser.add_argument('--qname', type=str, help="redis queue to store jobs in")
	parser.add_argument('--host', type=str, help="redis host")
	parser.add_argument('--srpkfile', type=str, help="file with one srpk per line")
	parser.add_argument('--port', type=int, help="redis port")
	args = parser.parse_args()

	r = redis.StrictRedis(host=args.host, port=args.port)
	load_queue(r, args.qname, args.srpkfile)