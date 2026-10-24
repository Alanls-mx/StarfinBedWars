package com.starfinplugins.bedwars.match;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Fetches only signed public Minecraft textures; never runs on the server thread. */
final class TabSkinProfileLookup {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F]{32}");

    static final class Texture {
        final String value;
        final String signature;

        Texture(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }

    private TabSkinProfileLookup() { }

    static Texture fetch(String name) throws IOException {
        if (name == null || !NAME.matcher(name).matches()) return null;
        JsonObject identity = read("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name);
        if (identity == null || !identity.has("id")) return null;
        String id = identity.get("id").getAsString();
        if (!ID.matcher(id).matches()) return null;
        return parseTexture(read("https://sessionserver.mojang.com/session/minecraft/profile/"
            + id + "?unsigned=false"));
    }

    static Texture parseTexture(JsonObject profile) {
        if (profile == null || !profile.has("properties")
                || !profile.get("properties").isJsonArray()) return null;
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            if (!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !"textures".equals(property.get("name").getAsString())
                    || !property.has("value") || !property.has("signature")) continue;
            String value = property.get("value").getAsString();
            String signature = property.get("signature").getAsString();
            if (!value.isEmpty() && !signature.isEmpty()) return new Texture(value, signature);
        }
        return null;
    }

    private static JsonObject read(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "StarfinBedWars/1.0");
        try {
            int status = connection.getResponseCode();
            if (status == 204 || status == 404) return null;
            if (status != 200) throw new IOException("Minecraft profile HTTP " + status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[2048];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (output.size() + count > 16384) throw new IOException("Minecraft profile too large");
                    output.write(buffer, 0, count);
                }
                JsonElement json = new JsonParser().parse(
                    new String(output.toByteArray(), StandardCharsets.UTF_8));
                return json.isJsonObject() ? json.getAsJsonObject() : null;
            } catch (RuntimeException invalidJson) {
                throw new IOException("Invalid Minecraft profile response", invalidJson);
            }
        } finally {
            connection.disconnect();
        }
    }
}
