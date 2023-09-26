package app.bqlab.febblindrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FoldersActivity extends AppCompatActivity {

    //constants
    final String TAG = "FoldersActivity";
    final int SPEECH_TO_TEXT = 1000;
    //variables
    boolean clicked;
    int focus, soundMenuEnd, soundDisable;
    String folderDir;
    String[] folderNames;
    ArrayList<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout foldersBody;
    List<FileLayout> foldersBodyLayouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);
        final ProgressBar loading = findViewById(R.id.folders_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (loading != null)
                    loading.setVisibility(View.GONE);
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                init();
                loadFolders();
                resetFocus();
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
                    Log.d("speech", speech.get(0));
                    switch (speech.get(0)) {
                        case "변경":
                            changeFolder();
                            break;
                        case "삭제" :
                            deleteFolder();
                            break;
                    }
                }
            }
        }
    }

    private void init() {
        //initialize
        foldersBody = findViewById(R.id.folders_body);
        foldersBodyLayouts = new ArrayList<>();
        folderDir = Environment.getExternalStorageDirectory() + File.separator + "음성메모장";
        //제스처
        gestureDetector = new GestureDetector(this, new FoldersActivity.MyGestureListener());
    }

    private void clickUp() {
        shutupTTS();
        focus--;
        if (focus < 0) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = 0;
        }
        resetFocus();
        speakFocus();
    }

    private void clickDown() {
        shutupTTS();
        focus++;
        if (focus > foldersBodyLayouts.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = foldersBodyLayouts.size() - 1;
        }
        resetFocus();
        speakFocus();
    }

    private void clickLeft() {
        shutupTTS();
        if (getIntent().getStringExtra("filePath") != null) {
            Intent i = new Intent(this, MenuActivity.class);
            i.putExtra("filePath", getIntent().getStringExtra("filePath") + "@folders");
            startActivity(i);
            finish();
        } else {
            startActivity(new Intent(this, FolderActivity.class));
            finish();
        }
    }

    private void changeFolder() {
        shutupTTS();
        String folderName = folderNames[focus];
        if (new File(folderDir, folderName).exists()) {
            getSharedPreferences("setting", MODE_PRIVATE).edit().putString("SAVE_FOLDER_NAME", folderName).apply();
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("폴더가 변경되었습니다.");
                }
            });
            speakThread.start();
            if (getIntent().getStringExtra("filePath") != null) {
                Intent i = new Intent(this, MenuActivity.class);
                i.putExtra("filePath", getIntent().getStringExtra("filePath") + "@folders");
                startActivity(i);
            }
        } else {
            loadFolders();
        }
    }

    private void changeTestFolder(String folderName) {
        shutupTTS();
        if (new File(folderDir, folderName).exists()) {
            getSharedPreferences("setting", MODE_PRIVATE).edit().putString("SAVE_FOLDER_NAME", folderName).apply();
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("폴더가 변경되었습니다.");
                }
            });
            speakThread.start();
            if (getIntent().getStringExtra("filePath") != null) {
                Intent i = new Intent(this, MenuActivity.class);
                i.putExtra("filePath", getIntent().getStringExtra("filePath") + "@folders");
                startActivity(i);
            }
        } else {
            loadFolders();
        }
    }

    private void deleteFolder() {
        shutupTTS();
        File file = new File(folderDir, folderNames[focus]);
        boolean success = file.delete();
        if (!success) {
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("폴더를 비우고 다시 시도하세요.");
                }
            });
            speakThread.start();
        }
        loadFolders();
        resetFocus();
    }

    private void loadFolders() {
        //파일을 커스텀 레이아웃인 FileLayout으로 치환하여 뷰그룹에 추가(파일 리스트->레이아웃 그룹으로 변환 정도로 이해하면 쉽습니다)
        foldersBody.removeAllViews();
        File dir = new File(folderDir);
        folderNames = dir.list();
        foldersBodyLayouts = new ArrayList<>();
        if (folderNames.length != 0) {
            for (int i = 0; i < folderNames.length; i++) {
                FileLayout fileLayout = new FileLayout(this, String.valueOf(i + 1), folderNames[i]);
                foldersBodyLayouts.add(fileLayout);
                foldersBody.addView(fileLayout);
            }
        }
    }

    private void resetFocus() {
        Log.d("focus", String.valueOf(focus));
        for (int i = 0; i < foldersBodyLayouts.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                foldersBodyLayouts.get(i).setColor(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                foldersBodyLayouts.get(i).setColor(getDrawable(R.drawable.app_button_focussed));
            }
        }
    }

    private void setupTTS() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREA);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(FoldersActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(FoldersActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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

    private void vibrate(long m) {
        Log.d("vibrate", String.valueOf(m));
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        try {
            vibrator.vibrate(VibrationEffect.createOneShot(m, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) {
        }
    }

    private void speakFirst() {
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    speak("폴더목록");
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
        final String folderName = foldersBodyLayouts.get(focus).getButton().getText().toString();
        long lastModified = new File(folderDir, folderName).lastModified();
        Date lastModifiedTime = new Date();
        lastModifiedTime.setTime(lastModified);
        String lastModifiedDay = new SimpleDateFormat("마지막으로 수정된 날짜는 yyyy년 MM월 dd일입니다. 이 폴더로 변경하시려면 스크린을 1초 이상 터치하세요.", Locale.KOREA).format(lastModifiedTime);
        String currentYear = new SimpleDateFormat("yyyy", Locale.KOREA).format(Calendar.getInstance().getTime());
        if (new SimpleDateFormat("yyyy", Locale.KOREA).format(lastModifiedTime).equals(currentYear))
            lastModifiedDay = lastModifiedDay.replace(currentYear + "년", "");
        final String finalLastModifiedDay = lastModifiedDay;
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    speak(folderName);
                    Thread.sleep(1500);
                    speak(finalLastModifiedDay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    private void requestSpeech() {
        shutupTTS();
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    vibrate(1000);
                    speak("폴더를 변경하시려면 변경을, 삭제하시려면 삭제를 말씀해주세요.");
                    Thread.sleep(6500);
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
                    intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "변경 또는 삭제를 말씀하세요.");
                    startActivityForResult(intent, SPEECH_TO_TEXT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
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
                        Toast toast = Toast.makeText(FoldersActivity.this, "→", Toast.LENGTH_SHORT);
                        TextView toastTextView = toast.getView().findViewById(android.R.id.message);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toastTextView.setHeight(300);
                        toastTextView.setWidth(240);
                        toastTextView.setGravity(Gravity.CENTER);
                        toastTextView.setTypeface(toastTextView.getTypeface(), Typeface.BOLD);
                        toastTextView.setTextSize(60);
                        toast.show();
                        requestSpeech();
                        //테스트
                        changeTestFolder("테스트");
                        Log.d("테스트", "바뀜");
                    } else {
                        // 왼쪽 스와이프
                        Toast toast = Toast.makeText(FoldersActivity.this, "←", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FoldersActivity.this, "↓", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FoldersActivity.this, "↑", Toast.LENGTH_SHORT);
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
            requestSpeech();
        }
    }
}
