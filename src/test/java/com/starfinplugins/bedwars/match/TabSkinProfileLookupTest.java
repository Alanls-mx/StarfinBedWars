package com.starfinplugins.bedwars.match;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.JsonParser;
import org.junit.Test;

public class TabSkinProfileLookupTest {
    @Test public void acceptsOnlySignedTextureProperty() {
        JsonParser parser = new JsonParser();
        assertNull(TabSkinProfileLookup.parseTexture(parser.parse(
            "{\"properties\":[{\"name\":\"textures\",\"value\":\"unsigned\"}]}"
        ).getAsJsonObject()));
        TabSkinProfileLookup.Texture texture = TabSkinProfileLookup.parseTexture(parser.parse(
            "{\"properties\":[{\"name\":\"other\",\"value\":\"x\"},"
                + "{\"name\":\"textures\",\"value\":\"skin\",\"signature\":\"signed\"}]}"
        ).getAsJsonObject());
        assertEquals("skin", texture.value);
        assertEquals("signed", texture.signature);
    }
}
