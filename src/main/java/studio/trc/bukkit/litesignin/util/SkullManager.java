package studio.trc.bukkit.litesignin.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

public class SkullManager
{
    @Getter
    private static final Map<UUID, String> base64Meta = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    public static void refreshTexture(UUID uuid, String name) {
        if (base64Meta.containsKey(uuid)) return;
        if (UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).equals(uuid)) {
            return;
        }
        StringBuilder source = new StringBuilder();
        try {
            URL url = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString());
            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                while ((line = reader.readLine()) != null) {
                    source.append(line);
                    source.append('\n');
                }
            }
            JsonObject json = gson.fromJson(source.toString(), JsonObject.class);
            base64Meta.put(uuid, json.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString());
        } catch (Exception ex) {
            return;
        }
    }

    private static ItemStack getDefaultHead() {
        return new ItemStack(Material.PLAYER_HEAD);
    }

    public static String getHeadTexturesFromHead(ItemStack headItem) {
        if (headItem == null || !(headItem.getItemMeta() instanceof SkullMeta)) {
            return null;
        }
        SkullMeta skull = (SkullMeta) headItem.getItemMeta();
        PlayerProfile profile = skull.getOwnerProfile();
        if (profile == null) {
            return null;
        }
        URL skin = profile.getTextures().getSkin();
        if (skin == null) {
            return null;
        }
        return encodeSkinUrl(skin.toString());
    }

    public static ItemStack getHeadWithTextures(String textures) {
        ItemStack headItem = getDefaultHead();
        if (textures == null) return headItem;
        SkullMeta skull = (SkullMeta) headItem.getItemMeta();
        URL skinUrl = getSkinUrl(textures);
        if (skull == null || skinUrl == null) {
            return headItem;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "Skull");
        PlayerTextures playerTextures = profile.getTextures();
        playerTextures.setSkin(skinUrl);
        profile.setTextures(playerTextures);
        skull.setOwnerProfile(profile);
        headItem.setItemMeta(skull);
        return headItem;
    }

    private static URL getSkinUrl(String textures) {
        try {
            String decoded = new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8);
            JsonObject textureJson = gson.fromJson(decoded, JsonObject.class);
            return new URL(textureJson.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
        } catch (Exception ex) {
            return null;
        }
    }

    private static String encodeSkinUrl(String skinUrl) {
        JsonObject root = new JsonObject();
        JsonObject textures = new JsonObject();
        JsonObject skin = new JsonObject();
        skin.addProperty("url", skinUrl);
        textures.add("SKIN", skin);
        root.add("textures", textures);
        return Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
    }
}
