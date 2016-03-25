package ca.ubc.cs.beta.stationpacking.facade.datamanager.solver.bundles;

import ca.ubc.cs.beta.stationpacking.base.StationPackingInstance;
import ca.ubc.cs.beta.stationpacking.facade.datamanager.data.ManagerBundle;
import ca.ubc.cs.beta.stationpacking.solvers.ISolver;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;
import lombok.extern.slf4j.Slf4j;

/**
* Created by newmanne on 01/10/15.
*/
@Slf4j
public abstract class AVHFUHFSolverBundle extends ASolverBundle {

    public AVHFUHFSolverBundle(ManagerBundle managerBundle) {
        super(managerBundle);
    }

    protected abstract ISolver getUHFSolver();
    protected abstract ISolver getVHFSolver();

    @Override
    public ISolver getSolver(StationPackingInstance aInstance) {
        //Return the right solver based on the channels in the instance.
        if (StationPackingUtils.HVHF_CHANNELS.containsAll(aInstance.getAllChannels()) || StationPackingUtils.LVHF_CHANNELS.containsAll(aInstance.getAllChannels())) {
            log.debug("Returning solver configured for VHF");
            return getVHFSolver();
        } else {
            log.debug("Returning solver configured for UHF");
            return getUHFSolver();
        }
    }

    @Override
    public void close() throws Exception {
        getUHFSolver().notifyShutdown();
        getVHFSolver().notifyShutdown();
    }
}
