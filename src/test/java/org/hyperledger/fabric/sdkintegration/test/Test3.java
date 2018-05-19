package org.hyperledger.fabric.sdkintegration.test;

import org.apache.commons.codec.binary.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Test3 {
    List<byte[]> luckBytes = new ArrayList<>();
    public static void main(String[] args) {
        //幸运号码是一个0～10^80次方的数,主要是为了让范围尽可能的覆盖sha256的数据范围
        //这样攻击者枚举出某个参与者实际的幸运号码非常困难,等价攻击sha256，目前不能在有限时间内计算出，
        //
        String s = "99999999999999999999999999999999999999999999999999999999999999999999999999999999";
        BigInteger bigInt = new BigInteger(s);
        System.out.println(bigInt.toString(16));

        byte[] bytes = bigInt.toByteArray();
        System.out.println(Hex.encodeHexString(bytes));
        //最终的开奖号码为所有参与者幸运号码的数字指纹

    }

    //提交时提交数据的hash
    public void bet() {

    }

    //list 为一个有序列表，根据提交的先后规则
    public static void f(BigInteger... list) {
        byte[] base = "LuckMan".getBytes();
        System.out.println(Hex.encodeHexString(base));
        for (BigInteger x : list) {
            x.toByteArray();
        }
    }
}
