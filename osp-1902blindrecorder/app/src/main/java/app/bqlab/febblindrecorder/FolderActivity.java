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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.security.cert.TrustAnchor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FolderActivity extends AppCompatActivity {

    //constants
    final int FOLDER_CREATE = 0;       //폴더 생성
    final int FOLDER_CHANGE = 1;       //폴더 변경
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundMenuEnd, soundDisable;
    String fileDir;
    ArrayList<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout folderBody;
    List<View> folderBodyButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);
        final ProgressBar loading = findViewById(R.id.folder_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (loading != null) {
                    loading.setVisibility(View.GONE);
                }
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                init();
                setupTTS();
                setupSoundPool();
                resetFocus();
                createTestFolder("나의메모");
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
                        case FOLDER_CREATE:
                            String filreDir = Environment.getExternalStorageDirectory() + File.separator + "음성메모장" + File.separator + speech.get(0);
                            File newFile = new File(filreDir);
                            boolean success;
                            if (newFile.exists()) {
                                speakThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        speak("이미 폴더가 존재합니다.");
                                    }
                                });
                                speakThread.start();
                            } else {
                                success = newFile.mkdir();
                                speakThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            speak("폴더 생성 완료");
                                            Thread.sleep(1500);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
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
        folderBody = findViewById(R.id.folder_body);
        folderBodyButtons = new ArrayList<>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장";
        for (int i = 0; i < folderBody.getChildCount(); i++)
            folderBodyButtons.add(folderBody.getChildAt(i));

        //제스처
        gestureDetector = new GestureDetector(this, new FolderActivity.MyGestureListener());
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
        if (focus > folderBodyButtons.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = folderBodyButtons.size() - 1;
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        startActivity(new Intent(FolderActivity.this, MainActivity.class));
        finish();
    }

    private void clickRight() {
        switch (focus) {
            case FOLDER_CREATE:
                if (!isFoldersEnough())
                    requestSpeech();
                break;
            case FOLDER_CHANGE:
                if (new File(fileDir).list().length != 0)
                    startActivity(new Intent(this, FoldersActivity.class));
                else {
                    speakThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            speak("생성된 폴더가 없습니다.");
                        }
                    });
                    speakThread.start();
                }
                break;
        }
    }

    private void resetFocus() {
        for (int i = 0; i < folderBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                folderBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                folderBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
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
                        Toast.makeText(FolderActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(FolderActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
                try {
                    Thread.sleep(500);
                    speak("폴더관리메뉴");
                    Thread.sleep(1500);
                    speakFocus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    private void speakFocus() {
        final Button button = (Button) folderBodyButtons.get(focus);
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

    private void requestSpeech() {
        shutupTTS();
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    speak("생성할 폴더명을 말씀해주세요");
                    Thread.sleep(2500);
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "생성할 폴더명을 말씀해주세요");
                    startActivityForResult(intent, SPEECH_TO_TEXT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    //테스트용 폴더 생성
    private void createTestFolder(String folderName) {
        String filreDir = Environment.getExternalStorageDirectory() + File.separator + "음성메모장" + File.separator + folderName;
        File newFile = new File(filreDir);
        boolean success;
        if (newFile.exists()) {
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("이미 폴더가 존재합니다.");
                }
            });
            speakThread.start();
        } else {
            success = newFile.mkdir();
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        speak("폴더 생성 완료");
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            speakThread.start();
        }
    }

    public boolean isFoldersEnough() {
        String folderPath = Environment.getExternalStorageDirectory() + File.separator + "음성메모장" + File.separator;
        File folder = new File(folderPath);
        File[] files = folder.listFiles();
        int folderCount = 0;

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    folderCount++;
                }
            }
            Log.d("folderCount", String.valueOf(folderCount));
            speak("폴더를 더이상 만들 수 없습니다.");
            return folderCount >= 4;
        } else
            return false;
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
                        Toast toast = Toast.makeText(FolderActivity.this, "→", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FolderActivity.this, "←", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FolderActivity.this, "↓", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FolderActivity.this, "↑", Toast.LENGTH_SHORT);
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
