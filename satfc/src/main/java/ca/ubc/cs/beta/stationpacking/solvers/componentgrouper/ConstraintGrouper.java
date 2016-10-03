/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 *
 * This file is part of SATFC.
 *
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 *
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.componentgrouper;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import net.jcip.annotations.ThreadSafe;

/**
 * Groups the stations in a station packing instance based on connected components in interference constraint graph.
 * @author afrechet
 */
@ThreadSafe
public class ConstraintGrouper implements IComponentGrouper {
	
	@Override
	public Set<Set<Station>> group(StationPackingInstance aInstance, IConstraintManager aConstraintManager){
		final SimpleGraph<Station,DefaultEdge> aConstraintGraph = getConstraintGraph(aInstance.getDomains(), aConstraintManager);
        final ConnectivityInspector<Station, DefaultEdge> aConnectivityInspector = new ConnectivityInspector<>(aConstraintGraph);
        return aConnectivityInspector.connectedSets().stream().collect(Collectors.toSet());
	}

	public static SimpleGraph<Station,DefaultEdge> getConstraintGraph(Map<Station, Set<Integer>> aDomains, IConstraintManager aConstraintManager) {
		return getConstraintGraph(aDomains, aConstraintManager, aDomains.keySet().stream().collect(Collectors.toMap(s -> s, s -> s)));
	}

	/**
	 * @param aConstraintManager - the constraint manager to use to form edges of the constraint graph.
	 * @return the constraint graph.
	 */
	public static <K> SimpleGraph<K,DefaultEdge> getConstraintGraph(Map<Station, Set<Integer>> aDomains, IConstraintManager aConstraintManager, Map<Station, K> stationToOther)
	{
		final Set<Station> aStations = aDomains.keySet();
		final SimpleGraph<K,DefaultEdge> aConstraintGraph = new SimpleGraph<>(DefaultEdge.class);
        for(Station aStation : aStations){
            aConstraintGraph.addVertex(stationToOther.get(aStation));
        }
        aConstraintManager.getAllRelevantConstraints(aDomains).forEach(constraint -> {
            aConstraintGraph.addEdge(stationToOther.get(constraint.getSource()), stationToOther.get(constraint.getTarget()));
        });
		return aConstraintGraph;
	}


}

