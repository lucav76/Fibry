package eu.lucaventuri.fibry.distributed;

import eu.lucaventuri.common.Exceptions;
import eu.lucaventuri.common.SystemUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

/** Class able to serialize and deserialize using Jackson with Reflection, assuming that it is available */
public class JacksonSerDeser<T, R> implements RemoteActorChannel.SerDeser<T, R> {
    private final Object jacksonMapper;
    private final MethodHandle mhWriteValueAsString;
    private final MethodHandle mhReadValue;
    private final Class<R> returnClazz;

    public JacksonSerDeser(Class<R> returnClazz) {
        try {
            Class clz = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
            MethodType mtWriteValueAsString = MethodType.methodType(String.class, Object.class);
            MethodType mtReadValue = MethodType.methodType(Object.class, String.class, Class.class);

            this.jacksonMapper = clz.getConstructor().newInstance();
            this.mhWriteValueAsString = SystemUtils.publicLookup.findVirtual(clz, "writeValueAsString", mtWriteValueAsString);
            this.mhReadValue = SystemUtils.publicLookup.findVirtual(clz, "readValue", mtReadValue);
            this.returnClazz = returnClazz;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] serialize(T object) {
        return serializeToString(object).getBytes();
    }

    @Override
    public String serializeToString(T object) {
        return Exceptions.rethrowRuntime(() -> (String) mhWriteValueAsString.invoke(jacksonMapper, object));
    }

    @Override
    public R deserialize(byte[] object) {
        return deserializeString(new String(object));
    }

    @Override
    public R deserializeString(String object) {
        return (R) Exceptions.rethrowRuntime(() -> mhReadValue.invoke(jacksonMapper, object, returnClazz));
    }
}
