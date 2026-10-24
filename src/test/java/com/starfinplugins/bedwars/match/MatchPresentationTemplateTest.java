package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChatColor;
import org.junit.Test;

public class MatchPresentationTemplateTest {
    @Test public void tabFooterExpandsConfiguredBrandAndOtherTokens() {
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("footer", "&ewww.meuserver.com.br");
        tokens.put("arena", "Castelo");
        assertEquals(ChatColor.YELLOW + "www.meuserver.com.br\nCastelo",
            MatchPresentationService.formatTabText("{footer}\\n{arena}", tokens));
    }

    @Test public void sidebarSuffixKeepsActiveColorAcrossProtocolSplit() {
        String[] parts = MatchPresentationService.splitSidebarLine(
            ChatColor.GREEN + "G. de Diamantes II 05:02");
        assertEquals(ChatColor.GREEN + "G. de Diamante", parts[0]);
        assertEquals(ChatColor.GREEN + "s II 05:02", parts[1]);
    }
}
