package app.bqlab.febblindrecorder;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
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
    //layouts
    LinearLayout filesBody;
    List<FileLayout> filesBodyLayouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_files);
        init();
        setupTTS();
        loadFiles();
        resetFocus();
        setupSoundPool();
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
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
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
        filesBody = findViewById(R.id.folders_body);
        filesBodyLayouts = new ArrayList<FileLayout>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        //setup
        findViewById(R.id.files_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.files_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.files_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.files_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.files_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.files_bot_close).setOnClickListener(new View.OnClickListener() {
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
        if (new File(fileDir, fileName).exists()) {
            File file = new File(fileDir, fileName);
            Intent i = new Intent(FilesActivity.this, PlayActivity.class);
            i.putExtra("filePath", file.getPath());
            i.putExtra("flag", "list");
            startActivity(i);
        } else {
            loadFiles();
        }
    }

    private void clickVToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickXToggle() {
        shutupTTS();
        if (clicked) {
            //두번째 클릭
            try {
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
                    speak("한번 더 누르면 파일이 삭제됩니다.");
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
            //파일을 커스텀 레이아웃인 FileLayout으로 치환하여 뷰그룹에 추가(파일 리스트->레이아웃 그룹으로 변환 정도로 이해하면 쉽습니다)
            filesBody.removeAllViews();
            File dir = new File(fileDir);
            fileNames = dir.list();
            filesBodyLayouts = new ArrayList<>();
            if (fileNames.length != 0) {
                for (int i = 0; i < fileNames.length; i++) {
                    FileLayout fileLayout = new FileLayout(this, String.valueOf(i + 1), fileNames[i]);
                    filesBodyLayouts.add(fileLayout);
                    filesBody.addView(fileLayout);
                }
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
}
