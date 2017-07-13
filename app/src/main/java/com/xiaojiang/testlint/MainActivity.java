package com.xiaojiang.testlint;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("guo", "===onCreate===");

        List list = new ArrayList();
        for (int i = 0; i < 5; i ++) {
            Object obj = new Object();
            list.add(obj);
        }

        for (Object o: list) {
            Object ob2 = new Object();
        }

        int i  = 0;
        while (i < 20) {
            Object ob3 = new Object();
            i ++;
        }

        int j  = 0;
        do {
            Object ob4 = new Object();
            j++;
        } while (j < 10);

    }
}
