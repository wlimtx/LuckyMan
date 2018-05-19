package org.hyperledger.fabric.sdkintegration.client;

import org.apache.commons.compress.utils.IOUtils;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class Pem {
    //"bet", "30", "2147483648", "4be0J/Hs4Cm/4pyctWalwoEw2d5Lzm+ZlVNSKGKvEm4=", "dJnyrOynGdxKfJBVmhpg1Wdh0HeGaOWKErtuPS8t7bTOUQ6kfPoeuk0vBXOcN8pQvv+/Q4w1WFqJIgG++j+49EtLNqxRNbry/BKRxNLDCPeUYkI6Fz0hraGCB+e+L5IwkPVIw9Q88y7zNs3P/A1XexOmgWvdqMBah/l6sb1lP1b0wdVQwFxVcRbuFojZoA4xV37l7r3Oj0j3X+2GpZJHfivhQhlj5R3DTG7TgPWmQirHaDDF8DNYUlNTeVWYSQvas/NYzaoTvdtGUIwg34VoAQaGrZJy4kjTlHcmJaRAsQyRBTw8Qzsg2YACjk//E/+KA8syt9CpJwLgWEQloWf4QA==", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB"
    public static void main(String[] args) throws Exception {
        File mspid = new File("mymsp/IronMan/");
        File pub = new File(mspid, "pub.pem");
        File key = new File(mspid, "key.pem");



        String message = "2335RRFLnbs2h+LYhgdFkV0Iih03MKQJ8jf3NwF7Qbyo+T4=MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxqZK5PmwmJJP1gLt7kKryINxwFt0HdkOuSBNuk4t+dveWKq1+NeAKTz+2KN7w9MndrsVRY5C0lKtn30iIPHvKjf5hrgaWQMF+T56XG10IZZjt1yjBRCV9uUAD5Zlsd9xEZFRDzzNuNRpacfo1e3YITEkAJsMmHAmYf6jz/vKgbWQN3/0k5+KpxTD6avcqLP5un5riXhE3j3Thd6vOQhPh/ocLu7030PCBa1pS0thKsBPgmI3MtHpA7jE7TBBE/slFwaZuhmDbG9f5vCeGcJkahfLj58ifok1SH/u6tMiqiQNxLaYVeAZ+zcX29Z748P3Usjp2S1TcLBPsbMQkva9/QIDAQAB";
        System.out.println(Arrays.toString(message.getBytes()));

        String sign = "lVB0OJrdFg/ebb8eruqjGv0ADDbxYUXFGJGkDhJj8fh+QyIyT2d2zV3h2T5X//HQG1ozMV83Lmch1S4g1jQYJpFPnBH6ct1v9KqWHZ8mtNCxNEzhD7533Its42jeD/TpYDyU3w8BBMpTmnmrvmcgx32RJUaTHCePkKN3ykqcHiV+PrJFizOVCx+ugCejT0T6qnBFhxTXD77NllCVkCRpe8ENtH3GlkYPyBFuOa82hUIBx8BHp26QCYUqt10SeRHsW685nNgVnnRgnR10f+iALUrIsmiZM4XKUUAfU7WckMmSOxDaRNMmYD403FFMa+vzgS7p4V71oUA0OOdRmyW1yw==";
        System.out.println(Hex.toHexString(sha256(base64ToDecode(sign))));
        String base64PublicKey = loadPemBase64(pub);
        String base64PrivateKey = loadPemBase64(key);

        RSAPrivateKey rsaPrivateKey = loadPrivateKey(base64ToDecode(base64PrivateKey));
        System.out.println("sign: "+Base64.getEncoder().encodeToString(rsaWithSha256(message.getBytes(), rsaPrivateKey)));
        System.out.println("base64: " + base64PublicKey);
        RSAPublicKey key1 = loadPublicKey(base64ToDecode(base64PublicKey));
        byte[] decrypt = decrypt(base64ToDecode(sign), key1);
        System.out.println(Hex.toHexString(decrypt));
        System.out.println(Hex.toHexString(sha256(message.getBytes())));
        System.out.println(Arrays.toString(sha256(message.getBytes())));

        byte[] sign1 = sign(message.getBytes(), rsaPrivateKey);
        System.out.println(doCheck(message.getBytes(), sign1, key1));
        System.out.println(rsaPrivateKey.getPrivateExponent());
    }

    public static byte[] rsaWithSha256(byte[] plainText, RSAPrivateKey key) throws Exception {
        return encrypt(sha256(plainText), key);
    }

    public static byte[] base64ToDecode(String base64) {
        return org.bouncycastle.util.encoders.Base64.decode(base64);
    }

    public static String loadPemBase64(File file) throws Exception {
        return new String(IOUtils.toByteArray(new FileInputStream(file)))
                .replaceFirst("^([^\r\n]++)", "")
                .replaceAll("[\r\n]", "")
                .replaceFirst("-[\\s\\S]++$", "");
    }

    public static RSAPublicKey loadPublicKey(byte[] key) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    public static RSAPrivateKey loadPrivateKey(byte[] encodeKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encodeKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(keySpec);
    }

    public static byte[] encrypt(byte[] content, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(content);
    }

    public static byte[] decrypt(byte[] content, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(content);
    }

    public static String SHA256(byte[] raw) throws NoSuchAlgorithmException {
        return Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(raw));
    }

    public static byte[] sha256(byte[] bytes) {
        System.out.println(java.util.Base64.getEncoder().encodeToString(bytes));
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new byte[]{};
    }

    public static byte[] sign(byte[] plainText, RSAPrivateKey key)
    {
        try
        {
            java.security.Signature signature = java.security.Signature.getInstance("SHA256WithRSA");
            signature.initSign(key);
            signature.update(plainText);
            return signature.sign();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean doCheck(byte[] content, byte[] sign, RSAPublicKey publicKey)
    {
        try
        {


            java.security.Signature signature = java.security.Signature
                    .getInstance("SHA256WithRSA");

            signature.initVerify(publicKey);
            signature.update(content);
            return signature.verify(sign);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    public static int randomNumber() {
        return new SecureRandom().nextInt(10_000_000);
    }
    public static byte[] randomBytes() {
        SecureRandom sec = new SecureRandom();
        byte[] random = new byte[sec.nextInt(32) + 1];
        sec.nextBytes(random);
        return random;
    }

    public static byte[] nTimesOfSha256(int n, byte[] bytes) {
        for (int i = n; i > 0; i--)
            bytes = Pem.sha256(bytes);
        return bytes;
    }
}

