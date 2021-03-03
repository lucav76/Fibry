package eu.lucaventuri.common;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class EasyCrypto {
    private EasyCrypto() {
    }

    /**
     * @param bytes Bytes to encode
     * @return a hash computed with SHA3-512
     * @throws NoSuchAlgorithmException
     */
    public static byte[] hash512(byte[] bytes) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA3-512");
        } catch (NoSuchAlgorithmException e) {
            return new byte[0];
        }
        digest.reset();
        digest.update(bytes);

        return digest.digest();
    }

    /**
     * @param content Content to encode
     * @return a hash computed with SHA3-512
     * @throws NoSuchAlgorithmException
     */
    public static byte[] hash512(String content) {
        return hash512(content.getBytes());
    }

    /**
     * @param bytes content
     * @return the hexadecimal representation of the byes in the content; this is useful to have SHA in hexadecimal
     */
    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bytes.length; i++) {
            int unsigned = ((int) bytes[i]) & 0xff;

            sb.append(Integer.toHexString(unsigned));
        }

        return sb.toString();
    }
}
