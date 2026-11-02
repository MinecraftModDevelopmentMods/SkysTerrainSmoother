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
            "1.16.5,2586,079666DA120BFC8B0EDABE50633253712F5EE6EC7D80FD4C7F5DC37D8211E4FA"
    })
    void precedingVersionSaveUsesQualifiedJarsAndContainsWetSand(String version, int dataVersion,
            String sealedChecksum) throws Exception {
        Path root = Paths.get("src/test/resources/fixtures/forward-" + version + "-family");
        JsonObject source = json(root.resolve("provenance.json"));
        String checksum = digest(root.resolve("world.zip"));

        assertEquals(sealedChecksum, checksum);
        assertEquals(checksum, source.get("world_sha256").getAsString());
        assertEquals(version, source.get("source_minecraft").getAsString());
        assertEquals(dataVersion, source.get("source_data_version").getAsInt());
        assertEquals(3413, source.get("source_saved_states").getAsInt());
        assertEquals(4, source.get("wet_sand_states").getAsInt());
        assertTrue(source.get("source_world_unchanged").getAsBoolean());
        assertEquals(source.get("manifest_sha256").getAsString(), digest(root.resolve("source-family-fixture.json")));
        assertEquals(source.get("native_states_sha256").getAsString(), digest(root.resolve("native-source-states.json")));
        assertEquals(8, json(root.resolve("source-family-fixture.json")).getAsJsonArray("native_sand_states").size());

        Set<String> artifacts = new HashSet<>();

        source.getAsJsonArray("source_artifacts")
                .forEach(row -> artifacts.add(row.getAsJsonObject().get("sha256").getAsString()));
        assertEquals(3, artifacts.size());
        String[] expected = version.equals("1.14.4") ? new String[] {
                "3DCC134F8EC216D8C7E78DC7294BB31F06DAEFA097739C241178E7C1D7E8A357",
                "CE66EA52660BC48533387B3065C082CF1C3916F4502BDB95C04B0F761B1BB195",
                "8A0A1DEA2A4A6C069519C7B1BCF28331186FF2DF6284DB72C2FFDB8178FC84D4"
        } : version.equals("1.15.2") ? new String[] {
                "7AD98ED70F0A88ADE5C7048C21811E7B3BAC889593070D54A0790EA339A81E9D",
                "A0D741B5D641DE05EFA37DDC0565B5FC3164661A6B03890E71ECF86F7EB56E51",
            "94A5BAA4B3DC025A338A60BAC27B3E5BBAD4AE0CE494BA5EC1F852E401A704F7"
        } : new String[] {
            "C506D0489231E6663952880F4554B769361DF0FEBE10E05EF18F264DF9D49A3C",
            "C975A9BC836DBFC9B4A01E2D1DCB350F8201AB12BFBA8A0D9CFE1D0609A2A568",
            "B4DC9F16E5CF42902B93667B9E95EC846B66E2A3C488CC7CF48D9FBA3B477C41"
        };
        for (String artifact : expected) {
            assertTrue(artifacts.contains(artifact), artifact);
        }
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
