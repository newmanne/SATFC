package ca.ubc.cs.beta.stationpacking.solvers.underconstrained;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Sets;

import lombok.extern.slf4j.Slf4j;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;

/**
 * Created by newmanne on 1/8/15.
 */
@Slf4j
public class UnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    private final IConstraintManager fConstraintManager;

    public UnderconstrainedStationFinder(IConstraintManager aConstraintManger) {
        fConstraintManager = aConstraintManger;
    }


    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains) {
        //Find  the under constrained nodes in the instance.
        final Set<Station> underconstrainedStations = new HashSet<Station>();

        log.debug("Finding underconstrained stations in the instance...");

        final Map<Station,Set<Integer>> badChannels = new HashMap<Station,Set<Integer>>();
        for(final Entry<Station, Set<Integer>> domainEntry : domains.entrySet())
        {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();
            
            for(Integer domainChannel : domain)
            {
                for(Station coNeighbour : fConstraintManager.getCOInterferingStations(station,domainChannel))
                {
                   if(domains.keySet().contains(coNeighbour) && domains.get(coNeighbour).contains(domainChannel))
                   {
                       Set<Integer> stationBadChannels = badChannels.get(station);
                       if(stationBadChannels == null)
                       {
                           stationBadChannels = new HashSet<Integer>();
                       }
                       stationBadChannels.add(domainChannel);
                       badChannels.put(station, stationBadChannels);
                       
                       Set<Integer> coneighbourBadChannels = badChannels.get(coNeighbour);
                       if(coneighbourBadChannels == null)
                       {
                           coneighbourBadChannels = new HashSet<Integer>();
                       }
                       coneighbourBadChannels.add(domainChannel);
                       badChannels.put(coNeighbour, coneighbourBadChannels);
                    }
                 }
                for(Station adjNeighbour : fConstraintManager.getADJplusInterferingStations(station, domainChannel))
                {
                    if(domains.keySet().contains(adjNeighbour) && domains.get(adjNeighbour).contains(domainChannel+1))
                    {
                        Set<Integer> stationBadChannels = badChannels.get(station);
                        if(stationBadChannels == null)
                        {
                            stationBadChannels = new HashSet<Integer>();
                        }
                        stationBadChannels.add(domainChannel);
                        badChannels.put(station, stationBadChannels);
                        
                        Set<Integer> adjneighbourBadChannels = badChannels.get(adjNeighbour);
                        if(adjneighbourBadChannels == null)
                        {
                            adjneighbourBadChannels = new HashSet<Integer>();
                        }
                        adjneighbourBadChannels.add(domainChannel+1);
                        badChannels.put(adjNeighbour, adjneighbourBadChannels);
                      }
                  }
              }
        }
        
        for(final Entry<Station, Set<Integer>> domainEntry : domains.entrySet())
        {
            final Station station = domainEntry.getKey();
            final Set<Integer> domain = domainEntry.getValue();
            
            Set<Integer> stationBadChannels = badChannels.get(station);
            if(stationBadChannels == null)
            {
                stationBadChannels = Collections.emptySet();
            }
            
            final Set<Integer> stationGoodChannels = Sets.difference(domain, stationBadChannels);
            
            log.trace("Station {} domain channels: {}.",station,domain);
            log.trace("Station {} bad channels: {}.",station,stationBadChannels);
            
            if(!stationGoodChannels.isEmpty())
            {
                log.trace("Station {} is underconstrained has it has {} domain channels ({}) on which it interferes with no one.",station,stationGoodChannels.size(),stationGoodChannels);
                underconstrainedStations.add(station);
            }
        }
        
        return underconstrainedStations;
        
        
//        final Map<Station,Integer> numCoNeighbours = new HashMap<Station,Integer>();
//        final Map<Station,Integer> numAdjNeighbours = new HashMap<Station,Integer>();
//
//        for(Station station : domains.keySet())
//        {
//            final Set<Integer> domain = domains.get(station);
//            for(Integer domainChannel : domain)
//            {
//                for(Station coNeighbour : fConstraintManager.getCOInterferingStations(station,domainChannel))
//                {
//                    if(domains.keySet().contains(coNeighbour) && domains.get(coNeighbour).contains(domainChannel))
//                    {
//                        Integer stationNumCo = numCoNeighbours.get(station);
//                        if(stationNumCo == null)
//                        {
//                            stationNumCo = 0;
//                        }
//                        stationNumCo++;
//                        numCoNeighbours.put(station, stationNumCo);
//
//                        Integer neighbourNumCo = numCoNeighbours.get(coNeighbour);
//                        if(neighbourNumCo == null)
//                        {
//                            neighbourNumCo = 0;
//                        }
//                        neighbourNumCo++;
//                        numCoNeighbours.put(coNeighbour, neighbourNumCo);
//                    }
//                }
//                for(Station adjNeighbour : fConstraintManager.getADJplusInterferingStations(station, domainChannel))
//                {
//                    if(domains.keySet().contains(adjNeighbour) && domains.get(adjNeighbour).contains(domainChannel+1))
//                    {
//                        Integer stationNumAdj = numAdjNeighbours.get(station);
//                        if(stationNumAdj == null)
//                        {
//                            stationNumAdj = 0;
//                        }
//                        stationNumAdj++;
//                        numAdjNeighbours.put(station, stationNumAdj);
//                        
//                        Integer neighbourNumAdj = num
//                        
//                    }
//                }
//            }
//        }
//
//        for(Station station : domains.keySet())
//        {
//            final Set<Integer> domain = domains.get(station);
//
//            final int domainSize = domain.size();
//            final int numCo = numCoNeighbours.containsKey(station) ? numCoNeighbours.get(station) : 0;
//            final int numAdj = numAdjNeighbours.containsKey(station) ? numAdjNeighbours.get(station) : 0;
//
//            if(domainSize > numCo + numAdj)
//            {
//                log.trace("Station {} is underconstrained ({} domain ({}) but {} co- and {} adj-neighbours), removing it from the instance (to be rea-dded after solving).",station,domainSize,domain,numCo,numAdj);
//                underconstrainedStations.add(station);
//            }
//        }
//        return underconstrainedStations;
    }

}
