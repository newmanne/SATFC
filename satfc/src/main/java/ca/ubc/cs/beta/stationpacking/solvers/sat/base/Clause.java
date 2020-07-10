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
package ca.ubc.cs.beta.stationpacking.solvers.sat.base;

import java.util.*;

import com.google.common.collect.Ordering;
import lombok.Getter;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A disjunctive clause (OR's of litterals). Implementation-wise just a litteral collection wrapper.
 *
 * @author afrechet
 */
public class Clause {

    // Use TreeSet for consistent ordering and because duplicates don't make sense
    @Getter
    private final TreeSet<Literal> literals;


    public Clause() {
        literals = new TreeSet<>(Comparator.comparingLong(l -> l.getVariable() * (l.getSign() ? 1 : -1)));
    }

    public Clause(Collection<Literal> literals) {
        this();
        addAll(literals);
    }

    @Override
    public String toString() {
        return StringUtils.join(literals, " v ");
    }

    public boolean add(Literal e) {
        if (e == null) {
            throw new IllegalArgumentException("Cannot add a null literal to a clause.");
        }
        return literals.add(e);
    }

    public boolean addAll(Collection<? extends Literal> c) {
        if (c.contains(null)) {
            throw new IllegalArgumentException("Cannot add a null literal to a clause.");
        }
        return literals.addAll(c);
    }

    public int size() {
        return literals.size();
    }

}
