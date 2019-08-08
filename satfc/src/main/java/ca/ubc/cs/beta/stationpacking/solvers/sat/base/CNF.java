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

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Ordering;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * A SAT formula in Conjunctive Normal Form (a conjunction of clauses - AND's of OR's of literals). Implementation wise just a clause collection wrapper.
 *
 * @author afrechet
 */
public class CNF {

    protected final List<Clause> clauses;

    public CNF() {
        clauses = new ArrayList<>();
    }

    /**
     * Builds and returns the <a href="http://fairmut3x.wordpress.com/2011/07/29/cnf-conjunctive-normal-form-dimacs-format-explained/">DIMACS</a> string representation of the CNF.
     *
     * @param aComments - the comments to add at the beginning of the CNF, if any.
     * @return the DIMACS string representation of the CNF.
     */
    public String toDIMACS(String[] aComments) {
        StringBuilder aStringBuilder = new StringBuilder();

        int aNumClauses = clauses.size();
        long aMaxVariable = 0;

//         Sort for consistent ordering
        for (Clause aClause : clauses.stream().sorted(Comparator.comparing(Clause::toString)).collect(Collectors.toList())) {
//        for (Clause aClause : clauses) {
            List<String> aLitteralStrings = new ArrayList<>();
            for (Literal aLitteral : aClause.getLiterals()) {
                if (aLitteral.getVariable() <= 0) {
                    throw new IllegalArgumentException("Cannot transform to DIMACS a CNF that has a litteral with variable value <= 0 (clause: " + aClause.toString() + ").");
                } else if (aLitteral.getVariable() > aMaxVariable) {
                    aMaxVariable = aLitteral.getVariable();
                }
                aLitteralStrings.add((aLitteral.getSign() ? "" : "-") + aLitteral.getVariable());
            }

            aStringBuilder.append(StringUtils.join(aLitteralStrings, " ")).append(" 0\n");
        }

        aStringBuilder.insert(0, "p cnf " + aMaxVariable + " " + aNumClauses + "\n");

        if (aComments != null) {
            for (int i = aComments.length - 1; i >= 0; i--) {
                aStringBuilder.insert(0, "c " + aComments[i].trim() + "\n");
            }
        }

        return aStringBuilder.toString();
    }

    /**
     * @return all the variables present in the CNF.
     */
    public Collection<Long> getVariables() {
        Collection<Long> aVariables = new HashSet<Long>();
        for (Clause aClause : clauses) {
            for (Literal aLitteral : aClause.getLiterals()) {
                aVariables.add(aLitteral.getVariable());
            }
        }
        return aVariables;
    }

    @Override
    public String toString() {
        ArrayList<String> aClauseStrings = new ArrayList<String>();
        for (Clause aClause : clauses) {
            aClauseStrings.add("(" + aClause.toString() + ")");
        }
        return StringUtils.join(aClauseStrings, " ^ ");
    }

    public String getHashString() {
        String aString = this.toString();
        MessageDigest aDigest = DigestUtils.getSha1Digest();
        try {
            byte[] aResult = aDigest.digest(aString.getBytes("UTF-8"));
            String aResultString = new String(Hex.encodeHex(aResult));
            return aResultString;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Could not encode cnf with sha1 hash.", e);
        }
    }

    public boolean add(Clause e) {
        if (e == null) {
            throw new IllegalArgumentException("Cannot add a null clause to a CNF.");
        }
        return clauses.add(e);
    }

    public boolean addAll(CNF cnf) {
        if (cnf.clauses.contains(null)) {
            throw new IllegalArgumentException("Cannot add a null clause to a CNF.");
        }
        return clauses.addAll(cnf.clauses);
    }

    public int size() {
        return clauses.size();
    }


}
