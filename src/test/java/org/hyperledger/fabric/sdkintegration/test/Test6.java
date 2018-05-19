package org.hyperledger.fabric.sdkintegration.test;

import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class Test6 {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();

        BigInteger bigInteger = new BigInteger(Long.toString(random.nextLong()));//随机19位

        System.out.println("受害者: " + bigInteger);

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] HA = md.digest(bigInteger.toByteArray());
        System.out.println("受害者hash: " + Hex.encodeHexString(HA));
        BigInteger A = new BigInteger(Hex.encodeHexString(HA),16);
        System.out.println(A);
        for (long i = 0; i < Long.MAX_VALUE - 1; i++) {
            BigInteger V = new BigInteger(Long.toString(i));
            System.out.println("try       : " + V);
            byte[] HB = md.digest(V.toByteArray());
            System.out.println("try   hash: " + Hex.encodeHexString(HB));
            byte[] luck = Arrays.copyOf(md.digest(HB), 64);
            System.arraycopy(md.digest(HA), 0, luck, 32, 32);
            byte[] finalLuck = md.digest(luck);
            System.out.println("final hash: " + Hex.encodeHexString(finalLuck));
            BigInteger B = new BigInteger(Hex.encodeHexString(HB),16);
            BigInteger res = new BigInteger(Hex.encodeHexString(finalLuck),16);

            if (res.subtract(B).abs().compareTo(res.subtract(A).abs()) < 0) {
                System.out.println("攻击成功!");
                System.out.println("受害者: "+ A);
                System.out.println("攻击者: "+B);
                System.out.println("幸运值: "+res);
                break;
            }


        }
    }
}
