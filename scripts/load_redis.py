# coding: utf-8
import redis
import argparse
import queue_stats

# Load up a redis queue with jobs
parser = argparse.ArgumentParser()
parser.add_argument('--qname', type=str, help="redis queue to store jobs in")
parser.add_argument('--host', type=str, help="redis host")
parser.add_argument('--srpkfile', type=str, help="file with one srpk per line")
parser.add_argument('--port', type=int, help="redis port")
args = parser.parse_args()

r = redis.StrictRedis(host=args.host, port=args.port)
print "BEFORE"
queue_stats.metrics(r, args.qname)
with open(args.srpkfile, 'r') as srpkfile:
    lines = [line.strip() for line in srpkfile.readlines()]
    r.rpush(args.qname, *lines)
print "AFTER"
queue_stats.metrics(r, args.qname)