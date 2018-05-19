package org.hyperledger.fabric.sdkintegration.test;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.encoders.Base64;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class Test2 {
    public static void main(String[] args) throws NoSuchAlgorithmException, DecoderException {

        byte[] bytes = new byte[8];


        String[] luck = {

                "10711253263663657999850425241748847974156381508229927303739026991611",
                "10711253263663657999850425241748847974156381508229927303739026991611",
                "3108452755380748452253346237576411082023531206374983273824231201154",
                "20720521215422284242001350134630667041883944155997106047813908146093",
        };
        BigInteger realLuck = new BigInteger("22221363900507395259431521234335900125749231259476524990119415816416");

        BigInteger[] ds = new BigInteger[4];
        //计算所有hash与实际hash的差
        //并计算差的和
        BigInteger sumds = BigInteger.ZERO;
        for (int i = 0; i < luck.length; i++) {
            ds[i] = realLuck.subtract(new BigInteger(luck[i])).abs();
            System.out.println(ds[i]);
            sumds = sumds.add(ds[i]);
        }
        System.out.println(sumds);
        System.out.println("------");
        BigDecimal[] ss = new BigDecimal[4];
        //计算每个差占总差的比例
        for (int i = 0; i < ss.length; i++) {
            ss[i] = new BigDecimal(ds[i]).divide(new BigDecimal(sumds), 20, BigDecimal.ROUND_HALF_DOWN);
            System.out.println(ss[i].doubleValue());
        }
        System.out.println("------");
//        {"asset":65.79390227454697,"status":0}   5
//        {"asset":104.4340082154009,"status":0} 44
//        {"asset":104.4340082154009,"status":0}  44
//        {"asset":125.33808129465123,"status":0}  55
        int[] a = {40, 40, 30, 40};
        int sum = 150;
        double[] K = new double[4];
        double sum2 = 0.0;
        //计算所有资产占比
        //并*hash差占比
        //并求和
        for (int i = 0; i < a.length; i++) {
            K[i] = a[i] / (double) sum;
            System.out.println(K[i]);
            K[i] *= ss[i].doubleValue();
            sum2 += K[i];
        }
        System.out.println("sum2 "+sum2);
        System.out.println("------");
        //计算资产占比*hash差占比 占的总比例
        for (int i = 0; i < K.length; i++) {
            double v = K[i];
            System.out.println(v);
            K[i] /= sum2;
        }
        System.out.println("--final----");
        for (double v : K) {
            System.out.println(v);
        }
        System.out.println("------");
        //计算结果
        for (double v : K) {
            System.out.println(v*150);
        }
    }
}
