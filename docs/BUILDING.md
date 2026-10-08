# Building from source

Use Java 17 for Gradle and Temurin 8.0.502+7 for production. Set `STS_JAVA8_HOME`
to the Java 8 installation. The wrapper uses Gradle 9.6.1 and ForgeGradle
7.0.34 with stable 29-1.10.2 mappings.

Build the Grass Slabs and Building Pieces source commits recorded in
`gradle/content-dependencies.properties` with their `jar` tasks first.
Then run `gradlew check build javadoc verifyReleaseArtifacts
writeReleaseChecksums verifyEclipseProductionClasspath` here.

The default layout uses sibling Grass Slabs and Building Pieces 1.10
checkouts. Elsewhere, pass `-PgrassSlabsDirectory=<checkout>` and
`-PbuildingPiecesDirectory=<checkout>`. Development dependencies use their
deobfuscated jars; packaged games use the normal main jars. No dependency
classes or assets are bundled.

The build-only integration mod exercises real Forge startup, grass lifecycle,
every footprint, player-tracked chunk generation and a saved-world reload.
It is absent from production artifacts and ordinary Eclipse runs. Use
`genEclipseRuns eclipse` to regenerate those runs. CI builds the pinned content
mods first. Publication remains disabled until this mod has its own CurseForge
project.
