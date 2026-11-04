package zone.moddev.mc.skysterrainsmoother;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class NamedForwardFixtureTest {
    @ParameterizedTest
    @CsvSource({
            "1.14.4,1976,D71FE9E2820C4CCEDE75B1642BDBA64EBC8AE654F51F3BF1FD82409BF1044562",
            "1.15.2,2230,B812D07B20C34EE058D155EC8592FFE8AB36DB386FF04A3687BBE9419A81BA5F",
            "1.16.5,2586,079666DA120BFC8B0EDABE50633253712F5EE6EC7D80FD4C7F5DC37D8211E4FA",
            "1.17.1,2730,81650A520DF40A440112FE7CD334FC3ACDE6E4B7909C5CA7B0427DEABA20B7EF",
            "1.18.2,2975,F75AF93379CACBCD0D6A89E4FFBF2D4A0679DE8747F5D51F1AF60747FE4CC2C5",
            "1.19.4,3337,FD1F173DF4BE4983225E28215BEF4168BFECB1535B27386A3A1E39478ECF8A74",
            "1.20.1,3465,C66243CF9A35216E2CEC414737815E75E2130165AA32EE98B384DE8164156AFB"
    })
    void precedingVersionSaveUsesQualifiedJarsAndContainsWetSand(String version, int dataVersion,
            String sealedChecksum) throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/forward-" + version + "-family");
        JsonObject source = json(root.resolve("provenance.json"));
        String checksum = digest(root.resolve("world.zip"));

        assertEquals(sealedChecksum, checksum);
        assertEquals(checksum, source.get("world_sha256").getAsString());
        assertEquals(version, source.get("source_minecraft").getAsString());
        if (version.equals("1.20.1")) {
            assertEquals("forge-47.4.10", source.get("source_loader").getAsString());
        }
        assertEquals(dataVersion, source.get("source_data_version").getAsInt());
        assertEquals(3413, source.get("source_saved_states").getAsInt());
        assertEquals(4, source.get("wet_sand_states").getAsInt());
        assertTrue(source.get("source_world_unchanged").getAsBoolean());
        assertEquals(source.get("manifest_sha256").getAsString(), digest(root.resolve("source-family-fixture.json")));
        assertEquals(source.get("native_states_sha256").getAsString(), digest(root.resolve("native-source-states.json")));
        assertEquals(8, json(root.resolve("source-family-fixture.json")).getAsJsonArray("native_sand_states").size());
        if (version.equals("1.18.2") || version.equals("1.19.4") || version.equals("1.20.1")) {
            Path biomeContract = root.resolve("native-source-biomes.json");
            assertEquals(source.get("native_biomes_sha256").getAsString(), digest(biomeContract));
            JsonObject biomes = json(biomeContract);
            assertEquals(checksum, biomes.get("source_world_sha256").getAsString());
            assertEquals(-16, biomes.get("minimum_quart_y").getAsInt());
            assertEquals(96, biomes.get("quart_height").getAsInt());
            assertEquals(821, biomes.getAsJsonArray("chunks").size());
            assertEquals(1261056, biomes.get("checked_cells").getAsInt());
        }

        Set<String> artifacts = new HashSet<>();

        source.getAsJsonArray("source_artifacts")
                .forEach(row -> artifacts.add(row.getAsJsonObject().get("sha256").getAsString()));
        assertEquals(3, artifacts.size());
        Set<String> expected = switch (version) {
            case "1.14.4" -> Set.of(
                    "3DCC134F8EC216D8C7E78DC7294BB31F06DAEFA097739C241178E7C1D7E8A357",
                    "CE66EA52660BC48533387B3065C082CF1C3916F4502BDB95C04B0F761B1BB195",
                    "8A0A1DEA2A4A6C069519C7B1BCF28331186FF2DF6284DB72C2FFDB8178FC84D4");
            case "1.15.2" -> Set.of(
                    "7AD98ED70F0A88ADE5C7048C21811E7B3BAC889593070D54A0790EA339A81E9D",
                    "A0D741B5D641DE05EFA37DDC0565B5FC3164661A6B03890E71ECF86F7EB56E51",
                    "94A5BAA4B3DC025A338A60BAC27B3E5BBAD4AE0CE494BA5EC1F852E401A704F7");
            case "1.16.5" -> Set.of(
                    "C506D0489231E6663952880F4554B769361DF0FEBE10E05EF18F264DF9D49A3C",
                    "C975A9BC836DBFC9B4A01E2D1DCB350F8201AB12BFBA8A0D9CFE1D0609A2A568",
                    "B4DC9F16E5CF42902B93667B9E95EC846B66E2A3C488CC7CF48D9FBA3B477C41");
            case "1.17.1" -> Set.of(
                    "35D6C37E24C3F02FC2BCABF7833FEF44B8A79586507DB5E8C6B26274C2739434",
                    "5A5EC232E9EB05B870315F7080B2F2B66003F2D66755607135E559B1D1E0D506",
                    "40ACD56C59F50C9C5DC484781B7FEA180E1C45FAB5470735CA27983E14258240");
            case "1.18.2" -> Set.of(
                    "D74CE5C1E1F58A3A679A26E836F3CFEEEEB3897D7A51C407980B6252D47C4433",
                    "357E8792542EEC84E4D3DEF3E91193559B826574DC23DB8A5081A69ABF112CE7",
                    "82709CF16A7282DB5562BE5FCC2F417D0F103620EAE86DBDE9E0CBD760B51B5A");
            case "1.19.4" -> Set.of(
                    "3A97C2D7E1A2E1D9C33D513BE08F9B2D3611AC914185DED8C8B9138D76EF5B82",
                    "A1055C3DD776D24619635D1F30700D6E2A51CBA19ECF2AEE6894E2DBBC6C6DB7",
                    "88EDD053B5A44391FE12FA9642EAABBB685DD3B17FAB845D36C7EE159AD595DA");
            case "1.20.1" -> Set.of(
                    "04B87A04BB9744580C18C34EB9A806CB307F9275969CC88ECA914F5BDA78DF84",
                    "9F61D47241B1AE536912C16539E4034825153430E028C7186A3505C0171A03AD",
                    "C4ABB48E52DE06508D3610E5463F55AF1ADB496E22BFE5EAFE4515BB52209F44");
            default -> throw new AssertionError("No artifact lock for " + version);
        };
        assertEquals(expected, artifacts);
    }

    private JsonObject json(Path path) throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private String digest(Path path) throws Exception {
        StringBuilder result = new StringBuilder();

        for (byte value : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))) {
            result.append(String.format(Locale.ROOT, "%02X", value));
        }
        return result.toString();
    }
}
