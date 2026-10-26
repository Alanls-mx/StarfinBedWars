package com.starfinplugins.bedwars.gameplay;

import com.starfinplugins.bedwars.stats.MatchMode;

/** Permission, mode and first-delivery rules shared by menus and match events. */
public final class KitAccess {
    private KitAccess() { }

    public static boolean canEquip(KitCatalog.Definition kit, int level,
                                   boolean permission, int profileLevel,
                                   String mode, boolean arenaAllows) {
        return kit != null && kit.enabled && level >= 1 && level <= 5 && permission
            && profileLevel >= kit.minimumPlayerLevel && arenaAllows
            && kit.modes.contains(MatchMode.persisted(mode));
    }

    public static boolean shouldDeliver(KitCatalog.Delivery delivery,
                                        boolean respawn, boolean deliveredAtStart) {
        if (respawn) return delivery == KitCatalog.Delivery.EVERY_RESPAWN;
        return !deliveredAtStart;
    }
}
