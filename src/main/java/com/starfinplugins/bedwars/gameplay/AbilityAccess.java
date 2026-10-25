package com.starfinplugins.bedwars.gameplay;

/** Explicit permission and state policy. Category has no role in access. */
public final class AbilityAccess {
    private AbilityAccess() { }

    public static boolean canEquip(AbilityCatalog.Definition ability, int ownedLevel,
                                   boolean hasPermission, int playerLevel,
                                   String mode, boolean arenaAllows) {
        return ability != null && ability.enabled && ownedLevel >= 1
            && ownedLevel <= ability.maxLevel && hasPermission
            && playerLevel >= ability.minimumPlayerLevel && arenaAllows
            && ability.modes.contains(mode);
    }

    public static boolean canApply(AbilityCatalog.Definition ability, int ownedLevel,
                                   boolean hasPermission, int playerLevel,
                                   String mode, boolean arenaAllows, boolean inGame,
                                   boolean connected, boolean spectator) {
        return canEquip(ability, ownedLevel, hasPermission, playerLevel, mode, arenaAllows)
            && inGame && connected && !spectator;
    }
}
