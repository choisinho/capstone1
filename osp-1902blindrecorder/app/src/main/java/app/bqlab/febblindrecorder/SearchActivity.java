package app.bqlab.febblindrecorder;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SearchActivity extends AppCompatActivity {

    //constants
    final int SEARCH_BY_NAME = 0;       //파일 이름으로 찾기
    final int SEARCY_BY_LIST = 1;       //파일 목록
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundMenuEnd, soundDisable;
    boolean cannotFind;
    String fileDir;
    ArrayList<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout searchBody;
    List<View> searchBodyButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        final ProgressBar loading = findViewById(R.id.search_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                loading.setVisibility(View.GONE);
                init();
                setupTTS();
                setupSoundPool();
                resetFocus();
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        speakFirst();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SPEECH_TO_TEXT) {
                if (data != null) {
                    speech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    switch (focus) {
                        case SEARCH_BY_NAME:
                            shutupTTS();
                            String fileName = speech.get(0) + ".mp4";
                            File file = new File(fileDir, fileName);
                            if (file.exists()) {
                                Intent i = new Intent(this, PlayActivity.class);
                                i.putExtra("filePath", file.getPath());
                                i.putExtra("flag", "name");
                                i.putExtra("searchResult", "파일찾기성공"); //PlayActivity로 이동할 때 성공 여부를 전달함
                                startActivity(i);
                            } else {
                                speakThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        cannotFind = true;
                                    }
                                });
                                speakThread.start();
                            }
                            break;
                    }
                }
            }
        }
    }

    private void init() {
        //initialize
        searchBody = findViewById(R.id.search_body);
        searchBodyButtons = new ArrayList<View>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        //setup
        for (int i = 0; i < searchBody.getChildCount(); i++)
            searchBodyButtons.add(searchBody.getChildAt(i));
        //제스처
        gestureDetector = new GestureDetector(this, new SearchActivity.MyGestureListener());
    }

    private void clickUp() {
        shutupTTS();
        focus--;
        if (focus < 0) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = 0;
        }
        speakFocus();
        resetFocus();
    }

    private void clickDown() {
        shutupTTS();
        focus++;
        if (focus > searchBodyButtons.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = searchBodyButtons.size() - 1;
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        startActivity(new Intent(SearchActivity.this, MainActivity.class));
        finish();
    }

    private void clickRight() {
        switch (focus) {
            case SEARCH_BY_NAME:
                shutupTTS();
                requestSpeech();
                break;
            case SEARCY_BY_LIST:
                if (new File(fileDir).list().length != 0)
                    startActivity(new Intent(SearchActivity.this, FilesActivity.class));
                else {
                    speakThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            speak("저장된 파일이 없습니다.");
                        }
                    });
                    speakThread.start();
                }
                break;
        }
    }

    private void resetFocus() {
        for (int i = 0; i < searchBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                searchBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                searchBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
            }
        }
    }

    private void setupSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();
        soundMenuEnd = mSoundPool.load(this, R.raw.app_sound_menu_end, 0);
        soundDisable = mSoundPool.load(this, R.raw.app_sound_disable, 0);
    }

    private void setupTTS() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREA);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(SearchActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(SearchActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void speakFirst() {
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (cannotFind) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    speak("파일을 찾지 못했습니다.");
                } else {
                    try {
                        Thread.sleep(500);
                        speak("파일찾기메뉴");
                        Thread.sleep(1500);
                        speakFocus();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        speakThread.start();
    }

    private void speakFocus() {
        final Button button = (Button) searchBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
            }
        });
        speakThread.start();
    }

    private void requestSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "파일명을 말하세요.");
        startActivityForResult(intent, SPEECH_TO_TEXT);
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
                        Toast.makeText(SearchActivity.this, "→", Toast.LENGTH_SHORT).show();
                        clickRight();
                    } else {
                        // 왼쪽 스와이프
                        Toast.makeText(SearchActivity.this, "←", Toast.LENGTH_SHORT).show();
                        clickLeft();
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // 아래로 스와이프
                        Toast.makeText(SearchActivity.this, "↓", Toast.LENGTH_SHORT).show();
                        clickDown();
                    } else {
                        // 위로 스와이프
                        Toast.makeText(SearchActivity.this, "↑", Toast.LENGTH_SHORT).show();
                        clickUp();
                    }
                }
            }
            return super.onFling(event1, event2, velocityX, velocityY);
        }
    }
}
