package ca.ubc.cs.beta.matroid.encoder;

import ca.ubc.cs.beta.stationpacking.solvers.sat.base.CNF;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Clause;
import ca.ubc.cs.beta.stationpacking.solvers.sat.base.Literal;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

/**
 * Created by newmanne on 2016-08-11.
 */
public class WCNF extends CNF {

    public String toDIMACS(String[] aComments) {
        final StringBuilder sb = new StringBuilder();

        int aNumClauses = clauses.size();
        long aMaxVariable = 0;

        for (Clause aClause : clauses) {
            ArrayList<String> aLitteralStrings = new ArrayList<String>();

            for (Literal aLitteral : aClause) {
                if (aLitteral.getVariable() <= 0) {
                    throw new IllegalArgumentException("Cannot transform to DIMACS a CNF that has a litteral with variable value <= 0 (clause: " + aClause.toString() + ").");
                } else if (aLitteral.getVariable() > aMaxVariable) {
                    aMaxVariable = aLitteral.getVariable();
                }
                aLitteralStrings.add((aLitteral.getSign() ? "" : "-") + Long.toString(aLitteral.getVariable()));
            }

            sb.append(StringUtils.join(aLitteralStrings, " ")).append(" 0\n");
        }

        sb.insert(0, "p cnf " + aMaxVariable + " " + aNumClauses + "\n");

        if (aComments != null) {
            for (int i = aComments.length - 1; i >= 0; i--) {
                sb.insert(0, "c " + aComments[i].trim() + "\n");
            }
        }

        return sb.toString();
    }


    public static class WeightedClause extends Clause {

        @Getter
        private final long weight;

        public WeightedClause(long weight) {
            super();
            this.weight = weight;
        }

    }
}
