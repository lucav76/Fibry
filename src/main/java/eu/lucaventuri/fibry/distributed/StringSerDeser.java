package eu.lucaventuri.fibry.distributed;

public enum StringSerDeser implements ChannelSerDeser<String, String> {
    INSTANCE;

    @Override
    public byte[] serialize(String message) {
        return message == null ? null : message.getBytes();
    }

    @Override
    public String serializeToString(String message) {
        return message;
    }

    @Override
    public String deserialize(byte[] message) {
        return message == null ? null : new String(message);
    }

    @Override
    public String deserializeString(String message) {
        return message;
    }
}
