package com.example.a4Barg.utils;

import java.util.Random;

public class RandomInteger {

    public static int getRandomId(){
        Random random = new Random();
        return 1000+random.nextInt(8999);
    }

}
