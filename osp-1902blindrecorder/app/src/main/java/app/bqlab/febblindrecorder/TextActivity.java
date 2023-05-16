package app.bqlab.febblindrecorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class TextActivity extends AppCompatActivity {

    //constants
    final int PLAY_FILE = 0;
    final int FILE_TO_TEXT = 1;
    //variables
    int focus, soundDisable;
    boolean playing, speaking;
    String fileName, fileDir, filePath;
    //objects
    File mFile;
    TextToSpeech mTTS;
    MediaPlayer mPlayer;
    MediaRecorder mRecorder;
    HashMap<String, String> mTTSMap;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout textBody;
    List<View> textBodyButtons;
    Button textBodyViewer, textBodyAlarm, textBodyPhone, textBodyMap, textBodyBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);
        final ProgressBar loading = findViewById(R.id.text_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                loading.setVisibility(View.GONE);
                init();
                resetFocus();
                setupSoundPool();
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        setupTTS();
        speakFirst();
    }

    @Override
    protected void onPause() {
        super.onPause();
        shutupTTS();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutupTTS();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private void init() {
        //initialize
        textBody = findViewById(R.id.text_body);
        textBodyButtons = new ArrayList<View>();
        for (int i = 0; i < textBody.getChildCount(); i++)
            textBodyButtons.add(textBody.getChildAt(i));

        //제스처
        gestureDetector = new GestureDetector(this, new TextActivity.MyGestureListener());
    }

    private void clickUp() {
        shutupTTS();
        focus--;
        if (focus < 0)
            focus = 0;
        speakFocus();
        resetFocus();
    }

    private void clickDown() {
        shutupTTS();
        focus++;
        if (focus > textBodyButtons.size() - 1)
            focus = textBodyButtons.size() - 1;
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        //이전으로 돌아갈때 인텐트에 파일 데이터 담아서 보내야 함
    }

    private void clickRight() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void longPressOption() {
        switch (focus) {
            case PLAY_FILE:
                break;
            case FILE_TO_TEXT:
                break;
        }
    }

    private void resetFocus() {
        for (int i = 0; i < textBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                textBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                textBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
            }
        }
    }

    private void setupSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        soundDisable = mSoundPool.load(this, R.raw.app_sound_disable, 0);
    }

    private void setupTTS() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREA);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(TextActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(TextActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                    finishAffinity();
                }
            }
        });
        mTTS.setPitch(0.7f);
        mTTS.setSpeechRate(1.2f);
    }

    private void shutupTTS() {
        try {
            speakThread.interrupt();
            speakThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void speak(String text) {
        Log.d("speak", text);
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, mTTSMap);
    }

    private void speakFocus() {
        final Button button = (Button) textBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
            }
        });
        speakThread.start();
    }

    private void speakFirst() {
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    speak("파일을 분석하고 있습니다.");
                    Thread.sleep(1000);
                    speakFocus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    private void vibrate(long m) {
        Log.d("vibrate", String.valueOf(m));
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(m, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
            float diffX = event2.getX() - event1.getX();
            float diffY = event2.getY() - event1.getY();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // 오른쪽 스와이프
                        Toast.makeText(TextActivity.this, "→", Toast.LENGTH_SHORT).show();
                        clickRight();
                    } else {
                        // 왼쪽 스와이프
                        Toast.makeText(TextActivity.this, "←", Toast.LENGTH_SHORT).show();
                        clickLeft();
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // 아래로 스와이프
                        Toast.makeText(TextActivity.this, "↓", Toast.LENGTH_SHORT).show();
                        clickDown();
                    } else {
                        // 위로 스와이프
                        Toast.makeText(TextActivity.this, "↑", Toast.LENGTH_SHORT).show();
                        clickUp();
                    }
                }
            }
            return super.onFling(event1, event2, velocityX, velocityY);
        }

        @Override
        public void onLongPress(MotionEvent event) {
            longPressOption();
        }
    }
}