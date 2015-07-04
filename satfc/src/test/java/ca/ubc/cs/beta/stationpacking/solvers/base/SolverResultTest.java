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

import java.io.IOException;
import java.util.Set;

import ca.ubc.cs.beta.stationpacking.execution.SATFCTAEExecutor;
import ca.ubc.cs.beta.stationpacking.tae.SATFCTargetAlgorithmEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import ca.ubc.cs.beta.stationpacking.base.Station;
import ca.ubc.cs.beta.stationpacking.utils.JSONUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

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

    @Test
    public void test() throws Exception {
        String claspParams = "-@0:1:sat-prepro '2' -@0:2:sat-prepro '10' -@0:3:sat-prepro '25' -@0:4:sat-prepro '0' -@0:5:sat-prepro '0' -@0:F:backprop 'yes' -@0:F:no-gamma 'no' -@0:S:eqCond 'no' -@0:S:sat-prepro 'yes' -@0:no:eq '0' -@0:solver 'clasp-3' -@0:trans-ext 'dynamic' -@1:0:del-cfl 'no' -@1:0:heuristic 'Vsids' -@1:0:opt-strategy 'bb' -@1:0:restarts 'x' -@1:0:strengthen 'local' -@1:1:BB:opt-strategy '0' -@1:1:Simp:restarts '128' -@1:1:del-glue '2' -@1:1:del-grow '1.1' -@1:1:del-init '3.0' -@1:1:deletion 'basic' -@1:1:lookahead 'no' -@1:1:strengthen '0' -@1:1:vsids:heuristic '95' -@1:2:Geo:restarts '1.5' -@1:2:del-glue '0' -@1:2:del-grow '20.0' -@1:2:del-init '1000' -@1:2:deletion '75' -@1:3:del-init '9000' -@1:3:deletion '0' -@1:F:init-moms 'no' -@1:F:local-restarts 'no' -@1:F:restart-on-model 'no' -@1:F:sign-fix 'no' -@1:F:update-act 'no' -@1:No:contraction 'no' -@1:S:Geo:aryrestarts '2' -@1:S:contraction 'no' -@1:S:counterCond 'yes' -@1:S:del-grow 'yes' -@1:S:deletion 'yes' -@1:S:growSched 'no' -@1:counter-bump '10' -@1:counter-restarts '3' -@1:del-estimate '0' -@1:del-max '250000' -@1:del-on-restart '0' -@1:init-watches '2' -@1:loops 'no' -@1:opt-heuristic '0' -@1:otfs '2' -@1:partial-check '0' -@1:rand-freq '0.0' -@1:reset-restarts '0' -@1:reverse-arcs '1' -@1:save-progress '180' -@1:score-other '1' -@1:score-res '2' -@1:sign-def '1' -@1:update-lbd '0' ";
        PythonInterpreter python = new PythonInterpreter();
        python.execfile(getClass().getClassLoader().getResourceAsStream("hydra/claspParamPcsParser.py"));
        final String evalString = "get_commmand(\"" + claspParams + "\")";
        log.info("Eval command: " + evalString);
        final PyObject eval = python.eval(evalString);
        final String claspConfig = eval.toString();
        log.info("Eval output: " + claspConfig);
    }

}