package zone.moddev.mc.skysterrainsmoother;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EclipseDependencyContractTest {
    @Test
    void explicitLocalCandidateSurvivesEclipseRefreshesWithoutChangingCiLocks() throws Exception {
        String build = new String(Files.readAllBytes(Paths.get("build.gradle")), StandardCharsets.UTF_8);

        assertTrue(build.contains("System.getenv('CI') != null"));
        assertTrue(build.contains("!providers.gradleProperty('buildingPiecesDirectory').isPresent()"));
        assertTrue(build.contains("arguments.removeAll"));
        assertTrue(build.contains("arguments.add('-PbuildingPiecesDirectory=' + relativePath(buildingPiecesDirectory))"));
        assertTrue(build.contains("arguments.add('-PbuildingPiecesCoreVersion=' + piecesVersion)"));
        assertTrue(build.contains("preferences.setProperty('arguments', arguments.join(' '))"));
        assertTrue(build.contains("Eclipse Buildship lost the selected content checkouts"));
        assertTrue(build.contains("Eclipse Buildship retained an incorrect Building Pieces version override"));
        assertTrue(build.contains("grassLibraries != [grassSlabsDevelopmentJar.canonicalFile]"));
        assertTrue(build.contains("piecesLibraries != [buildingPiecesDevelopmentJar.canonicalFile]"));
        assertTrue(build.contains("expected ${expectedVersion}, found ${metadata.mod_version}"));
    }
}
