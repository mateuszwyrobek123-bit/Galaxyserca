package pl.galaxy.serca;

public enum HeartType {
    NORMAL("normal"), ANARCHIC("anarchic");

    private final String id;
    HeartType(String id) { this.id = id; }
    public String id() { return id; }

    public static HeartType fromId(String id) {
        for (HeartType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
