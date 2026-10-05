package org.puneet.cloudsimplus.hiippo.scenario;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Protocol 2 length-prefixed UTF-8 seed derivation, independent of platform serialization. */
public final class Seeds {
    private Seeds() {}
    public static long derive(long master, String phase, String scenario, int replication, String component, String... algorithm) {
        if(replication<0 || algorithm.length>1) throw new IllegalArgumentException("Invalid seed identity");
        var digest=sha256();
        for(var field:new String[]{"2",Long.toString(master),phase,scenario,Integer.toString(replication),component}) add(digest,field);
        for(var field:algorithm) add(digest,field);
        return ByteBuffer.wrap(digest.digest()).getLong();
    }
    private static void add(MessageDigest digest,String field) {
        byte[] bytes=field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }
    public static String hash(String text) { return HexFormat.of().formatHex(sha256().digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
