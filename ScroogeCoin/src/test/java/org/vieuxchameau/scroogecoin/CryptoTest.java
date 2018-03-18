package org.vieuxchameau.scroogecoin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.Signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.vieuxchameau.scroogecoin.Crypto.verifySignature;

class CryptoTest {

    private final KeyHelper keyHelper = new KeyHelper();

    CryptoTest() throws Exception {
    }


    @DisplayName("SCROOGE Public key should verify the message sign by himself")
    @Test
    public void shouldVerifySignature() throws Exception {
        final KeyPair keyPair = keyHelper.getKeyPair(KeyHelper.SCROOGE);

        final byte[] message = "SimpleMessage".getBytes();

        final Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(keyPair.getPrivate());
        sig.update(message);
        final byte[] signature = sig.sign();

        assertThat(verifySignature(keyPair.getPublic(), message, signature)).isTrue();
    }


    @DisplayName("DEWEY Public key should not verify the message sign by SCROOGE")
    @Test
    public void shouldNotVerifySignature() throws Exception {
        final KeyPair scroogeKeyPair = keyHelper.getKeyPair(KeyHelper.SCROOGE);

        final byte[] message = "SimpleMessage".getBytes();

        final Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(scroogeKeyPair.getPrivate());
        sig.update(message);
        final byte[] signature = sig.sign();


        final KeyPair deweyKeyPair = keyHelper.getKeyPair(KeyHelper.DEWEY);

        assertThat(verifySignature(deweyKeyPair.getPublic(), message, signature)).isFalse();
    }
}