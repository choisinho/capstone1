package app.bqlab.febblindrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MemoActivity extends AppCompatActivity {

    //constants
    final int MEMO_VIEWER = 0;       //파일 이름으로 찾기
    final int INPUT_MEMO = 1;       //파일 이름으로 찾기
    final int SAVE_MEMO = 2;       //파일 목록
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundMenuEnd, soundDisable;
    boolean cannotFind = false, isFirst = true;
    String fileDir;
    ArrayList<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout memoBody;
    List<View> memoBodyButtons;
    Button memoBodyViewer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_memo);
        final ProgressBar loading = findViewById(R.id.memo_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (loading != null)
                    loading.setVisibility(View.GONE);
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                init();
                setupTTS();
                setupSoundPool();
                resetFocus();
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
                    //STT 음성 입력 불러옴
                    speech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    switch (focus) {
                        case INPUT_MEMO:
                            vibrate(500);
                            inputMemo(speech.get(0));
                            break;
                        case SAVE_MEMO:
                            boolean saved = saveMemo(speech.get(0));
                            Log.d("saved", String.valueOf(saved));
                            if (saved) {
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            }
                            break;
                    }
                }
            }
        } else {
            //사용자가 아무 말도 하지 않은 경우
            if (requestCode == SPEECH_TO_TEXT) {
                switch (focus) {
                    case INPUT_MEMO:
                        vibrate(500);
                        break;
                    case SAVE_MEMO:
                        boolean saved = saveMemo(null);
                        Log.d("saved", String.valueOf(saved));
                        if (saved) {
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        }
                        break;
                }
            }
        }
    }


    private void init() {
        //initialize
        memoBody = findViewById(R.id.memo_body);
        memoBodyViewer = findViewById(R.id.memo_body_viewer);
        memoBodyButtons = new ArrayList<View>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        Log.d("save folder", fileDir);
        //setup
        for (int i = 0; i < memoBody.getChildCount(); i++)
            memoBodyButtons.add(memoBody.getChildAt(i));
        //제스처
        gestureDetector = new GestureDetector(this, new MemoActivity.MyGestureListener());
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
        if (focus > memoBodyButtons.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = memoBodyButtons.size() - 1;
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        startActivity(new Intent(MemoActivity.this, MainActivity.class));
        finish();
    }

    private void clickRight() {
        Log.d("focus", String.valueOf(focus));
        switch (focus) {
            case MEMO_VIEWER:
                shutupTTS();
                speak(memoBodyViewer.getText().toString());
                break;
            case INPUT_MEMO:
                shutupTTS();
                requestSpeech("추가할 내용을 말하세요.");
                break;
            case SAVE_MEMO:
                shutupTTS();
                requestSpeech("저장할 이름을 말하세요.");
                break;
        }
    }

    private void resetFocus() {
        for (int i = 0; i < memoBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                memoBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                memoBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
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
                        Toast.makeText(MemoActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(MemoActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
                        speak("메모작성화면");
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
        final Button button = (Button) memoBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
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

    private void requestSpeech(String msg) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, msg);
        startActivityForResult(intent, SPEECH_TO_TEXT);
    }

    private void inputMemo(String newString) {
        String oldString = memoBodyViewer.getText().toString();
        if (isStringLengthOver())
            speak("더이상 내용을 추가할 수 없습니다.");
        else if (isFirst && !newString.equals(null)) {
            memoBodyViewer.setText(newString);
            isFirst = false;
        }  else
            memoBodyViewer.setText(oldString + " " + newString);
    }

    private boolean saveMemo(String fileName) {
        String content = memoBodyViewer.getText().toString();

        if (isFirst) {
            speak("저장할 내용이 없습니다.");
            return false;
        }

        int last = 0;
        if (fileName == null) {
            for (File f : new File(fileDir).listFiles()) {
                if (f.getName().contains("이름 없는 텍스트 메모")) {
                    String s1 = f.getName().replace("이름 없는 텍스트 메모", "");
                    String s2 = s1.replace(".txt", "");
                    int temp = Integer.parseInt(s2);
                    if (last < temp)
                        last = temp;
                    //가장 마지막 숫자를 검색
                }
            }
            fileName = "이름 없는 텍스트 메모" + String.valueOf(last + 1); //가장 마지막 숫자보다 1 더 큰 숫자를 끝에 추가
        }

        File file = new File(fileDir, fileName + ".txt");
        try {
            // FileWriter를 사용하여 텍스트 파일에 데이터를 씁니다.
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(content);
            fileWriter.flush();
            fileWriter.close();
            Log.d("saved name", fileName);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            speak("파일 저장에 실패했습니다. 잠시후 다시 시도하세요.");
            return false;
        }
    }

    private boolean isStringLengthOver() {
        String s = memoBodyViewer.getText().toString();
        return s.length() >= 120;
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
                        Toast toast = Toast.makeText(MemoActivity.this, "→", Toast.LENGTH_SHORT);
                        TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toastTextView.setHeight(300);
                        toastTextView.setWidth(240);
                        toastTextView.setGravity(Gravity.CENTER);
                        toastTextView.setTypeface(toastTextView.getTypeface(), Typeface.BOLD);
                        toastTextView.setTextSize(60);
                        toast.show();
                        clickRight();
                    } else {
                        // 왼쪽 스와이프
                        Toast toast = Toast.makeText(MemoActivity.this, "←", Toast.LENGTH_SHORT);
                        TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toastTextView.setHeight(300);
                        toastTextView.setWidth(240);
                        toastTextView.setGravity(Gravity.CENTER);
                        toastTextView.setTypeface(toastTextView.getTypeface(), Typeface.BOLD);
                        toastTextView.setTextSize(60);
                        toast.show();
                        clickLeft();
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // 아래로 스와이프
                        Toast toast = Toast.makeText(MemoActivity.this, "↓", Toast.LENGTH_SHORT);
                        TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toastTextView.setHeight(300);
                        toastTextView.setWidth(240);
                        toastTextView.setGravity(Gravity.CENTER);
                        toastTextView.setTypeface(toastTextView.getTypeface(), Typeface.BOLD);
                        toastTextView.setTextSize(60);
                        toast.show();
                        clickDown();
                    } else {
                        // 위로 스와이프
                        Toast toast = Toast.makeText(MemoActivity.this, "↑", Toast.LENGTH_SHORT);
                        TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toastTextView.setHeight(300);
                        toastTextView.setWidth(240);
                        toastTextView.setGravity(Gravity.CENTER);
                        toastTextView.setTypeface(toastTextView.getTypeface(), Typeface.BOLD);
                        toastTextView.setTextSize(60);
                        toast.show();
                        clickUp();
                    }
                }
            }
            return super.onFling(event1, event2, velocityX, velocityY);
        }

        @Override
        public void onLongPress(MotionEvent event) {
            vibrate(1000);
            clickRight();
        }
    }
}
