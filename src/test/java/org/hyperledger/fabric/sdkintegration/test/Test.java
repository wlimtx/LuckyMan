package org.hyperledger.fabric.sdkintegration.test;


import java.util.Arrays;

public class Test {
    public static void main(String[] args) {
        int[] A = {1, 5, 14, 20};
        int[] L = {10, 5, 20, 40};
        int l = 10;//real luck number
        double[] k = new double[A.length];
        int sumA = 0;
        for (int i = 0; i < A.length; i++) {
            sumA += A[i];
        }
        double[] pre = new double[A.length];
        for (int i = 0; i < A.length; i++) {
            pre[i] = A[i] / (double) sumA;
        }
        System.out.println("before: " + Arrays.toString(pre));
        double sumK = 0.0;
        for (int i = 0; i < k.length; i++) {
            k[i] = pre[i] * Math.min((double) L[i] / l,(double) l / L[i]);
            sumK += k[i];
        }
//        System.out.println(Arrays.toString(k));
        //在选取幸运数字后，重新调整他们的资产比例
        for (int i = 0; i < k.length; i++) {
            k[i] /= sumK;
        }
        System.out.println("after: " + Arrays.toString(k));
        double[] A2= new double[k.length];
        double s = 0.0;
        for (int i = 0; i < k.length; i++) {
            A2[i] = sumA * k[i];
            System.out.println(A2[i]);
            A2[i] = Double.valueOf(String.valueOf(A2[i]).replaceFirst("^([^.]++\\.\\d\\d).*+$", "$1"));
            s += A2[i];
        }
        System.out.println(Arrays.toString(A));
        System.out.println(Arrays.toString(A2));
        System.out.println(sumA-s);
        System.out.println(s);
    }
}
