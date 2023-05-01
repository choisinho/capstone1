package app.bqlab.febblindrecorder;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
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
    //variables
    boolean clicked;
    int focus, soundMenuEnd, soundDisable;
    String folderDir;
    String[] folderNames;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    //layouts
    LinearLayout foldersBody;
    List<FileLayout> foldersBodyLayouts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folders);
        init();
        loadFolders();
        resetFocus();
        setupSoundPool();
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
        foldersBody = findViewById(R.id.folders_body);
        foldersBodyLayouts = new ArrayList<>();
        folderDir = Environment.getExternalStorageDirectory() + File.separator + "음성메모장";
        //setup
        findViewById(R.id.folders_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.folders_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.folders_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.folders_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.folders_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.folders_bot_close).setOnClickListener(new View.OnClickListener() {
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

    private void clickRight() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickVToggle() {
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

    private void clickXToggle() {
        shutupTTS();
        if (clicked) {
            //두번째 클릭
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
        } else {
            //첫번째 클릭
            clicked = true;
            speakThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    speak("한번 더 누르면 폴더가 삭제됩니다.");
                }
            });
            speakThread.start();
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
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
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
        String lastModifiedDay = new SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREA).format(lastModifiedTime);
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
}
