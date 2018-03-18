package org.vieuxchameau.scroogecoin;

import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;

public class KeyHelper {

    private static final char[] PASSWORD = "Password".toCharArray();
    public static final String SCROOGE = "scrooge";
    public static final String DONALD = "donald";
    public static final String HUEY = "huey";
    public static final String DEWEY = "dewey";
    public static final String LOUIS = "louis";

    private final KeyStore keyStore;

    public KeyHelper() throws Exception {
        keyStore = KeyStore.getInstance("JKS");
        try (InputStream is = this.getClass().getResourceAsStream("/keystore.jks")) {
            keyStore.load(is, PASSWORD);
        }
    }


    public KeyPair getKeyPair(final String alias) throws Exception {
        final Key key = keyStore.getKey(alias, PASSWORD);
        if (key instanceof PrivateKey) {
            final Certificate cert = keyStore.getCertificate(alias);
            final PublicKey publicKey = cert.getPublicKey();
            return new KeyPair(publicKey, (PrivateKey) key);
        }
        throw new IllegalArgumentException("Not key matching " + alias);
    }

    public byte[] sign(final String alias, final byte[] message) throws Exception {
        final KeyPair keyPair = getKeyPair(alias);

        final Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(message);
        return sig.sign();
    }
}
