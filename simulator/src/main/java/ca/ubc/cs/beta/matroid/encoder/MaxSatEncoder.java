package ca.ubc.cs.beta.matroid.encoder;

import ca.ubc.cs.beta.fcc.vcg.VCGMip;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.Constraint;
import ca.ubc.cs.beta.stationpacking.datamanagers.constraints.IConstraintManager;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles.yaml.EncodingType;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Clause;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATDecoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.ISATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoder;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoderUtils;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.base.CompressionBijection;
import ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.base.IBijection;
import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.Pair;

import java.util.*;

import static ca.ubc.cs.beta.stationpacking.solvers.sat.cnfencoder.SATEncoderUtils.SzudzikElegantPairing;

/**
 * Created by newmanne on 2016-08-11.
 */
@Slf4j
public class MaxSatEncoder extends SATEncoder {

    public MaxSatEncoder(IConstraintManager constraintManager) {
        super(constraintManager, new CompressionBijection<>(), EncodingType.DIRECT);
    }

    @Override
    public Pair<CNF, ISATDecoder> encode(StationPackingInstance aInstance) {
        return super.encode(aInstance);
    }

    public CNF encodeGreedyClauses(StationPackingInstance instance) {
        CNF cnf = new CNF();

        for (Station s : instance.getStations()) {
            final List<Integer> domain = new ArrayList<>(instance.getDomains().get(s));

            // Either you are on a channel...
            final Clause atLeastOneDomainClause = new Clause();
            for (Integer channel : domain) {
                atLeastOneDomainClause.add(new Literal(bijection.map(SzudzikElegantPairing(s.getID(), channel)), true));
            }

            // Or, for this particular channel, everything interferes with you
            for (Integer channel : domain) {
                final Clause stationChannelClause = new Clause(atLeastOneDomainClause);
                final Map<Station, Set<Integer>> domainsCopy = new HashMap<>(instance.getDomains());
                domainsCopy.put(s, ImmutableSet.of(channel));
                for (Constraint constraint : constraintManager.getAllRelevantConstraints(domainsCopy)) {
                    final boolean sIsSource = constraint.getSource().equals(s);
                    final long interferingStationVar;
                    if (sIsSource) {
                        interferingStationVar = bijection.map(SzudzikElegantPairing(constraint.getTarget().getID(), constraint.getTargetChannel()));
                    } else {
                        interferingStationVar = bijection.map(SzudzikElegantPairing(constraint.getSource().getID(), constraint.getSourceChannel()));
                    }
                    stationChannelClause.add(new Literal(interferingStationVar, true));
                }
                cnf.add(stationChannelClause);
            }
        }

        return cnf;
    }

    public static Set<VCGMip.StationChannel> getConstraintsForChannel(IConstraintManager constraintManager, Station s, int channel, Map<Station, Set<Integer>> domains) {
        final Set<VCGMip.StationChannel> interfering = new HashSet<>();
        for (Constraint constraint : constraintManager.getAllRelevantConstraints(domains)) {
            final boolean sIsSource = constraint.getSource().equals(s);
            final boolean sIsTarget = constraint.getTarget().equals(s);
            if (sIsSource || sIsTarget) {
                int chan = sIsSource ? constraint.getSourceChannel() : constraint.getTargetChannel();
                if (chan == channel) {
                    if (sIsSource) {
                        interfering.add(new VCGMip.StationChannel(constraint.getTarget().getID(), constraint.getTargetChannel()));
                    } else {
                        interfering.add(new VCGMip.StationChannel(constraint.getSource().getID(), constraint.getSourceChannel()));
                    }
                }
            }
        }
//        log.debug("Found {} constraints matching station {} on channel {}", interfering.size(), s, channel);
        return interfering;
    }

//    @Override
//    public Pair<CNF, ISATDecoder> encode(StationPackingInstance aInstance) {
//
//    }

    //    @Override
//    public Pair<CNF, ISATDecoder> encode(StationPackingInstance aInstance) {
//        return null;
//    }


}
