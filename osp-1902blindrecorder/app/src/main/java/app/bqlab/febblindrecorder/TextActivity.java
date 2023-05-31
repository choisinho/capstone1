package app.bqlab.febblindrecorder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.net.Uri;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextActivity extends AppCompatActivity {

    //constants
    final int TEXT_VIEWER = 0;
    final int GET_ALARM = 1;
    final int GET_PHONE = 2;
    final int GET_MAP = 3;
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundDisable;
    String fileName, fileDir, filePath, flag;
    String viewerContent, phoneNumber, dateTime;
    ArrayList<String> speech;
    //objects
    File mFile;
    TextToSpeech mTTS;
    HashMap<String, String> mTTSMap;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    //layouts
    LinearLayout textBody;
    List<View> textBodyButtons;
    Button textBodyViewer, textBodyAlarm, textBodyPhone, textBodyMap, textBodyBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);
        final ProgressBar loading = findViewById(R.id.text_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                loading.setVisibility(View.GONE);
                init();
                resetFocus();
                setupSoundPool();
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SPEECH_TO_TEXT) {
                if (data != null) {
                    speech = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    switch (focus) {
                        case GET_ALARM:
                            shutupTTS();
                            if (speech.get(0).equals("알람")) {
                                //알람 기능 만들기
                            }
                            break;
                        case GET_PHONE:
                            shutupTTS();
                            if (speech.get(0).equals("전화")) {
                                Intent intent = new Intent(Intent.ACTION_CALL);
                                intent.setData(Uri.parse("tel:" + phoneNumber));
                                startActivity(intent);
                            }
                            break;
                        case GET_MAP:
                            shutupTTS();
                            break;
                    }
                }
            }
        }
    }

    private void init() {
        //initialize
        textBody = findViewById(R.id.text_body);
        textBodyButtons = new ArrayList<View>();
        for (int i = 0; i < textBody.getChildCount(); i++)
            textBodyButtons.add(textBody.getChildAt(i));

        //file
        flag = getIntent().getStringExtra("flag");
        filePath = getIntent().getStringExtra("filePath");
        fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
        fileName = filePath.replace(fileDir + File.separator, "");
        mFile = new File(fileDir, fileName);
        Log.d("filePath", filePath);

        //제스처
        gestureDetector = new GestureDetector(this, new TextActivity.MyGestureListener());

        //layout
        if (isMp4File(filePath)) {
            //mp4 to txt
        } else if (isTxtFile(filePath)) {
            String s = ""; // txt 파일의 내용을 저장할 변수
            try {
                File file = new File(filePath);
                BufferedReader br = new BufferedReader(new FileReader(file));
                String line;
                while ((line = br.readLine()) != null) {
                    s += line + "\n";
                }
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            textBodyViewer = findViewById(R.id.text_body_viewer);
            textBodyViewer.setText(s);
        }
        //for test
        textBodyViewer.setText("제일산업 영업부 대리 김모씨 전화번호는 01012345678 6월 9일 오후 12시 교통대학교 정문카페에서 미팅");
        viewerContent = textBodyViewer.getText().toString();
    }

    private void clickUp() {
        shutupTTS();
        focus--;
        if (focus < 0)
            focus = 0;
        speakFocus();
        resetFocus();
    }

    private void clickDown() {
        shutupTTS();
        focus++;
        if (focus > textBodyButtons.size() - 1)
            focus = textBodyButtons.size() - 1;
        speakFocus();
        resetFocus();
    }

    private void clickLeft() {
        shutupTTS();
        String flag = getIntent().getStringExtra("flag");
        if (flag.equals("list")) {
            if (isMp4File(filePath)) {
                File file = new File(fileDir, fileName);
                Intent i = new Intent(this, PlayActivity.class);
                i.putExtra("filePath", file.getPath());
                i.putExtra("flag", flag);
                startActivity(i);
            } else if (isTxtFile(filePath)) {
                File file = new File(fileDir, fileName);
                Intent i = new Intent(this, FilesActivity.class);
                i.putExtra("filePath", file.getPath());
                startActivity(i);
            } else
                speak("잠시후 다시 시도해주세요.");
            finish();
        } else if (flag.equals("name")) {
            startActivity(new Intent(TextActivity.this, SearchActivity.class));
            finish();
        }
    }

    private void clickRight() {
        switch (focus) {
            case TEXT_VIEWER:
                speakFocus();
                break;
            case GET_ALARM:
                speakThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            dateTime = getDateTime(viewerContent);
                            if (dateTime == null) {
                                speak("일정 관련 정보가 인식되지 않았습니다.");
                            } else {
                                speak("인식된 일정은 " + dateTime + " 입니다. 알람을 원하시면 잠시후 알람을 말하세요.");
                                Thread.sleep(3000);
                                requestSpeech("알람을 원하시면 알람이라고 말씀하세요.", GET_ALARM);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                speakThread.start();
                break;
            case GET_PHONE:
                speakThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            phoneNumber = getPhoneNumber(viewerContent);
                            if (phoneNumber == null) {
                                speak("전화번호가 인식되지 않았습니다.");
                            } else {
                                speak("인식된 전화번호는 " + phoneNumber + " 입니다. 전화를 원하시면 잠시 후 전화라고 말씀하세요.");
                                Thread.sleep(3000);
                                requestSpeech("전화를 원하시면 전화라고 말씀하세요.", GET_PHONE);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                speakThread.start();
                break;
            case GET_MAP:
                break;
        }
    }

    private void resetFocus() {
        for (int i = 0; i < textBodyButtons.size(); i++) {
            if (i != focus) {
                //포커스가 없는 버튼 처리
                textBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button));
            } else {
                //포커스를 가진 버튼 처리
                textBodyButtons.get(i).setBackground(getDrawable(R.drawable.app_button_focussed));
            }
        }
    }

    private void setupSoundPool() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        mSoundPool = new SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build();
        soundDisable = mSoundPool.load(this, R.raw.app_sound_disable, 0);
    }

    private void setupTTS() {
        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREA);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(TextActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
                        finishAffinity();
                    }
                } else {
                    Toast.makeText(TextActivity.this, "지원하지 않는 서비스입니다.", Toast.LENGTH_LONG).show();
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
        mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, mTTSMap);
    }

    private void speakFocus() {
        final Button button = (Button) textBodyButtons.get(focus);
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                speak(button.getText().toString());
            }
        });
        speakThread.start();
        Log.d("focus", String.valueOf(focus));
    }

    private void speakFirst() {
        speakThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    speak("파일을 분석하고 있습니다.");
                    Thread.sleep(1000);
                    speakFocus();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

    private boolean isMp4File(String filePath) {
        String fileExtension = getFileExtension(filePath);
        return fileExtension.equalsIgnoreCase("mp4");
    }

    private boolean isTxtFile(String filePath) {
        String fileExtension = getFileExtension(filePath);
        return fileExtension.equalsIgnoreCase("txt");
    }

    private void requestSpeech(String content, int code) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, content);
        startActivityForResult(intent, code);
    }

    private String getFileExtension(String filePath) {
        String fileExtension = "";
        int dotIndex = filePath.lastIndexOf(".");
        if (dotIndex != -1 && dotIndex < filePath.length() - 1) {
            fileExtension = filePath.substring(dotIndex + 1);
        }
        return fileExtension;
    }

    private String getPhoneNumber(String input) {
        ArrayList<String> numberArray = new ArrayList<>();

        String hr1 = "^(\\d{2,3}-)?\\d{3,4}-\\d{4}$"; //하이픈이 있는 개인번호 정규식
        String nr1 = "^01[016789]\\d{7,8}$";          //하이픈이 없는 개인번호 정규식
        String hr2 = "^(02|0[3-9]\\d{1,2})-\\d{3,4}-\\d{4}$";  //하이픈이 있는 지역번호 정규식
        String nr2 = "^(02|0[3-9]\\d{1,2})\\d{7,8}$";          //하이픈이 없는 지역번호 정규식

        if (input != null && !input.isEmpty()) {
            String[] parts = input.split(" ");
            for (String part : parts) {
                Log.d("파트", part);
                if (part.matches(hr1)) {
                    part = part.replaceAll("-", "");
                    Log.d("파트 더함", part);
                    numberArray.add(part);
                    break;
                } else if (part.matches(nr1)) {
                    Log.d("파트 더함", part);
                    numberArray.add(part);
                    break;
                } else if (part.matches(hr2)) {
                    Log.d("파트 더함", part);
                    part = part.replaceAll("-", "");
                    numberArray.add(part);
                    break;
                } else if (part.matches(nr2)) {
                    Log.d("파트 더함", part);
                    numberArray.add(part);
                    break;
                }
            }
        }

        if (numberArray.size() == 0) {
            return null;
        } else if (numberArray.size() > 1) {
            //이후 업데이트를 통해 여러개의 전화번호를 처리할 때 사용
            return numberArray.get(0);
        } else
            return numberArray.get(0);
    }

    public String getDateTime(String input) {
        String mon = "\\d{1,2}월";
        String day = "\\d{1,2}일";
        String hur = "\\d{1,2}시";
        String min = "\\d{1,2}분";
        String dateTime;

        String[] parts = input.split(" ");
        StringBuilder dt = new StringBuilder();
        for (String part : parts) {
            Log.d("파트", part);
            if (part.matches(mon)) {
                dt.append(part);
            } else if (part.matches(day)) {
                dt.append(part);
            } else if (part.equals("오전") || part.equals("오후")) {
                dt.append(part);
            } else if (part.matches(hur)) {
                dt.append(part);
            } else if (part.matches(min)) {
                dt.append(part);
            }
        }
        dateTime = dt.toString();
        if ((dateTime.contains("오전") || dateTime.contains("오후") && !dateTime.contains("시")))
            dateTime = null;
        Log.d("결과", dateTime);
        return dateTime;
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
                        Toast toast = Toast.makeText(TextActivity.this, "→", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(TextActivity.this, "←", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(TextActivity.this, "↓", Toast.LENGTH_SHORT);
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
                        Toast toast = Toast.makeText(TextActivity.this, "↑", Toast.LENGTH_SHORT);
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
