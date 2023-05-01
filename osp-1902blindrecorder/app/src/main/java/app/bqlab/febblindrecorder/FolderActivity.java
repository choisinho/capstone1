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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
    //layouts
    LinearLayout folderBody;
    List<View> folderBodyButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder);
        init();
        setupTTS();
        setupSoundPool();
        resetFocus();
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
        //setting
        findViewById(R.id.folder_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.folder_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.folder_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.folder_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.folder_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.folder_bot_close).setOnClickListener(new View.OnClickListener() {
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
        shutupTTS();
        switch (focus) {
            case FOLDER_CREATE:
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

    private void clickVToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickXToggle() {
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
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

    private void requestSpeech() {
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
}
