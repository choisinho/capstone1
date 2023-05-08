package app.bqlab.febblindrecorder;

import android.Manifest;
import android.app.job.JobScheduler;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    //constants
    final int FOCUS_VOICE_MEMO = 0;             //음성 메모
    final int FOCUS_FOLDER_MANAGE = 1;          //파일 관리
    final int FOCUS_SEARCH_MEMO = 2;            //메모 찾기
    final int FOCUS_INSTANT_PLAY = 3;           //파일 바로 재생
    final int FOCUS_APP_EXIT = 4;               //종료
    //variables
    String fileDir;
    List<String> filePathes;
    int focus, soundMenuEnd, soundDisable;
    boolean playing;
    //layouts
    LinearLayout main;
    LinearLayout mainBody;
    List<View> mainBodyButtons;
    //objects
    File mFile;
    TextToSpeech mTTS;
    MediaPlayer mPlayer;
    MediaRecorder mRecorder;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        init();
        resetFocus();
        setupSoundPool();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //앱이 재개될 때 TTS를 세팅한 후 음성 안내
        setupTTS();
        speakFirst();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //앱이 중지될 때 TTS 음성을 강제로 중지
        shutupTTS();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //앱이 종료될 때 서비스 예외처리
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
        if (mPlayer != null) {
            try {
                mPlayer.stop();
                mPlayer.release();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        mSoundPool.release();
        mSoundPool = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private void init() {
        main = findViewById(R.id.main);
        mainBody = findViewById(R.id.main_body);
        mainBodyButtons = new ArrayList<View>();

        //포커스 처리를 위해 버튼 리스트에 버튼들 적재
        for (int i = 0; i < mainBody.getChildCount(); i++)
            mainBodyButtons.add(mainBody.getChildAt(i));

        //제스쳐 디텍터
        gestureDetector = new GestureDetector(this, new MyGestureListener());
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
        stopRecentPlaying();
    }

    private void clickDown() {
        shutupTTS();
        focus++;
        if (focus > mainBodyButtons.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = mainBodyButtons.size() - 1;
        }
        speakFocus();
        resetFocus();
        stopRecentPlaying();
    }

    private void clickLeft() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickRight() {
        shutupTTS();
        switch (focus) {
            case FOCUS_VOICE_MEMO:
                if (isDirectoryAllRight()) {
                    startActivity(new Intent(MainActivity.this, RecordActivity.class));
                    stopPlaying();
                }
                break;
            case FOCUS_FOLDER_MANAGE:
                isDirectoryAllRight();
                startActivity(new Intent(MainActivity.this, FolderActivity.class));
                stopPlaying();
                break;
            case FOCUS_SEARCH_MEMO:
                if (isDirectoryAllRight()) {
                    startActivity(new Intent(MainActivity.this, SearchActivity.class));
                    stopPlaying();
                }
                break;
            case FOCUS_INSTANT_PLAY:
                if (isDirectoryAllRight())
                    playRecentFile();
                break;
            case FOCUS_APP_EXIT:
                finishAffinity();
        }
    }

    private void clickVToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickXToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private boolean isDirectoryAllRight() {
        fileDir = Environment.getExternalStorageDirectory() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        mFile = new File(fileDir);
        boolean success;
        if (!mFile.exists())
            success = mFile.mkdir();
        if (Objects.equals(getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", ""), "")) {
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("폴더를 설정하지 않았습니다.");
                }
            });
            speakThread.start();
            return false;
        }
        return true;
    }

    private void resetFocus() {
        for (int i = 0; i < mainBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                mainBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                mainBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
            }
        }
    }

    private void setupSoundPool() {
        //음성파일 속성 세팅
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        //SoundPool 속성 세팅
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build();
        //두 효과음 SoundPool에 등록
        soundMenuEnd = mSoundPool.load(this, R.raw.app_sound_menu_end, 0);
        soundDisable = mSoundPool.load(this, R.raw.app_sound_disable, 0);
    }

    private void setupTTS() {
        //TTS 지원 확인 및 속성 세팅
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREA);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(MainActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
            playing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void speak(String text) {
        //TTS에 음성 출력 명령
        Log.d("speak", text);
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void speakFirst() {
        //최초 화면 실행시 출력되는 음성 설정
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    speak("홈메뉴");
                    Thread.sleep(1000);
                    speakFocus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    private void speakFocus() {
        //현재 포커스를 가진 버튼 텍스트 음성으로 출력
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                final Button button = (Button) mainBodyButtons.get(focus);
                speak(button.getText().toString());
            }
        });
        speakThread.start();
    }

    private void playRecentFile() {
        loadFiles(); //파일 리스트 동기화
        final String latestFilePath = getSharedPreferences("setting", MODE_PRIVATE).getString("LATEST_RECORD_FILE", "");
        final File latestFile = new File(latestFilePath);
        playing = false;
        if (Objects.equals(latestFilePath, "")) {
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("최근 저장한 파일을 찾을 수 없습니다.");
                }
            });
            speakThread.start();
        } else {
            //최근파일 재생
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        speak("최근저장메모");
                        Thread.sleep(1000);
                        speak(latestFile.getName());
                        Thread.sleep(3000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!playing) {
                                mRecorder = new MediaRecorder();
                                mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                                mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                                mRecorder.setOutputFile(latestFilePath);
                                mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                                playing = true;
                                Log.d("playing", "shutupTTS: " + String.valueOf(playing));
                                try {
                                    mPlayer = new MediaPlayer();
                                    mPlayer.setDataSource(latestFilePath);
                                    mPlayer.prepare();
                                    mPlayer.start();
                                    mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        @Override
                                        public void onCompletion(MediaPlayer mp) {
                                            playing = false;
                                            Log.d("playing", "shutupTTS: " + String.valueOf(playing));
                                            setupTTS();
                                            speakFocus();
                                        }
                                    });
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });
                }
            });
            speakThread.start();
        }
    }

    private void stopRecentPlaying() {
        //최근 파일 재생 중지
        try {
            if (mPlayer.isPlaying()) {
                mPlayer.stop();
                mPlayer.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFiles() {
        //디렉토리의 파일을 파일 리스트로 불러옴
        filePathes = new ArrayList<>();
        String[] names = mFile.list();
        for (String name : names) {
            filePathes.add(Environment.getExternalStorageDirectory() + File.separator + "음성메모장" + File.separator + name);
        }
    }

    private void stopPlaying() {
        //파일 재생 중지
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        if (mPlayer != null) {
            try {
                mPlayer.stop();
                mPlayer = null;
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
    }

    private void requestPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(Manifest.permission.VIBRATE);

        if (!permissionsToRequest.isEmpty()) {
            String[] permissionsArray = permissionsToRequest.toArray(new String[0]);
            ActivityCompat.requestPermissions(this, permissionsArray, 0);
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
                        Toast.makeText(MainActivity.this, "→", Toast.LENGTH_SHORT).show();
                        clickRight();
                    } else {
                        // 왼쪽 스와이프
                        Toast.makeText(MainActivity.this, "←", Toast.LENGTH_SHORT).show();
                        clickLeft();
                    }
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // 아래로 스와이프
                        Toast.makeText(MainActivity.this, "↓", Toast.LENGTH_SHORT).show();
                        clickDown();
                    } else {
                        // 위로 스와이프
                        Toast.makeText(MainActivity.this, "↑", Toast.LENGTH_SHORT).show();
                        clickUp();
                    }
                }
            }
            return super.onFling(event1, event2, velocityX, velocityY);
        }
    }
}