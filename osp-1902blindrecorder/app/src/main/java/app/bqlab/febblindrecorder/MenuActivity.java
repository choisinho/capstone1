package app.bqlab.febblindrecorder;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.renderscript.ScriptGroup;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class MenuActivity extends AppCompatActivity {

    //constants
    final int FILE_SAVE = 0;          //저장
    final int RESUME_RECORD = 1;      //이어서 녹음
    final int RE_RECORD = 2;          //재 녹음
    final int RETURN_MAIN = 3;        //메뉴로 돌아가기
    final int SPEECH_TO_TEXT = 1000;  //STT 데이터 요청
    final String TAG = "MenuActivity";
    //variables
    int focus, soundMenuEnd, soundDisable;
    boolean allowedExit, timerStart, foldersToHere, shutup;
    String fileName, fileDir, filePath;
    List<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    //layouts
    LinearLayout menuBody;
    List<View> menuBodyButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        init();
        resetFocus();
        setupSoundPool();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            setupTTS();
            checkEnterOption();
            speakFirst();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shutupTTS();
        if (!allowedExit) {
            File file = new File(filePath);
            boolean success = file.delete();
        }
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SPEECH_TO_TEXT) {
                if (data != null) {
                    //STT 음성 입력 불러옴
                    speech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    switch (focus) {
                        case FILE_SAVE:
                            //포커스가 '파일 저장'에 있을 경우 입력된 이름에 따라 녹음파일 저장(이름이 지정되지 않았을 경우 159... 형태의 난수로된 이름으로 지정됨)
                            String newName = speech.get(0);
                            File file = new File(filePath);
                            if (file.exists()) {
                                File renamedFile = new File(fileDir, newName + ".mp4");
                                if (file.renameTo(renamedFile)) {
                                    try {
                                        getSharedPreferences("setting", MODE_PRIVATE).edit().putString("LATEST_RECORD_FILE", renamedFile.getPath()).apply();
                                        speakThread = new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                speak("녹음파일이 저장되었습니다.");
                                            }
                                        });
                                        speakThread.start();
                                        Thread.sleep(1600);
                                        startActivity(new Intent(this, MainActivity.class));
                                        finish();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                        finish();
                                    }
                                }
                            } else {
                                try {
                                    //사용자가 임의로 파일 경로에 접근하여 삭제했을 경우 발생하는 오류 예외처리
                                    speakThread = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            speak("녹음파일이 삭제되었거나 임의로 수정되었습니다.");
                                        }
                                    });
                                    speakThread.start();
                                    Thread.sleep(2000);
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    finish();
                                }
                            }
                    }
                }
            }
        } else {
            if (requestCode == SPEECH_TO_TEXT) {
                switch (focus) {
                    case FILE_SAVE:
                        int last = 0;
                        //사용자가 정확한 발음으로 음성입력하지 않았을 경우 이름은 이름없음N과 같은 형태로 지정되도록 설정
                        for (File file : new File(fileDir).listFiles()) {
                            if (file.getName().contains("이름없음")) {
                                String s1 = file.getName().replace("이름없음", "");
                                String s2 = s1.replace(".mp4", "");
                                int temp = Integer.parseInt(s2);
                                if (last < temp)
                                    last = temp;
                                //가장 마지막 숫자를 검색
                            }
                        }
                        String newName = "이름없음" + String.valueOf(last + 1); //가장 마지막 숫자보다 1 더 큰 숫자를 끝에 추가
                        File file = new File(filePath);
                        if (file.exists()) {
                            File renamedFile = new File(fileDir, newName + ".mp4");
                            if (file.renameTo(renamedFile)) {
                                try {
                                    getSharedPreferences("setting", MODE_PRIVATE).edit().putString("LATEST_RECORD_FILE", newName).apply();
                                    speakThread = new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            speak("녹음파일이 저장되었습니다.");
                                        }
                                    });
                                    speakThread.start();
                                    Thread.sleep(1600);
                                    startActivity(new Intent(this, MainActivity.class));
                                    finish();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                    finish();
                                }
                            }
                        } else {
                            try {
                                speakThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        speak("녹음파일이 삭제되었거나 임의로 수정되었습니다.");
                                    }
                                });
                                speakThread.start();
                                Thread.sleep(2000);
                                finish();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            }
                        }
                        break;
                }
            }
        }
    }

    private void init() {
        //initialization
        menuBody = findViewById(R.id.menu_body);
        menuBodyButtons = new ArrayList<View>();
        //setting
        for (int i = 0; i < menuBody.getChildCount(); i++)
            menuBodyButtons.add(menuBody.getChildAt(i));
        findViewById(R.id.menu_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.menu_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.menu_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.menu_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.menu_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.menu_bot_close).setOnClickListener(new View.OnClickListener() {
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
        if (focus > menuBodyButtons.size() - 1) {
            focus = menuBodyButtons.size() - 1;
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickRight() {
        if (timerStart) {
            shutupTTS();
            shutup = true;
            allowedExit = true;
            Intent i = new Intent(this, FoldersActivity.class);
            i.putExtra("filePath", filePath);
            startActivity(i);
            finish();
        } else
            mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickVToggle() {
        shutupTTS();
        switch (focus) {
            case FILE_SAVE:
                final String folderName = getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
                speakThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            speak("현재 폴더" + folderName);
                            Thread.sleep(2000);
                            speak("변경하시려면 오른쪽 키 입력");
                            Thread.sleep(1000);
                            if (!timerStart)
                                timerStart = true;
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new CountDownTimer(3000, 1000) {
                                    @Override
                                    public void onTick(long millisUntilFinished) {

                                    }

                                    @Override
                                    public void onFinish() {
                                        timerStart = false;
                                        requestSpeech();
                                    }
                                }.start();
                            }
                        });
                    }
                });
                speakThread.start();
                break;
            case RESUME_RECORD:
                allowedExit = true; //alowedExit는 소스파일을 삭제할지 말지를 결정하는 플래그, 이 경우는 소스파일을 삭제하지 않고 이어 녹음함
                Intent i = new Intent(MenuActivity.this, RecordActivity.class);
                i.putExtra("filePath", filePath);
                startActivity(i);
                finish();
                break;
            case RE_RECORD: //소스파일을 삭제하는 경우
                allowedExit = false;
                startActivity(new Intent(MenuActivity.this, RecordActivity.class));
                finish();
                break;
            case RETURN_MAIN:
                allowedExit = false;
                finish();
                break;
        }
    }

    private void clickXToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void checkEnterOption() {
        try {
            filePath = getIntent().getStringExtra("filePath");
            if (filePath.contains("@folders")) {
                foldersToHere = true;
                filePath = filePath.replace("@folders", "");
                fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
                fileName = filePath.replace(fileDir + File.separator, "");
            } else {
                fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
                fileName = filePath.replace(fileDir + File.separator, "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetFocus() {
        for (int i = 0; i < menuBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                menuBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                menuBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
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
                        Toast.makeText(MenuActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(MenuActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                    finishAffinity();
                }
            }
        });
        mTTS.setPitch(0.7f);
        mTTS.setSpeechRate(1.2f);
    }

    private void shutupTTS() {
        try {
            mTTS.stop();
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
                    if (foldersToHere) {
                        Thread.sleep(1500);
                        requestSpeech();
                        Thread.sleep(3000);
                    } else {
                        Thread.sleep(500);
                        speak("녹음메뉴");
                        Thread.sleep(1500);
                        speakFocus();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        speakThread.start();
    }

    private void speakFocus() {
        final Button button = (Button) menuBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
            }
        });
        speakThread.start();
    }

    private void requestSpeech() {
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!shutup) {
                        speak("파일명을 말하세요.");
                        Thread.sleep(2000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
                        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "파일명을 말하세요.");
                        startActivityForResult(intent, SPEECH_TO_TEXT);
                    }
                });
            }
        });
        speakThread.start();
    }
}