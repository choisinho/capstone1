package app.bqlab.febblindrecorder;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FilesActivity extends AppCompatActivity {

    //variables
    boolean clicked;
    int focus, soundMenuEnd, soundDisable;
    String fileDir;
    String[] fileNames;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout filesBody;
    List<FileLayout> filesBodyLayouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);
        final ProgressBar loading = findViewById(R.id.files_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (loading != null)
                    loading.setVisibility(View.GONE);
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                init();
                setupTTS();
                loadFiles();
                resetFocus();
                setupSoundPool();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        filesBody = findViewById(R.id.folders_body);
        filesBodyLayouts = new ArrayList<FileLayout>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
       //제스처
        gestureDetector = new GestureDetector(this, new FilesActivity.MyGestureListener());
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
        if (focus > filesBodyLayouts.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = filesBodyLayouts.size() - 1;
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        startActivity(new Intent(FilesActivity.this, SearchActivity.class));
        finish();
    }

    private void clickRight() {
        shutupTTS();
        String fileName = fileNames[focus];
        File file = new File(fileDir, fileName);

        if (file.exists()) {
            String fileExtension = getFileExtension(fileName);
            Intent intent = null;
            if (fileExtension.equalsIgnoreCase("mp4")) {
                intent = new Intent(FilesActivity.this, PlayActivity.class);
            } else if (fileExtension.equalsIgnoreCase("txt")) {
                intent = new Intent(FilesActivity.this, TextActivity.class);
            } else {
                // 예외 처리: 지원되지 않는 파일 형식일 경우에 대한 처리
                return;
            }
            intent.putExtra("filePath", file.getPath());
            intent.putExtra("flag", "list");
            startActivity(intent);
        } else {
            loadFiles();
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1);
        } else {
            return "";
        }
    }

    private void deleteFile() {
        shutupTTS();
        if (clicked) {
            //두번째 클릭
            try {
                vibrate(1000);
                File file = new File(fileDir, fileNames[focus]);
                boolean success = file.delete();
                loadFiles();
                resetFocus();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            //첫번째 클릭
            clicked = true;
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    vibrate(500);
                    speak("다시 한번 스크린을 1초 이상 누르면 파일이 삭제됩니다.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new CountDownTimer(6000, 1000) { //딜레이 동안 한번 더 토글 클릭 입력시 파일 삭제
                                @Override
                                public void onTick(long millisUntilFinished) {

                                }

                                @Override
                                public void onFinish() {
                                    clicked = false;
                                }
                            }.start();
                        }
                    });
                }
            });
            speakThread.start();
        }
    }

    //현재 화면에 6개까지만 보여짐 (그 이상은 스크롤 기능 만들기 귀찮아서 일단 보류)
    private void loadFiles() {
        if (new File(fileDir).list().length == 0) {
            startActivity(new Intent(this, MainActivity.class));
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("저장된 파일이 없습니다.");
                }
            });
            speakThread.start();
        } else {
            filesBody.removeAllViews();
            File dir = new File(fileDir);
            fileNames = dir.list();
            filesBodyLayouts = new ArrayList<>();

            // 파일을 최근에 생성된 순서로 정렬
            Arrays.sort(fileNames, new Comparator<String>() {
                @Override
                public int compare(String filename1, String filename2) {
                    File file1 = new File(fileDir + File.separator + filename1);
                    File file2 = new File(fileDir + File.separator + filename2);
                    return Long.compare(file2.lastModified(), file1.lastModified());
                }
            });

            int maxFilesToShow = Math.min(fileNames.length, 6); // 최대 6개 파일까지만 보여주기 위해 개수 제한

            for (int i = 0; i < maxFilesToShow; i++) {
                FileLayout fileLayout = new FileLayout(this, String.valueOf(i + 1), fileNames[i]);
                filesBodyLayouts.add(fileLayout);
                filesBody.addView(fileLayout);
            }
        }
    }

    private void resetFocus() {
        for (int i = 0; i < filesBodyLayouts.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                filesBodyLayouts.get(i).setColor(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                filesBodyLayouts.get(i).setColor(getDrawable(R.drawable.app_button_focussed));
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
                        Toast.makeText(FilesActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(FilesActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
                    speak("현재 폴더는 " + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", ""));
                    Thread.sleep(3000);
                    speak("파일목록");
                    Thread.sleep(1500);
                    speakFocus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();;
    }

    private void speakFocus() {
        final Button button = filesBodyLayouts.get(focus).getButton();
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(String.valueOf(focus + 1) + "번파일 " + button.getText().toString().replace(".mp4", ""));
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
                        Toast toast = Toast.makeText(FilesActivity.this, "→", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FilesActivity.this, "←", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FilesActivity.this, "↓", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(FilesActivity.this, "↑", Toast.LENGTH_SHORT);
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
            deleteFile();
        }
    }
}
