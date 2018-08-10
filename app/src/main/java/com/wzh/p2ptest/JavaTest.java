package com.wzh.p2ptest;

import java.util.Locale;

/**
 * Created by wzh on 07/08/2018.
 */

public class JavaTest {

    public static void main(String[] args) {
        int i = 2147483647;
        String bin = Integer.toBinaryString(16384);
        System.out.println("ll: " + i + " " + bin);

        i = 1073741824;
        System.out.println("ll: " + i + " " + Integer.toBinaryString(i));


        int size = 16384;

        byte b = (byte) size;
        System.out.println("b: " + b);

        byte[] data = new byte[992];
        long setV = 34359738369L;
        setValue(data, 0, 5, setV);
        System.out.println("setv: "+setV);

        long getV = getValue(data, 0, 5);
        System.out.println("getValue: " + setV + " == " + getV + " ?=" + (setV == getV));

        i = 10;
        i = i << 0;//左移，就是在二进制中，在右边添加n个0 ,如3<<2 =11 ，00 = 12
        System.out.println("i: " + i);

        i = 15;
        i = i >>> 2;
        System.out.println("i: " + i);


        i = i >> 0;//右移，就是删除二进制中的右面n个 如15>>2 = 11,11 删除后面11 = 3
        System.out.println("i: " + i);


    }


    private static void setIndex(byte[] data, int index) {
        if (index <= Byte.MAX_VALUE) {//byte -128~127
            data[0] = 0;
            data[1] = (byte) index;
        } else {
            String binary = Integer.toBinaryString(index);//二进制
            System.out.println(": " + binary);
            int len = binary.length();
            data[0] = (byte) (index >> 7);//>>右移，丢弃右边7位
//            data[0]=Byte.parseByte(binary.substring(0,len-7),2);
            data[1] = Byte.parseByte(binary.substring(len - 7), 2);
        }
        System.out.println(String.format("index [0]=%d ,[1]=%d ", data[0], data[1]));

    }


    private static int getIndex(byte[] data) {
        if (data[0] == 0)
            return data[1];
        String high = Integer.toBinaryString(data[0]);
        String low = Integer.toBinaryString(data[1]);
        System.out.println("[0]: " + high + " [1]" + low);
        String binary = high
                + String.format(Locale.CHINA, "%07d", Integer.parseInt(low));
        System.out.println("getIndex: " + binary);
        return Integer.parseInt(binary, 2);
    }

    /**
     * 把int弄value 值 转换成byte数组 表示
     * 如 setValue(data,0,2,333) 表示在数组中0，1两个byte 中分隔存放 333
     *
     * @param data
     * @param firstPosition 数据存放开始索引
     * @param endPosition   数据存放结束索引位置，在数组不包含此位置，如 firstPosition=0，endPosition=2,表示 0,1索引
     * @param value         要表示的int值
     */
    private static void setValue(byte[] data, int firstPosition, int endPosition, long value) {
        if (endPosition - firstPosition < 1)
            throw new IndexOutOfBoundsException("end - first must > 0 " + endPosition + " - " + firstPosition);

        if (value <= Byte.MAX_VALUE) {//byte -128~127
            for (int i = firstPosition; i < endPosition - 1; i++) {
                data[i] = 0;
            }
            data[endPosition - 1] = (byte) value;
        } else {
            String binary = Long.toBinaryString(value);//二进制
            System.out.println(": " + binary);
            int len = binary.length();
            int maxBit = (endPosition - firstPosition) * 7;
            if (len > maxBit) {//超出最大可表示数
                throw new IndexOutOfBoundsException(value + "--> " + binary + " out of " + maxBit + " bit byte value");
            }

            for (int i = endPosition - 1; i >= firstPosition; i--) {
                long newValue = value >> 7;//去除后七位
                data[i] = (byte) (value - (newValue << 7));//加上七位0，相减就是后七位值
                value = newValue;
            }
        }
    }

    /**
     * 把fir~end 位的数转换为对应value
     * @param data
     * @param firstPosition
     * @param endPosition 如fir=0，end=2,表示0,1两位，不包含索引2
     * @return
     */
    private static long getValue(byte[] data, int firstPosition, int endPosition) {
        if (endPosition - firstPosition < 1)
            throw new IndexOutOfBoundsException("end - first must > 0 " + endPosition + " - " + firstPosition);

        long value = 0;
        int bit = endPosition - firstPosition - 1;
        for (int i = firstPosition; i < endPosition; i++, bit--) {
            long bitValue = (long) data[i] << (7 * bit);
//            System.out.println("bit: " + bit + " bitV=" + bitValue);
            value += bitValue;
        }
        return value;
    }


}
