/**
 * Copyright 2016, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
 * <p>
 * This file is part of SATFC.
 * <p>
 * SATFC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * SATFC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with SATFC.  If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * For questions, contact us at:
 * afrechet@cs.ubc.ca
 */
package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;
import ca.ubc.cs.beta.stationpacking.solvers.termination.infinite.NeverEndingTerminationCriterion;
import lombok.extern.slf4j.Slf4j;

/**
 * `
 * Created by newmanne on 13/05/15.
 */
@Slf4j
public class InterruptibleTerminationCriterion implements ITerminationCriterion.IInterruptibleTerminationCriterion {

    private final ITerminationCriterion decoratedCriterion;
    private final AtomicBoolean interrupt;


    public InterruptibleTerminationCriterion(ITerminationCriterion decoratedCriterion) {
        this.decoratedCriterion = decoratedCriterion;
        this.interrupt = new AtomicBoolean(false);
    }

    public InterruptibleTerminationCriterion() {
        this(new NeverEndingTerminationCriterion());
    }

    @Override
    public double getRemainingTime() {
        return interrupt.get() ? 0.0 : decoratedCriterion.getRemainingTime();
    }

    @Override
    public boolean hasToStop() {
        return interrupt.get() || decoratedCriterion.hasToStop();
    }

    @Override
    public void notifyEvent(double aTime) {
        decoratedCriterion.notifyEvent(aTime);
    }

    public boolean interrupt() {
        return interrupt.compareAndSet(false, true);
    }


}
