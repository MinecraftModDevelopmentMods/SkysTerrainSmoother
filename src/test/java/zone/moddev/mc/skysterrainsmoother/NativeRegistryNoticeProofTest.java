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
import java.util.Base64;
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
    void permittedRegistryNoticesMatchSavedNamesAndUses() throws Exception {
        JsonObject profiles = new JsonParser().parse(new String(
                Files.readAllBytes(Paths.get("tools/native-log-profiles.json")), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("profiles");
        assertEquals(6, profiles.size());

        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : profiles.entrySet()) {
            JsonObject profile = entry.getValue().getAsJsonObject();
            JsonObject mappings = profile.getAsJsonObject("mappings");
            Set<String> names = new HashSet<>();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> registry
                    : mappings.entrySet()) {
                if (registry.getKey().equals("minecraft:worldgen/biome")) {
                    // Biomes can still be live after the registry moves out of Forge's static snapshot.
                    continue;
                }
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
            boolean forge47 = profile.has("forge47_registry_proof");
            assertNotEquals(forge47, profile.has("forge45_registry_proof"));
            JsonObject binding = profile.getAsJsonObject(forge47 ? "forge47_registry_proof" : "forge45_registry_proof");
            byte[] proofBytes = Files.readAllBytes(Paths.get(binding.get("path").getAsString()));
            assertEquals(binding.get("sha256").getAsString(), checksum(proofBytes));
            JsonObject proof = new JsonParser().parse(new String(proofBytes, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(forge47 ? "Forge 47.4.10" : "Forge 45.4.0", proof.get("target").getAsString());
            assertEquals(mappings, proof.getAsJsonObject("snapshot_entries"));
            assertEquals(profile.get("sha256"), proof.get("source_world_sha256"));
            assertEquals(biome.get("sha256"), proof.get("native_biome_contract_sha256"));
            assertEquals(names, proof.getAsJsonObject("unused_non_biome_ids").keySet());
            for (var use : proof.getAsJsonObject("unused_non_biome_ids").entrySet()) {
                assertEquals(0, use.getValue().getAsInt(), use.getKey());
            }
            checkBiomeCensus(contract, mappings.getAsJsonObject("minecraft:worldgen/biome"), proof);
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
                                root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                            } else {
                                // Older forced-chunk data is native, uncompressed NBT.
                                root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
                            }
                            if (label.equals("world/level.dat")) {
                                checkSnapshot(root.getCompound("fml").getCompound("Registries"), mappings);
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
                            // Scan the fallback too. The profile's count describes the primary save.
                            if (!label.endsWith(".dat_old")) {
                                records++;
                            }
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

    private static void checkSnapshot(CompoundTag registries, JsonObject mappings) {
        for (var registry : mappings.entrySet()) {
            var saved = new java.util.HashMap<String, Integer>();
            for (Tag row : registries.getCompound(registry.getKey()).getList("ids", 10)) {
                CompoundTag entry = (CompoundTag) row;
                assertNull(saved.put(entry.getString("K"), entry.getInt("V")));
            }
            for (var entry : registry.getValue().getAsJsonObject().entrySet()) {
                assertEquals(entry.getValue().getAsInt(), saved.get(entry.getKey()), entry.getKey());
            }
        }
    }

    private static void checkBiomeCensus(JsonObject contract, JsonObject noticed, JsonObject proof) throws Exception {
        JsonObject names = contract.getAsJsonObject("source_names");
        int cells = 0;
        int noticedCells = 0;
        for (var value : contract.getAsJsonArray("chunks")) {
            byte[] packed = Base64.getDecoder().decode(value.getAsJsonObject().get("cells").getAsString());
            try (var input = new InflaterInputStream(new ByteArrayInputStream(packed))) {
                for (byte cell : input.readAllBytes()) {
                    String name = names.get(Integer.toString(Byte.toUnsignedInt(cell))).getAsString();
                    if (noticed != null && noticed.has(name)) {
                        noticedCells++;
                    }
                    cells++;
                }
            }
        }
        assertEquals(contract.get("checked_cells").getAsInt(), cells);
        assertEquals(proof.get("noticed_biome_cells").getAsInt(), noticedCells);
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
