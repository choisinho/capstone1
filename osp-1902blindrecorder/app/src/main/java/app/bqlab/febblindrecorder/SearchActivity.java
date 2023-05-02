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

public class SearchActivity extends AppCompatActivity {

    //constants
    final int SEARCH_BY_NAME = 0;       //파일 이름으로 찾기
    final int SEARCY_BY_LIST = 1;       //파일 목록
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundMenuEnd, soundDisable;
    boolean cannotFind;
    String fileDir;
    ArrayList<String> speech;
    //objects
    TextToSpeech mTTS;
    SoundPool mSoundPool;
    Thread speakThread;
    //layouts
    LinearLayout searchBody;
    List<View> searchBodyButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
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
                        case SEARCH_BY_NAME:
                            shutupTTS();
                            String fileName = speech.get(0) + ".mp4";
                            File file = new File(fileDir, fileName);
                            if (file.exists()) {
                                Intent i = new Intent(this, PlayActivity.class);
                                i.putExtra("filePath", file.getPath());
                                i.putExtra("flag", "name");
                                i.putExtra("searchResult", "파일찾기성공"); //PlayActivity로 이동할 때 성공 여부를 전달함
                                startActivity(i);
                            } else {
                                speakThread = new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        cannotFind = true;
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
        searchBody = findViewById(R.id.search_body);
        searchBodyButtons = new ArrayList<View>();
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        //setup
        for (int i = 0; i < searchBody.getChildCount(); i++)
            searchBodyButtons.add(searchBody.getChildAt(i));
        findViewById(R.id.search_bot_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickUp();
            }
        });
        findViewById(R.id.search_bot_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickDown();
            }
        });
        findViewById(R.id.search_bot_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickLeft();
            }
        });
        findViewById(R.id.search_bot_right).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickRight();
            }
        });
        findViewById(R.id.search_bot_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickVToggle();
            }
        });
        findViewById(R.id.search_bot_close).setOnClickListener(new View.OnClickListener() {
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
        if (focus > searchBodyButtons.size() - 1) {
            mSoundPool.play(soundMenuEnd, 1, 1, 0, 0, 1);
            focus = searchBodyButtons.size() - 1;
        }
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        startActivity(new Intent(SearchActivity.this, MainActivity.class));
        finish();
    }

    private void clickRight() {
        switch (focus) {
            case SEARCH_BY_NAME:
                shutupTTS();
                requestSpeech();
                break;
            case SEARCY_BY_LIST:
                if (new File(fileDir).list().length != 0)
                    startActivity(new Intent(SearchActivity.this, FilesActivity.class));
                else {
                    speakThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            speak("저장된 파일이 없습니다.");
                        }
                    });
                    speakThread.start();
                }
                break;
        }
    }

    private void clickVToggle() {
        shutupTTS();
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void clickXToggle()
    {
        shutupTTS();
        mSoundPool.play(soundDisable, 1, 1, 0, 0, 1);
    }

    private void resetFocus() {
        for (int i = 0; i < searchBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                searchBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                searchBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
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
                        Toast.makeText(SearchActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(SearchActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
                        speak("파일찾기메뉴");
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
        final Button button = (Button) searchBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
            }
        });
        speakThread.start();
    }

    private void requestSpeech() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "파일명을 말하세요.");
        startActivityForResult(intent, SPEECH_TO_TEXT);
    }
}
