package com.starfinplugins.bedwars.setup;

import com.starfinplugins.bedwars.domain.economy.ResourceType;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GeneratorSetupInputTest {
    @Test
    public void parsesGeneratorResourcesInEnglishAndPortuguese() {
        assertEquals(ResourceType.IRON, ArenaSetupWizardService.parseGeneratorResource("iron"));
        assertEquals(ResourceType.IRON, ArenaSetupWizardService.parseGeneratorResource("FERRO"));
        assertEquals(ResourceType.GOLD, ArenaSetupWizardService.parseGeneratorResource("gold"));
        assertEquals(ResourceType.GOLD, ArenaSetupWizardService.parseGeneratorResource("ouro"));
        assertEquals(ResourceType.DIAMOND, ArenaSetupWizardService.parseGeneratorResource("diamond"));
        assertEquals(ResourceType.DIAMOND, ArenaSetupWizardService.parseGeneratorResource("diamante"));
        assertEquals(ResourceType.EMERALD, ArenaSetupWizardService.parseGeneratorResource("emerald"));
        assertEquals(ResourceType.EMERALD, ArenaSetupWizardService.parseGeneratorResource("esmeralda"));
    }

    @Test
    public void rejectsCombinedOrUnknownResourceWithActionableError() {
        try {
            ArenaSetupWizardService.parseGeneratorResource("iron:gold");
            fail("Expected invalid resource to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("geradores centrais"));
            assertTrue(expected.getMessage().contains("diamond/diamante"));
            assertTrue(expected.getMessage().contains("emerald/esmeralda"));
        }
    }
}
