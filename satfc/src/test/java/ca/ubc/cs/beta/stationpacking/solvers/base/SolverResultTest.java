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
package ca.ubc.cs.beta.stationpacking.solvers.base;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;
import ca.ubc.cs.beta.stationpacking.utils.NativeUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.sun.jna.Library;
import com.sun.jna.Native;

@Slf4j
public class SolverResultTest {

    @Before
    public void setUp() {
        Set<Station> s = Sets.newHashSet();
        s.add(new Station(3));
        result = new SolverResult(SATResult.SAT, 37.4, ImmutableMap.of(3, s));
    }

    private SolverResult result;

    @Test
    public void testSerializationDeserializationAreInverses() throws Exception {
        assertEquals(result, JSONUtils.toObject(JSONUtils.toString(result), SolverResult.class));
    }

    public static interface DCCA extends Library {
        public void helloFromC();
    }

    @Test
    public void t() throws Exception {
//        final String path = "/home/newmanne/research/dccasat/sources/DCCASat_with_cutoff_and_for_random_instances_sources/";
//        NativeLibrary.addSearchPath("DCCASat", path);
        final File libFile = new File(Resources.getResource("DCCASat").getFile());
        log.info(libFile.getPath());
        final DCCA dcca = (DCCA) Native.loadLibrary(libFile.getPath(), DCCA.class, NativeUtils.NATIVE_OPTIONS);
        dcca.helloFromC();
    }
    
}