package org.hyperledger.fabric.sdkintegration.test;


import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Test4 {
    public static void main(String[] args) throws NoSuchAlgorithmException {
        SecureRandom random = new SecureRandom();
        List<Integer> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            users.add(random.nextInt(100_000_000));
        }
        System.out.println(users);
        for (int i = 0; i < 1000; i++) {
            System.out.println(calTarget(users));
        }


//        000000000000000000300e73491ec099efb4292489d16096c4bd12fbe7ee2e3b
    }

    private static int calTarget(List<Integer> users) throws NoSuchAlgorithmException {
        byte[] base = "Luck".getBytes();
        SecureRandom random = new SecureRandom();

        MessageDigest md5 = MessageDigest.getInstance("SHA1");
        for (Integer luck : users) {
            byte[] sail = new byte[random.nextInt(32) + 1];
            random.nextBytes(sail);
            base = md5.digest((new String(base) + luck + new String(sail)).getBytes());
        }
//        System.out.println(Hex.encodeHexString(base));
        int index = base[base.length - 1] & 0xf;
        byte[] data = new byte[4];
        for (int i = 0; i < data.length; i++) {
            data[i] = base[index + i];
        }
        BigInteger big = new BigInteger(data);
        return big.abs().mod(new BigInteger("100000000")).intValue();
    }
}
