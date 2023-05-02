package app.bqlab.febblindrecorder;

import android.Manifest;
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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
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
    LinearLayout main, mainBody;
    List<View> mainBodyButtons;
    //objects
    File mFile;
    TextToSpeech mTTS;
    MediaPlayer mPlayer;
    MediaRecorder mRecorder;
    SoundPool mSoundPool;
    Thread speakThread;

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
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            //각 키에 따른 기능 구현
            case KeyEvent.KEYCODE_DPAD_UP:
                clickRight();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                clickLeft();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                clickUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                clickDown();
                return true;
            case KeyEvent.KEYCODE_BUTTON_X:
                clickVToggle();
                return true;
            case KeyEvent.KEYCODE_BUTTON_B:
                clickXToggle();
                return true;
            default:
                return true;
        }
    }

    private void init() {
        //initialize
        main = findViewById(R.id.main);
        mainBody = findViewById(R.id.main_body);
        mainBodyButtons = new ArrayList<View>();
        //setup
        //포커스 처리를 위해 버튼 리스트에 버튼들 적재
        for (int i = 0; i < mainBody.getChildCount(); i++)
            mainBodyButtons.add(mainBody.getChildAt(i));
        //각 키에 따른 클릭 이벤트 처리
        findViewById(R.id.main_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.main_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.main_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.main_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.main_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.main_bot_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickXToggle();
            }
        });
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
        //권한 체크
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
        }
    }
}