/**
 * Copyright 2015, Auctionomics, Alexandre FrÃ©chette, Neil Newman, Kevin Leyton-Brown.
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
package ca.ubc.cs.beta.stationpacking.solvers.underconstrained;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.solvers.componentgrouper.ConstraintGrouper;
import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.NullOutputStream;
import org.jgrapht.alg.NeighborIndex;
import org.jgrapht.graph.DefaultEdge;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by newmanne on 1/8/15.
 * <p/>
 * The goal is to find stations for which, no matter how their neighbours are
 * arranged, there will always be a channel to put them onto. Then, you can
 * remove them from the problem, and simply add them back afterwards by
 * iterating through their domain until you find a satisfying assignment.
 * <p/>
 * One helpful framework for thinking about this problem is to think of the
 * question as: If all of my neighbours were placed adversarially to block out
 * the maximum number of channels from my domain, how many could they block out?
 * If the answer is less than my domain's size, then I am underconstrained.
 * <p/>
 * For example, Neighbour A can block out {1, 2} or {2, 3} Neighbour B can block
 * out {2} or {2, 3} Then the worst case is when neighbour A blocks out {1,2}
 * and neighbour B blocks out {2,3}
 * <p/>
 * Slightly more formally: There are N groups of sets You have to choose exactly
 * one set from each group Your goal is to maximize the size of the union of the
 * groups that you choose (Note that we don't need the actual values of the
 * choices, just the size)
 * <p/>
 * This problem seems to be a variant of the Maximum Coverage Problem
 * <p/>
 * We do not solve the program exactly, but instead solve a relaxed linear program in which a station can choice a fractional amount of of each of its sets.
 * Note that to truly solve the underconstrained problem, you would also have to make sure that the corresponding choices from the neighbours channels were actually satisfying assignments
 */
@Slf4j
public class UnderconstrainedStationFinder implements IUnderconstrainedStationFinder {

    private final IConstraintManager constraintManager;

    public UnderconstrainedStationFinder(IConstraintManager constraintManager) {
        this.constraintManager = constraintManager;
    }

    @Override
    public Set<Station> getUnderconstrainedStations(Map<Station, Set<Integer>> domains, ITerminationCriterion terminationCriterion, Set<Station> stationsToCheck) {
        final Set<Station> underconstrainedStations = new HashSet<>();
        log.debug("Finding underconstrained stations in the instance...");
        final NeighborIndex<Station, DefaultEdge> neighborIndex = new NeighborIndex<>(ConstraintGrouper.getConstraintGraph(domains, constraintManager));
        for (final Station station : stationsToCheck) {
            final Set<Integer> domain = domains.get(station);
            final Set<Station> neighbours = neighborIndex.neighborsOf(station);
            log.debug("Station {} has {} neighbours, {}", station, neighbours.size(), neighbours);
            if (neighbours.isEmpty()) {
                log.debug("Station {} has no neighbours and is therefore trivially underconstrained", station);
                underconstrainedStations.add(station);
            } else {
                // Create a map of which channels on the station's domain each of its neighbours can block it given all of their choices
                final Map<Station, Map<Integer, Set<Integer>>> channelsThatANeighbourCanBlockOut = neighbours.stream() // Map<Neighbour, Map<NeigbhournCHannel, Set<MyBadChannel>>
                        .collect(
                                Collectors.toMap(
                                        Function.identity(),
                                        neighbour -> domains.get(neighbour).stream()
                                                .collect(
                                                        Collectors.toMap(
                                                                Function.identity(),
                                                                neighbourChannel -> domain.stream()
                                                                        .filter(myChannel -> !constraintManager.isSatisfyingAssignment(station, myChannel, neighbour,(int) neighbourChannel))
                                                                        .collect(Collectors.toSet())
                                                        )
                                                )
                                )
                        ); // TODO: filter empty entries or subsets?
                double maxSpread;
                try {
                    maxSpread = encodeAndSolveAsLinearProgram(domain, channelsThatANeighbourCanBlockOut, domains);
                } catch (IloException e) {
                    throw new RuntimeException(e);
                }
                log.trace("Max spread is upper bounded at {}, and the domain is of size {}", maxSpread, domain.size());
                if (maxSpread < domain.size()) {
                    log.debug("Station {} is underconstrained as it has {} domain channels, but the {} neighbouring interfering stations can only spread to a max of {} of them", station, domain.size(), neighbours.size(), maxSpread);
                    underconstrainedStations.add(station);
                }
            }
        }
        log.debug("Found {} underconstrained stations", underconstrainedStations.size());
        return underconstrainedStations;
    }

    public double encodeAndSolveAsLinearProgram(Set<Integer> domain,
                                                Map<Station, Map<Integer, Set<Integer>>> channels,
                                                Map<Station, Set<Integer>> domains) throws IloException {
        IloCplex cplex = new IloCplex();
        cplex.setOut(new NullOutputStream());
        final List<Integer> domainAsList = new ArrayList<>(domain);
        IloLinearNumExpr[] constraints = new IloLinearNumExpr[domain.size()];
        for (int i = 0; i < domain.size(); i++) {
            constraints[i] = cplex.linearNumExpr();
        }
        final Map<Station, Map<Integer, IloNumVar>> stationToChannelToVar = new HashMap<>();
        for (Entry<Station, Map<Integer, Set<Integer>>> entry : channels.entrySet()) {
            final Station station = entry.getKey();
            stationToChannelToVar.put(station, new HashMap<>());
            Collection<Entry<Integer, Set<Integer>>> c = entry.getValue().entrySet();
            int size = c.size();
            IloNumVar[] sjk = cplex.numVarArray(size, 0, 1.0);
            final Iterator<Entry<Integer, Set<Integer>>> iterator = c.iterator();
            int m = 0;
            while (iterator.hasNext()) {
                Entry<Integer, Set<Integer>> c2 = iterator.next();
                stationToChannelToVar.get(station).put(c2.getKey(), sjk[m]);
                for (Integer i : c2.getValue()) {
                    int ind = domainAsList.indexOf(i);
                    constraints[ind].addTerm(1.0, sjk[m]);
                }
                m++;
            }
            cplex.addEq(cplex.sum(sjk), 1.0);
        }
        IloNumVar[] uArray = cplex.numVarArray(domain.size(), 0, 1);
        IloNumVar[] qArray = cplex.numVarArray(domain.size(), -Double.MAX_VALUE, 0);
        final double epsilon = 10e-3;
        final IloLinearNumExpr obj = cplex.linearNumExpr();
        final double[] uCoeffs = new double[uArray.length];
        Arrays.fill(uCoeffs, 1);
        obj.addTerms(uArray, uCoeffs);
        final double[] qCoeffs = new double[qArray.length];
        Arrays.fill(uCoeffs, epsilon);
        obj.addTerms(qArray, qCoeffs);
        cplex.addMaximize(obj);
        for (int i = 0; i < domainAsList.size(); i++) {
            IloNumVar u = uArray[i];
            IloNumVar q = qArray[i];
            IloLinearNumExpr justu = cplex.linearNumExpr();
            justu.addTerm(1.0, u);
            IloLinearNumExpr qSide = cplex.linearNumExpr();
            qSide.addTerm(1.0, q);
            qSide.add(constraints[i]);
            cplex.addEq(qSide, justu);
        }

        // TODO: these don't seem to be doing anything
        for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains)) {
            Map<Integer, IloNumVar> channelToVarSource = stationToChannelToVar.get(constraint.getSource());
            Map<Integer, IloNumVar> channelToVarTarget = stationToChannelToVar.get(constraint.getTarget());
            if (channelToVarSource != null && channelToVarTarget != null) {
                IloNumVar sourceSjk = channelToVarSource.get(constraint.getSourceChannel());
                IloNumVar targetSjk = channelToVarTarget.get(constraint.getTargetChannel());
                if (sourceSjk != null && targetSjk != null) {
                    final IloLinearNumExpr iloLinearNumExpr = cplex.linearNumExpr();
                    iloLinearNumExpr.addTerm(1.0, sourceSjk);
                    iloLinearNumExpr.addTerm(1.0, targetSjk);
                    cplex.addLe(iloLinearNumExpr, 1.0);
                }
            }
        }

        // Solve the MIP.
        final boolean feasible = cplex.solve();
        // log.info("Solution status = " + cplex.getStatus());
        Double d = null;
        if (feasible) {
            // log.info("Solution value = " + cplex.getObjValue());
            double[] val = cplex.getValues(uArray);
            double sum = Arrays.stream(val).sum();
            d = sum;
            // log.info("Sum is {}", sum);
        } else {
            log.info("Could not solve MIP");
            // cplex.exportModel("/home/newmanne/test.lp");
        }
        cplex.end();
        return d != null ? d : Double.MAX_VALUE;
    }

}