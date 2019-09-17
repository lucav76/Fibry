package eu.lucaventuri.common;

import java.io.Closeable;

/** Closable that can close other objects */
public interface ExtendedClosable extends AutoCloseable  {
    AutoCloseable closeOnExit(AutoCloseable... closeables);
}
