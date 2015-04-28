# coding: utf-8
import redis
import argparse

# Load up a redis queue with jobs

parser = argparse.ArgumentParser()
parser.add_argument('--qname', type=str, help="redis queue to delete")
parser.add_argument('--host', type=str, help="redis host")
parser.add_argument('--port', type=int, help="redis port")
args = parser.parse_args()

r = redis.StrictRedis(host=args.host, port=args.port)
r.delete(args.qname)
r.delete(args.qname+'_PROCESSING')
