package ca.ubc.cs.beta.fcc.simulator.participation;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * Created by newmanne on 2016-05-20.
 */
public enum Participation {

    BIDDING,
    FROZEN_PROVISIONALLY_WINNING,
    FROZEN_CURRENTLY_INFEASIBLE,
    EXITED_NOT_PARTICIPATING,
    EXITED_NOT_NEEDED,
    EXITED_VOLUNTARILY;

    // You need to compute prices for these stations
    public static final Set<Participation> ACTIVE = ImmutableSet.of(BIDDING, FROZEN_CURRENTLY_INFEASIBLE);
    public static final Set<Participation> NON_ZERO_PRICES = ImmutableSet.copyOf(Sets.union(ACTIVE, ImmutableSet.of(FROZEN_PROVISIONALLY_WINNING)));

    public static final Set<Participation> EXITED = ImmutableSet.of(EXITED_NOT_NEEDED, EXITED_NOT_PARTICIPATING, EXITED_VOLUNTARILY);
    public static final Set<Participation> INACTIVE = ImmutableSet.copyOf(Sets.union(EXITED, ImmutableSet.of(FROZEN_PROVISIONALLY_WINNING)));

}
