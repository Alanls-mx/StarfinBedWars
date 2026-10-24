package com.starfinplugins.bedwars.reconnect;

import com.starfinplugins.bedwars.domain.match.ParticipantState;
import com.starfinplugins.bedwars.domain.match.GameParticipant;
import com.starfinplugins.bedwars.domain.match.ConnectionState;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReconnectSessionTest {
    private PlayerStateSnapshot snapshot() {
        final PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("getContents")) return new ItemStack[36];
                    if (method.getName().equals("getArmorContents")) return new ItemStack[4];
                    if (method.getName().equals("getHeldItemSlot")) return 0;
                    return null;
                }
            });
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {Player.class}, new InvocationHandler() {
                @Override public Object invoke(Object proxy, Method method, Object[] args) {
                    if (method.getName().equals("getInventory")) return inventory;
                    if (method.getName().equals("getHealth")) return 20.0;
                    if (method.getName().equals("getFoodLevel")) return 20;
                    if (method.getName().equals("getSaturation")) return 5.0f;
                    if (method.getName().equals("getLocation")) return new Location(null, 0, 64, 0);
                    if (method.getName().equals("getActivePotionEffects")) return Collections.emptyList();
                    return null;
                }
            });
        return new PlayerStateSnapshot(player);
    }

    @Test public void deadlineIsAbsoluteAndInclusive() {
        ReconnectSession session = new ReconnectSession(UUID.randomUUID(), UUID.randomUUID(),
            "castle", "red", 1000L, 2000L, ParticipantState.CONNECTED, snapshot());
        assertTrue(session.isWithinDeadline(1999L));
        assertTrue(session.isWithinDeadline(2000L));
        assertFalse(session.isWithinDeadline(2001L));
        assertTrue(session.begin(2000L));
        assertFalse(session.begin(2000L));
        session.complete();
        assertFalse(session.begin(2000L));
    }

    @Test public void failedRestoreCanRetryWithoutExtendingDeadline() {
        ReconnectSession session = new ReconnectSession(UUID.randomUUID(), UUID.randomUUID(),
            "castle", "red", 1000L, 2000L, ParticipantState.RESPAWNING, snapshot());
        assertTrue(session.begin(1500L));
        session.retry();
        assertEquals(2000L, session.getExpiresAt());
        assertTrue(session.begin(1800L));
        session.retry();
        assertFalse(session.begin(2001L));
        session.expire();
        assertEquals(ReconnectSession.State.EXPIRED, session.getState());
    }

    @Test public void disconnectPreservesRespawnLifeState() {
        GameParticipant participant = new GameParticipant(UUID.randomUUID(), "match", "red");
        participant.setState(ParticipantState.RESPAWNING);
        participant.setState(ParticipantState.DISCONNECTED);
        assertEquals(ParticipantState.RESPAWNING, participant.getLifeState());
        assertEquals(ConnectionState.DISCONNECTED, participant.getConnectionState());
        participant.setConnectionState(ConnectionState.RECONNECTING);
        assertEquals(ParticipantState.RESPAWNING, participant.getLifeState());
    }
}
