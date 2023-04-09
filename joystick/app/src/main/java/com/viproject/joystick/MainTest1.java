package com.viproject.joystick;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class MainTest1 extends Activity {
    FrameLayout fLayout;
    RelativeLayout rLayout;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_test1);

        fLayout = findViewById(R.id.f_layout);
        rLayout = findViewById(R.id.r_layout);

        fLayout.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                // 터치된 지점의 좌표를 가져옵니다.
                int x = (int) motionEvent.getX();
                int y = (int) motionEvent.getY();

                Log.d("x", String.valueOf(x));
                Log.d("y", String.valueOf(y));

                return true;
            }
        });
    }
}