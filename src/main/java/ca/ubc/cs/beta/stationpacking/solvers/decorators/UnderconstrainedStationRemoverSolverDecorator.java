package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.utils.Watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

/**
 * Solver decorator that removes underconstrained stations from the instance, solve the sub-instance and then add back
 * the underconstrained stations using simply looping over all the station's domain channels.
 * 
 * A station is underconstrained if no matter what channels the other stations are assigned to, the station will always have a feasible
 * channel to be packed on.
 * 
 * We "approximately" find underconstrained stations by limiting to stations that have more domain channels than sum of stations that CO- or ADJ+1-
 * interfering with them (double counting stations that interferes the two ways).
 * 
 * @author afrechet
 */
public class UnderconstrainedStationRemoverSolverDecorator extends ASolverDecorator {

	private final Logger log = LoggerFactory.getLogger(UnderconstrainedStationRemoverSolverDecorator.class);
	
	private final IConstraintManager fConstraintManager;
	
	public UnderconstrainedStationRemoverSolverDecorator(ISolver aSolver, IConstraintManager aConstraintManager) {
		super(aSolver);
		fConstraintManager = aConstraintManager;
	}
	
	@Override
	public SolverResult solve(StationPackingInstance aInstance,
			ITerminationCriterion aTerminationCriterion, long aSeed) {
		
		Watch watch = new Watch();
		watch.start();
		
		//Find  the under constrained nodes in the instance.
		final Set<Station> underconstrainedStations = new HashSet<Station>();
		final Map<Station,Set<Integer>> domains = aInstance.getDomains();
		
		log.debug("Finding underconstrained stations in the instance...");
		
		final Map<Station,Integer> numCoNeighbours = new HashMap<Station,Integer>();
		final Map<Station,Integer> numAdjNeighbours = new HashMap<Station,Integer>();
		
		for(Station station : domains.keySet())
		{
			final Set<Integer> domain = domains.get(station); 
			for(Integer domainChannel : domain)
			{
				for(Station coNeighbour : fConstraintManager.getCOInterferingStations(station,domainChannel))
				{
					if(domains.keySet().contains(coNeighbour) && domains.get(coNeighbour).contains(domainChannel))
					{
						Integer stationNumCo = numCoNeighbours.get(station);
						if(stationNumCo == null)
						{
							stationNumCo = 0;
						}
						stationNumCo++;
						numCoNeighbours.put(station, stationNumCo);
						
						Integer neighbourNumCo = numCoNeighbours.get(coNeighbour);
						if(neighbourNumCo == null)
						{
							neighbourNumCo = 0;
						}
						neighbourNumCo++;
						numCoNeighbours.put(coNeighbour, neighbourNumCo);
					}
				}
				for(Station adjNeighbour : fConstraintManager.getADJplusInterferingStations(station, domainChannel))
				{
					if(domains.keySet().contains(adjNeighbour) && domains.get(adjNeighbour).contains(domainChannel+1))
					{
						Integer stationNumAdj = numAdjNeighbours.get(station);
						if(stationNumAdj == null)
						{
							stationNumAdj = 0;
						}
						stationNumAdj++;
						numAdjNeighbours.put(station, stationNumAdj);
					}
				}
			}
		}
		
		for(Station station : domains.keySet())
		{
			final Set<Integer> domain = domains.get(station);
			
			final int domainSize = domain.size();
			final int numCo = numCoNeighbours.containsKey(station) ? numCoNeighbours.get(station) : 0;
			final int numAdj = numAdjNeighbours.containsKey(station) ? numAdjNeighbours.get(station) : 0;
			
			if(domainSize > numCo + numAdj)
			{
				log.trace("Station {} is underconstrained ({} domain but {} co- and {} adj-neighbours), removing it from the instance (to be rea-dded after solving).",station,domainSize,numCo,numAdj);
				underconstrainedStations.add(station);
			}
		}
		
		
		log.debug("Removing {} underconstrained stations...",underconstrainedStations.size());
		
		//Remove the nodes from the instance.
		Map<Station,Set<Integer>> alteredDomains = new HashMap<Station,Set<Integer>>(domains);
		alteredDomains = Maps.filterKeys(alteredDomains, new Predicate<Station>(){
			@Override
			public boolean apply(Station arg0) {
				return !underconstrainedStations.contains(arg0);
			}});

		final SolverResult subResult;
		final double preTime;
		if(!alteredDomains.isEmpty())
		{
			//Solve the reduced instance.
			log.debug("Solving the sub-instance...");
			StationPackingInstance alteredInstance = new StationPackingInstance(alteredDomains, aInstance.getPreviousAssignment());
			watch.stop();
			preTime = watch.getElapsedTime();
			log.trace("{} s spent on underconstrained pre-solving setup.",preTime);
			subResult = fDecoratedSolver.solve(alteredInstance, aTerminationCriterion, aSeed);
		}
		else
		{
			log.debug("All stations were underconstrained!");
			preTime = watch.getElapsedTime();
			log.trace("{} s spent on underconstrained pre-solving setup.",preTime);
			subResult = new SolverResult(SATResult.SAT, 0.0,new HashMap<Integer,Set<Station>>());
		}
		watch.start();
		
		if(subResult.getResult().equals(SATResult.SAT))
		{
			log.debug("Sub-instance is packable, adding back the underconstrained stations...");
			//If satisfiable, find a channel for the under constrained nodes that were removed by brute force through their domain.
			final Map<Integer,Set<Station>> assignment = subResult.getAssignment();
			final Map<Integer,Set<Station>> alteredAssignment = new HashMap<Integer,Set<Station>>(assignment);
			
			for(Station station : underconstrainedStations)
			{
				
				boolean stationAdded = false;
				
				Set<Integer> domain = domains.get(station);
				//Try to add the underconstrained station at one of its channel domain.
				log.trace("Trying to add back underconstrained station {} on its domain {} ...",station,domain);
				
				for(Integer channel : domain)
				{
					log.trace("Checking domain channel {} ...",channel);
					if(!alteredAssignment.containsKey(channel))
					{
						alteredAssignment.put(channel, new HashSet<Station>());
					}
					
					alteredAssignment.get(channel).add(station);
					final boolean addedSAT = fConstraintManager.isSatisfyingAssignment(alteredAssignment);
					
					if(addedSAT)
					{
						log.trace("Added on channel {}.",channel);
						stationAdded = true;
						break;
					}
					else
					{
						alteredAssignment.get(channel).remove(station);
						if(alteredAssignment.get(channel).isEmpty())
						{
							alteredAssignment.remove(channel);
						}
					}
				}
				if(!stationAdded)
				{
					throw new IllegalStateException("Could not add unconstrained station "+station+" on any of its domain channels.");
				}
			}
			watch.stop();
			final double postTime = watch.getElapsedTime();
			log.trace("{} s spent on underconstrained post-solving wrap up.",postTime);
			return new SolverResult(SATResult.SAT, subResult.getRuntime() + preTime + postTime, alteredAssignment);
			
		}
		else
		{
			log.debug("Sub-instance was not satisfiable, no need to consider adding back underconstrained stations.");
			//Not satisfiable, so re-adding the underconstrained nodes cannot change anything.
			watch.stop();
			final double postTime = watch.getElapsedTime();
			log.trace("{} s spent on underconstrained post-solving wrap up.",postTime);
			return SolverResult.addTime(subResult, preTime + postTime);
		}
	}

}
