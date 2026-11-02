package zone.moddev.mc.skysterrainsmoother;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently checks the raw saved content behind the narrow log profiles. */
class NativeRegistryNoticeProofTest {
    @Test
    void permittedVanillaRegistryNamesAreUnusedInEverySavedRecord() throws Exception {
        JsonObject profiles = new JsonParser().parse(new String(
                Files.readAllBytes(Paths.get("tools/native-log-profiles.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(4, profiles.size());

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : profiles.entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            Set<String> names = new HashSet<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                    : profile.getAsJsonObject("mappings").entrySet()) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> mapping
                        : registry.getValue().getAsJsonObject().entrySet()) {
                    names.add(mapping.getKey());
                }
            }
            if (profile.has("remapped")) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                        : profile.getAsJsonObject("remapped").entrySet()) {
                    for (java.util.Map.Entry<String, com.google.gson.JsonElement> mapping
                            : registry.getValue().getAsJsonObject().entrySet()) {
                        assertTrue(mapping.getValue().getAsJsonObject().get("id").getAsInt() >= 0);
                        assertTrue(mapping.getValue().getAsJsonObject().get("target").getAsString().startsWith("minecraft:"));
                        names.add(mapping.getKey());
                    }
                }
            }
            assertFalse(names.isEmpty());
            JsonObject biome = profile.getAsJsonObject("native_biome_contract");
            byte[] biomeBytes = Files.readAllBytes(Paths.get(biome.get("path").getAsString()));
            assertEquals(biome.get("sha256").getAsString(), checksum(biomeBytes));
            JsonObject contract = new JsonParser().parse(new String(biomeBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(profile.get("sha256"), contract.get("source_world_sha256"));
            assertEquals(biome.get("checked_cells"), contract.get("checked_cells"));
            assertEquals(0, contract.get("native_renamed_cells").getAsInt());
            int records = 0;

            try (ZipFile archive = new ZipFile(profile.get("fixture").getAsString())) {
                java.util.Enumeration<? extends ZipEntry> members = archive.entries();
                while (members.hasMoreElements()) {
                    ZipEntry member = members.nextElement();
                    String label = member.getName();
                    try (InputStream input = archive.getInputStream(member)) {
                        if (label.endsWith(".dat") || label.endsWith(".dat_old")) {
                            byte[] bytes = readAll(input);
                            CompoundTag root;
                            if (bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139) {
                                root = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
                            } else {
                                // Older forced-chunk data is native, uncompressed NBT.
                                root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
                            }
                            // Registry availability is not a saved block, entity or inventory use.
                            root.remove("FML");
                            root.remove("fml");
                            if (label.equals("world/data/skysgrassslabs_legacy_registry.dat")) {
                                assertEquals(java.util.Collections.singleton("Blocks"), root.getAllKeys());
                                CompoundTag blocks = root.getCompound("Blocks");
                                assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
                                        "aliases", "blocked", "ids", "overrides", "dummied")), blocks.getAllKeys());
                                for (Tag row : blocks.getList("ids", 10)) {
                                    assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("K", "V")),
                                            ((CompoundTag) row).getAllKeys());
                                }
                                // This file contains registry availability, not placed blocks or inventories.
                                root.remove("Blocks");
                            }
                            inspect(root, names, label);
                            records++;
                        } else if (label.endsWith(".mca")) {
                            records += region(readAll(input), names, label);
                        }
                    }
                }
            }
            assertEquals(profile.get("inspected_nbt_records").getAsInt(), records);
            assertEquals(0, profile.get("saved_uses").getAsInt());
        }
    }

    private static String checksum(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format(java.util.Locale.ROOT, "%02X", value));
        }
        return result.toString();
    }

    private static int region(byte[] bytes, Set<String> names, String label) throws Exception {
        if (bytes.length == 0) {
            return 0;
        }
        assertTrue(bytes.length >= 8192, label);
        ByteBuffer header = ByteBuffer.wrap(bytes);
        int records = 0;
        for (int slot = 0; slot < 1024; slot++) {
            int location = header.getInt(slot * 4);
            if (location == 0) {
                continue;
            }
            int offset = (location >>> 8) * 4096;
            int length = header.getInt(offset);
            int compression = bytes[offset + 4] & 255;
            InputStream payload = new ByteArrayInputStream(bytes, offset + 5, length - 1);
            if (compression == 1) {
                payload = new GZIPInputStream(payload);
            } else if (compression == 2) {
                payload = new InflaterInputStream(payload);
            } else {
                assertEquals(3, compression, "Unexpected fixture compression: " + label);
            }
            try (DataInputStream input = new DataInputStream(payload)) {
                inspect(NbtIo.read(input), names, label + ":" + slot);
            }
            records++;
        }
        return records;
    }

    private static void inspect(Tag value, Set<String> names, String label) {
        if (value instanceof CompoundTag) {
            CompoundTag compound = (CompoundTag) value;
            for (String key : compound.getAllKeys()) {
                assertFalse(names.contains(key), "Saved registry-name key in " + label + ": " + key);
                inspect(compound.get(key), names, label + "/" + key);
            }
        } else if (value instanceof ListTag) {
            for (Tag entry : (ListTag) value) {
                inspect(entry, names, label);
            }
        } else if (value instanceof StringTag) {
            assertFalse(names.contains(value.getAsString()), "Saved registry-name value in " + label);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
