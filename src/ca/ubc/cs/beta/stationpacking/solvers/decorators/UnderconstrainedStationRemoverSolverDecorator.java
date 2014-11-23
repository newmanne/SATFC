package ca.ubc.cs.beta.stationpacking.solvers.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.solvers.base.SATResult;
import ca.ubc.cs.beta.stationpacking.solvers.base.SolverResult;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

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
		
		//Find  the under constrained nodes in the instance.
		final Set<Station> underconstrainedStations = new HashSet<Station>();
		final Map<Station,Set<Integer>> domains = aInstance.getDomains();
		
		log.debug("Finding underconstrained stations in the instance...");
		
		for(Station station : domains.keySet())
		{
			final Set<Integer> domain = domains.get(station); 
			
			final int domainSize = domain.size();
			
			int numCoNeighbours = 0;
			int numAdjNeighbours = 0;
			for(Integer domainChannel : domain)
			{
				for(Station neighbour : fConstraintManager.getCOInterferingStations(station,domainChannel))
				{
					if(domains.keySet().contains(neighbour) && domains.get(neighbour).contains(domainChannel))
					{
						numCoNeighbours++;
					}
				}
				for(Station neighbour : fConstraintManager.getADJplusInterferingStations(station, domainChannel))
				{
					if(domains.keySet().contains(neighbour) && domains.get(neighbour).contains(domainChannel+1))
					{
						numAdjNeighbours++;
					}
				}
			}
			
			if(domainSize > numCoNeighbours + numAdjNeighbours)
			{
				log.trace("Station {} is underconstrained, removing it from the instance (to be readded after solving).",station);
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
		if(!alteredDomains.isEmpty())
		{
			//Solve the reduced instance.
			log.debug("Solving the sub-instance...");
			final StationPackingInstance alteredInstance = new StationPackingInstance(alteredDomains, aInstance.getPreviousAssignment());
			subResult = fDecoratedSolver.solve(alteredInstance, aTerminationCriterion, aSeed);
		}
		else
		{
			log.debug("All stations were underconstrained!");
			subResult = new SolverResult(SATResult.SAT, 0.0,new HashMap<Integer,Set<Station>>());
		}
		
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
				for(Integer channel : domain)
				{
					if(!alteredAssignment.containsKey(channel))
					{
						alteredAssignment.put(channel, new HashSet<Station>());
					}
					
					alteredAssignment.get(channel).add(station);
					final boolean addedSAT = fConstraintManager.isSatisfyingAssignment(alteredAssignment);
					
					if(addedSAT)
					{
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
					if(!stationAdded)
					{
						throw new IllegalStateException("Could not add unconstrained station "+station+" on any of its domain channels.");
					}
				}
			}
			
			return new SolverResult(SATResult.SAT, subResult.getRuntime(), alteredAssignment);
			
		}
		else
		{
			log.debug("Sub-instance was not satisfiable, no need to consider adding back underconstrained stations.");
			//Not satisfiable, so re-adding the underconstrained nodes cannot change anything.
			return subResult;
		}
	}

}
