package com.example.zebra

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val emdkWrapper=EMDKWrapper();
        emdkWrapper.OnCreateZebra(this);
    }
}