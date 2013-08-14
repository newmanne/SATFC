package ca.ubc.cs.beta.stationpacking.daemon.simple.server;

import java.net.SocketException;
import java.net.UnknownHostException;

import ca.ubc.cs.beta.stationpacking.daemon.simple.datamanager.SATBasedSolverFactory;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.NoGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.sat.solvers.ClaspSATSolver;

public class DaemonTest {

	public static void main(String[] args) {
		
		SolverServer aSolverServer;
		try {
			String aAlexLibPath = "/ubc/cs/project/arrow/afrechet/git/FCCStationPacking/SATsolvers/clasp/jna/libjnaclasp.so";
			String aSantaLibPath = "/home/gsauln/workspace/FCC-Station-Packing/SATsolvers/clasp/jna/libjnaclasp.so";
			ClaspSATSolver clasp = new ClaspSATSolver(aSantaLibPath, "--eq=0 --trans-ext=all --sat-prepro=0 --sign-def=2 --del-max=10000 --strengthen=local,1 --del-init-r=800,20000 --loops=no --init-watches=0 --heuristic=Berkmin --del-cfl=F,100 --restarts=L,256 --del-algo=basic,0 --deletion=3,66,3.0 --berk-max=256 --del-grow=1.0,100.0,F,128 --update-act --del-glue=4,0 --update-lbd=2 --reverse-arcs=3 --otfs=0 --berk-huang --del-on-restart=50 --contraction=120 --counter-restarts=3 --local-restarts --lookahead=no --save-progress=10 --counter-bump=180");
			SATBasedSolverFactory factory = new SATBasedSolverFactory(clasp, new NoGrouper());
			
			aSolverServer = new SolverServer(49149, factory);
		} catch (SocketException | UnknownHostException e) {
			throw new IllegalArgumentException("Could not establish connection ("+e.getMessage()+").");
		}
		aSolverServer.start();
		
	}

}
