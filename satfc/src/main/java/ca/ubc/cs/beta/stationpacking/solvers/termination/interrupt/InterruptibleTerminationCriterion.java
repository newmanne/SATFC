/**
 * Copyright 2015, Auctionomics, Alexandre Fréchette, Neil Newman, Kevin Leyton-Brown.
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
package ca.ubc.cs.beta.stationpacking.solvers.termination.interrupt;

import ca.ubc.cs.beta.stationpacking.solvers.termination.ITerminationCriterion;

import java.util.concurrent.atomic.AtomicBoolean;

/**`
 * Created by newmanne on 13/05/15.
 */
public class InterruptibleTerminationCriterion implements ITerminationCriterion.IInterruptibleTerminationCriterion {

    private final ITerminationCriterion decoratedCriterion;
    private final AtomicBoolean interrupt;

    public InterruptibleTerminationCriterion(ITerminationCriterion decoratedCriterion) {
        this.decoratedCriterion = decoratedCriterion;
        this.interrupt = new AtomicBoolean(false);
    }

    @Override
    public double getRemainingTime() {
        return decoratedCriterion.getRemainingTime();
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