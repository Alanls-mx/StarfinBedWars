package com.starfinplugins.bedwars.domain.match;

import com.starfinplugins.bedwars.domain.arena.TeamDefinition;
import com.starfinplugins.bedwars.domain.economy.TeamTrapType;
import com.starfinplugins.bedwars.domain.economy.TeamUpgradeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GameTeam {
    private final TeamDefinition definition;
    private final Set<UUID> members = new LinkedHashSet<UUID>();
    private final Map<TeamUpgradeType, Integer> upgrades =
        new EnumMap<TeamUpgradeType, Integer>(TeamUpgradeType.class);
    private final List<TeamTrapType> traps = new ArrayList<TeamTrapType>();
    private boolean bedAlive = true;

    public GameTeam(TeamDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("team definition ausente");
        }
        this.definition = definition;
    }

    public TeamDefinition getDefinition() { return definition; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
    public boolean isBedAlive() { return bedAlive; }
    public int getUpgradeLevel(TeamUpgradeType type) {
        Integer level = upgrades.get(type);
        return level == null ? 0 : level;
    }
    public boolean upgrade(TeamUpgradeType type, int maximumLevel) {
        if (type == null || maximumLevel < 1 || getUpgradeLevel(type) >= maximumLevel) return false;
        upgrades.put(type, getUpgradeLevel(type) + 1);
        return true;
    }
    public boolean addTrap(TeamTrapType trap, int maximumQueued) {
        if (trap == null || maximumQueued < 1 || traps.size() >= maximumQueued) return false;
        traps.add(trap);
        return true;
    }
    public TeamTrapType consumeTrap() {
        return traps.isEmpty() ? null : traps.remove(0);
    }
    public List<TeamTrapType> getTraps() {
        return Collections.unmodifiableList(new ArrayList<TeamTrapType>(traps));
    }
    public void destroyBed() { bedAlive = false; }
    public boolean add(UUID player) {
        if (player == null || members.size() >= definition.getCapacity()) {
            return false;
        }
        return members.add(player);
    }
    public void remove(UUID player) { members.remove(player); }
    public int getActiveCount(GameMatch match) {
        int count = 0;
        for (UUID member : members) {
            GameParticipant participant = match.getParticipant(member);
            if (participant != null && participant.getState() != ParticipantState.ELIMINATED
                    && participant.getState() != ParticipantState.ABANDONED) {
                count++;
            }
        }
        return count;
    }
    public boolean isEligible(GameMatch match) { return getActiveCount(match) > 0; }
}
