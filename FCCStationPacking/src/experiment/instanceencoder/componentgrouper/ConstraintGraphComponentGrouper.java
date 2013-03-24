package experiment.instanceencoder.componentgrouper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import data.Constraint;
import data.Station;

public class ConstraintGraphComponentGrouper implements IComponentGrouper{

	private ArrayList<Set<Station>> fComponents;
	
	public ConstraintGraphComponentGrouper(Set<Constraint> aConstraints)
	{
		SimpleGraph<Station,DefaultEdge> aConstraintGraph = new SimpleGraph<Station,DefaultEdge>(DefaultEdge.class);

		for(Constraint aConstraint : aConstraints)
		{
			Station aStation1 = aConstraint.getProtectedPair().getKey();
			Station aStation2 = aConstraint.getInterferingPair().getKey();
			
			if(!aConstraintGraph.containsVertex(aStation1))
			{
				aConstraintGraph.addVertex(aStation1);
			}
			if(!aConstraintGraph.containsVertex(aStation2))
			{
				aConstraintGraph.addVertex(aStation2);
			}
			
			aConstraintGraph.addEdge(aStation1, aStation2);
		}
		
		ConnectivityInspector<Station, DefaultEdge> aConnectivityInspector = new ConnectivityInspector<Station,DefaultEdge>(aConstraintGraph);
		
		fComponents = new ArrayList<Set<Station>>();
		for(Set<Station> aComponent : aConnectivityInspector.connectedSets())
		{
			
			if(aComponent.size()>1)
			{
				fComponents.add(aComponent);
			}
		}

	}
	
	@Override
	public ArrayList<HashSet<Station>> group(Set<Station> aStations) {
		
		ArrayList<HashSet<Station>> aGroups = new ArrayList<HashSet<Station>>(fComponents.size());
		for(int i=0;i<fComponents.size();i++)
		{
			aGroups.add(i,new HashSet<Station>());
		}
		
		for(Station aStation : aStations)
		{
			for(int i=0;i<fComponents.size();i++)
			{
				if(fComponents.get(i).contains(aStation)){
					aGroups.get(i).add(aStation);
					break;
				}
			}
		}
		Iterator<HashSet<Station>> aGroupsIterator = aGroups.iterator();
		while(aGroupsIterator.hasNext())
		{
			if(aGroupsIterator.next().isEmpty())
			{
				aGroupsIterator.remove();
			}
		}
	
		return aGroups;
		
	}
	
	

}
