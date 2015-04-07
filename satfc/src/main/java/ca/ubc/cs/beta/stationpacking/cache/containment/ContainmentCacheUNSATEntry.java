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
package ca.ubc.cs.beta.stationpacking.cache.containment;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

import lombok.Data;
import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.utils.StationPackingUtils;

/**
* Created by newmanne on 25/03/15.
*/
@Data
public class ContainmentCacheUNSATEntry {
    final BitSet bitSet;
    Map<Station, Set<Integer>> domains;
    String key;

    // "fake" constructor used for comparator purposes only
    public ContainmentCacheUNSATEntry(BitSet bitSet) {
        this.bitSet = bitSet;
    }

    public ContainmentCacheUNSATEntry(final Map<Station, Set<Integer>> domains, final String key) {
        this.key = key;
        this.domains = domains;
        this.bitSet = new BitSet(StationPackingUtils.N_STATIONS);
        domains.keySet().forEach(station -> bitSet.set(station.getID()));
    }

}
