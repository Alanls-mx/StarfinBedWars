package com.starfinplugins.bedwars.stats;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerPreferencesTest {
    @Test public void languageHasSafeCanonicalFallback() {
        assertEquals("pt_BR", PlayerPreferences.languageOrDefault(null));
        assertEquals("pt_BR", PlayerPreferences.languageOrDefault("wrong"));
        assertEquals("en_US", PlayerPreferences.languageOrDefault("en_us"));
        assertEquals("es_ES", PlayerPreferences.languageOrDefault("es_ES"));
        assertFalse(PlayerPreferences.supportedLanguage("fr_FR"));
    }

    @Test public void quickBuyRoundTripsAndKeepsIntentionalEmpty() {
        assertNull(PlayerPreferences.encode(null));
        assertNull(PlayerPreferences.decode(null));
        assertEquals(Collections.emptyList(), PlayerPreferences.decode(""));
        assertEquals(Arrays.asList("wool", "wood_sword"), PlayerPreferences.decode(
            PlayerPreferences.encode(Arrays.asList("wool", "wood_sword", "wool", "../unsafe"))));
        PlayerPreferences preference = new PlayerPreferences("pt_BR", Arrays.asList("wool"));
        assertEquals("es_ES", preference.withLanguage("es_ES").language);
        assertEquals(Arrays.asList("wool"), preference.getQuickBuy());
    }
}
