package com.starfinplugins.bedwars.match;

/**
 * A final kill that decides the match does not transfer the loser's materials
 * to the winner: the match is already finishing and inventory is restored.
 * Regular kills continue to reward the attacker as before.
 */
public final class FinalKillResourcePolicy {
    private FinalKillResourcePolicy() { }

    public static boolean shouldTransfer(boolean victimBedAlive,
                                         int victimTeamAlive, int eligibleTeams) {
        return victimBedAlive || victimTeamAlive != 1 || eligibleTeams != 2;
    }
}
