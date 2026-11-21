package zone.moddev.mc.skysterrainsmoother.probe;

/** Watches synchronous chunk requests made by one controlled late-decoration pass. */
public final class ChunkReadGuard {
    private static final ThreadLocal<Integer> REQUESTS = new ThreadLocal<>();

    public static void begin() {
        if (REQUESTS.get() != null) {
            throw new AssertionError("A chunk request audit is already active");
        }
        REQUESTS.set(0);
    }

    public static boolean active() {
        return REQUESTS.get() != null;
    }

    public static void request(int chunkX, int chunkZ, boolean create, boolean alreadyLoaded) {
        Integer count = REQUESTS.get();
        if (count == null) {
            return;
        }
        REQUESTS.set(count + 1);
        if (create && !alreadyLoaded) {
            throw new AssertionError("The late pass requested an unavailable chunk " + chunkX + "," + chunkZ);
        }
    }

    public static int end() {
        Integer count = REQUESTS.get();
        REQUESTS.remove();
        if (count == null || count == 0) {
            throw new AssertionError("The native chunk request hook did not run");
        }
        return count;
    }

    private ChunkReadGuard() {
    }
}
