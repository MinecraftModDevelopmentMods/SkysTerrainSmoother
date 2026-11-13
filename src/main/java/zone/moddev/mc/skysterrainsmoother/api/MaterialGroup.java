package zone.moddev.mc.skysterrainsmoother.api;

/** Player-controlled material families shared by built-in and add-on mappings. */
public enum MaterialGroup {
    SOIL("soil"),
    SAND_AND_GRAVEL("sandAndGravel"),
    STONE("stone"),
    SNOW("snow"),
    ICE("ice"),
    OTHER("other");

    private final String configKey;

    MaterialGroup(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }
}
