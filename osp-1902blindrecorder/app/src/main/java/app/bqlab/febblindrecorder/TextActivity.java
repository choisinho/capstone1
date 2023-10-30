package app.bqlab.febblindrecorder;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
    final int GET_MESSAGE = 3;
    final int GET_KAKAO = 4;
    final int SPEECH_TO_TEXT = 1000;
    //variables
    int focus, soundDisable;
    String fileName, fileDir;
    String viewerContent, dateTime;
    String iFilePath, iFlag;
    ArrayList<String> speech, phoneNumbers;
    //objects
    File mFile;
    TextToSpeech mTTS;
    HashMap<String, String> mTTSMap;
    SoundPool mSoundPool;
    Thread speakThread;
    GestureDetector gestureDetector;
    AlarmManager alarmManager;
    PendingIntent pendingIntent;
    //layouts
    LinearLayout textBody;
    List<View> textBodyButtons;
    Button textBodyViewer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);
        final ProgressBar loading = findViewById(R.id.text_loading);
        ViewTreeObserver viewTreeObserver = findViewById(android.R.id.content).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (loading != null)
                    loading.setVisibility(View.GONE);
                findViewById(android.R.id.content).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                init();
                resetFocus();
                setupSoundPool();
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
                            if (speech.get(0).equals("설정")) {
                                try {
                                    startAlarm(setupAlarm(stringToDate(viewerContent)));
                                } catch (ParseException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case GET_PHONE:
                            shutupTTS();
                            if (speech.get(0).equals("전화")) {
                                Intent intent = new Intent(Intent.ACTION_DIAL);
                                intent.setData(Uri.parse("tel:" + phoneNumbers.get(0)));
                                startActivity(intent);
                            }
                            break;
                        case GET_MESSAGE:
                            shutupTTS();
                            if (speech.get(0).equals("문자")) {
                                Intent intent = new Intent(Intent.ACTION_SENDTO);
                                intent.setData(Uri.parse("smsto:" + phoneNumbers.get(0)));
                                intent.putExtra("sms_body", viewerContent);
                                startActivity(intent);
                            }
                            break;
                        case GET_KAKAO:
                            shutupTTS();
                            if (speech.get(0).equals("카톡")) {
                                Intent intent = new Intent(Intent.ACTION_SEND);
                                intent.setType("text/plain");
                                intent.putExtra(Intent.EXTRA_TEXT, viewerContent);
                                startActivity(intent);
                            }
                            break;
                    }
                }
            }
        }
    }

    private void init() {
        //layout
        textBody = findViewById(R.id.text_body);
        textBodyButtons = new ArrayList<View>();
        for (int i = 0; i < textBody.getChildCount(); i++)
            textBodyButtons.add(textBody.getChildAt(i));

        //init
        iFlag = getIntent().getStringExtra("flag");
        iFilePath = getIntent().getStringExtra("filePath");

        //file
        try {
            fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
            fileName = iFilePath.replace(fileDir + File.separator, "");
            mFile = new File(fileDir, fileName);
            Log.d("filePath", iFilePath);
        } catch (NullPointerException e) {
            iFlag = getSharedPreferences("ALARM_RES", Context.MODE_PRIVATE).getString("flag", "");
            iFilePath = getSharedPreferences("ALARM_RES", Context.MODE_PRIVATE).getString("filePath", "");
            fileDir = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "음성메모장" + File.separator + getSharedPreferences("setting", MODE_PRIVATE).getString("SAVE_FOLDER_NAME", "");
            fileName = iFilePath.replace(fileDir + File.separator, "");
            mFile = new File(fileDir, fileName);
            Log.d("filePath", iFilePath);
        }

        //gesture
        gestureDetector = new GestureDetector(this, new TextActivity.MyGestureListener());

        //layout
        if (isMp4File(iFilePath)) {
            //mp4 to txt
        } else if (isTxtFile(iFilePath)) {
            String s = ""; // txt 파일의 내용을 저장할 변수
            try {
                File file = new File(iFilePath);
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
        viewerContent = textBodyViewer.getText().toString(); //위치 바꾸지 말것
        //for test
//        textBodyViewer.setText("2023년 10월 29일 오후 11시");
//        viewerContent = textBodyViewer.getText().toString();
//        try {
////            startAlarm(setupAlarm(stringToDate(viewerContent)));
//            startAlarm(setupAlarm(stringToDate("오후1시57분")));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
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
        if (iFlag.equals("list")) {
            if (isMp4File(iFilePath)) {
                File file = new File(fileDir, fileName);
                Intent i = new Intent(this, PlayActivity.class);
                i.putExtra("filePath", file.getPath());
                i.putExtra("flag", iFlag);
                startActivity(i);
            } else if (isTxtFile(iFilePath)) {
                File file = new File(fileDir, fileName);
                Intent i = new Intent(this, FilesActivity.class);
                i.putExtra("filePath", file.getPath());
                startActivity(i);
            } else
                speak("잠시후 다시 시도해주세요.");
            finish();
        } else if (iFlag.equals("name")) {
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
                            if (dateTime == null || dateTime.equals("")) {
                                speak("일정 관련 정보가 인식되지 않았습니다.");
                            } else {
                                speak("인식된 일정은 " + dateTime + " 입니다. 알람을 설정하려면 잠시후 설정이라고 말씀하세요.");
                                Thread.sleep(10000);
                                requestSpeech("알람을 설정하려면 설정이라고 말씀하세요.");
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
                            phoneNumbers = getPhoneNumbers(viewerContent); //일단은 여러 번호를 받게끔 만듦
                            if (phoneNumbers.size() == 0) {
                                speak("전화번호가 인식되지 않았습니다.");
                            } else {
                                speak("인식된 전화번호는 " + phoneNumbers.get(0) + " 입니다. 전화를 원하시면 잠시 후 전화라고 말씀하세요.");
                                Thread.sleep(10000);
                                requestSpeech("전화를 원하시면 전화라고 말씀하세요.");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                speakThread.start();
                break;
            case GET_MESSAGE:
                speakThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            phoneNumbers = getPhoneNumbers(viewerContent); //일단은 여러 번호를 받게끔 만듦
                            if (phoneNumbers.size() == 0) {
                                speak("전화번호가 인식되지 않았습니다.");
                            } else {
                                speak("인식된 전화번호는 " + phoneNumbers.get(0) + " 입니다. 문자를 원하시면 잠시 후 문자라고 말씀하세요.");
                                Thread.sleep(10000);
                                requestSpeech("문자를 원하시면 문자라고 말씀하세요.");
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                speakThread.start();
                break;
            case GET_KAKAO:
                speakThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            speak("카톡으로 공유하시려면 잠시 후 카톡이라고 말씀하세요.");
                            Thread.sleep(10000);
                            requestSpeech("공유를 원하시면 카톡이라고 말씀하세요.");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                });
                speakThread.start();
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

    private void startAlarm(boolean alarmRes) {
        if (alarmRes) {
            SharedPreferences alarmPref = getSharedPreferences("ALARM_RES", Context.MODE_PRIVATE);
            SharedPreferences.Editor prefEditor = alarmPref.edit();
            prefEditor.putString("flag", iFlag);
            prefEditor.putString("filePath", iFilePath);
            prefEditor.apply();
            speak(dateTime + "에 알람이 울립니다.");
        } else
            speak("알람을 설정할 수 없습니다.");
    }

    private boolean setupAlarm(Date date) {
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getApplicationContext(), AlarmReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
//        calendar.setTime(delayForTest());
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) { //설정시간이 현재시간 보다 이전일 경우
            return false;
        } else if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
            return true;
        } else
            return false;
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

    private void requestSpeech(String msg) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, msg);
        startActivityForResult(intent, SPEECH_TO_TEXT);
    }

    private String getFileExtension(String filePath) {
        String fileExtension = "";
        int dotIndex = filePath.lastIndexOf(".");
        if (dotIndex != -1 && dotIndex < filePath.length() - 1) {
            fileExtension = filePath.substring(dotIndex + 1);
        }
        return fileExtension;
    }

    private ArrayList<String> getPhoneNumbers(String input) {
        ArrayList<String> numberArray = new ArrayList<>();

        String hr1 = "\\d{2,3}-\\d{3,4}-\\d{4}"; // 하이픈이 있는 개인번호 정규식
        String nr1 = "01[016789]\\d{7,8}";       // 하이픈이 없는 개인번호 정규식
        String hr2 = "(02|0[3-9]\\d{1,2})-\\d{3,4}-\\d{4}";  // 하이픈이 있는 지역번호 정규식
        String nr2 = "(02|0[3-9]\\d{1,2})\\d{7,8}";          // 하이픈이 없는 지역번호 정규식

        if (input != null && !input.isEmpty()) {
            Pattern pattern = Pattern.compile(hr1 + "|" + nr1 + "|" + hr2 + "|" + nr2);
            Matcher matcher = pattern.matcher(input);
            while (matcher.find()) {
                Log.d("파트", matcher.group());
                numberArray.add(matcher.group().replaceAll("-", ""));
            }
        }

        return numberArray;
    }

    private String getDateTime(String input) {
        String dateTime;
        String[] parts = input.split(" ");
        StringBuilder dt = new StringBuilder();
        for (String part : parts) {
            if (part.contains("년"))
                dt.append(part);
            else if (part.contains("월"))
                dt.append(part);
            else if (part.contains("일"))
                dt.append(part);
            else if (part.equals("오전") || part.equals("오후"))
                dt.append(part);
            else if (part.contains("시"))
                dt.append(part);
            else if (part.contains("분"))
                dt.append(part);
        }
        dateTime = dt.toString();
        if ((dateTime.contains("오전") || dateTime.contains("오후")) && !dateTime.contains("시")) {
            Log.d("dateTime is wrong", dateTime);
            dateTime = null;
        }
        try {
            Log.d("결과", dateTime);
        } catch (NullPointerException e) {
            e.fillInStackTrace();
        }
        return dateTime;
    }

    private Date stringToDate(String input) throws java.text.ParseException {
        input = toDateForm(input);
        dateTime = input; //사용자가 적은 형식에서 앱에서 사용하는 구체적인 형식으로 변경시킴
        DateFormat format = new SimpleDateFormat("yyyy년MM월dd일HH시mm분", Locale.KOREAN);
        Calendar calendar = Calendar.getInstance();
        Date date = format.parse(input);
        calendar.setTime(date);
        return calendar.getTime();
    }

    private Date delayForTest() { //for test
        Date current = new Date();
        Date delay = new Date(current.getTime() + (long) 60000);
        Log.d("현재시각이 언제길래", Calendar.getInstance().getTime().toString());
        Log.d("설정시각이 언제길래", String.valueOf(delay.getTime()));
        return delay;
    }

    private String toDateForm(String input) {
        String date = "yyyy년MM월dd일HH시mm분";

        // 날짜 및 시간 요소 추출
        String year = "";
        String month = "";
        String day = "";
        String hour = "";
        String minute = "";

        // 년도 추출
        Pattern yearPattern = Pattern.compile("\\d{4}년");
        Matcher yearMatcher = yearPattern.matcher(input);
        if (yearMatcher.find()) {
            year = yearMatcher.group(0);
            date = date.replace("yyyy", year.substring(0, 4));
        }
        Log.d("년", date);

        // 월 추출
        Pattern monthPattern = Pattern.compile("\\d{1,2}월");
        Matcher monthMatcher = monthPattern.matcher(input);
        if (monthMatcher.find()) {
            month = monthMatcher.group(0);
            date = date.replace("MM", String.format("%02d", Integer.parseInt(month.substring(0, month.length() - 1))));
        }
        Log.d("월", date);

        // 일 추출
        Pattern dayPattern = Pattern.compile("\\d{1,2}일");
        Matcher dayMatcher = dayPattern.matcher(input);
        if (dayMatcher.find()) {
            day = dayMatcher.group(0);
            date = date.replace("dd", String.format("%02d", Integer.parseInt(day.substring(0, day.length() - 1))));
        }
        Log.d("일", date);

        // 시간 추출
        Pattern hourPattern = Pattern.compile("\\d{1,2}시");
        Matcher hourMatcher = hourPattern.matcher(input);
        if (hourMatcher.find()) {
            hour = hourMatcher.group(0);
            // "오후" 포함되어 있을 경우 시간에 12를 더함
            if (input.contains("오후") && Integer.parseInt(hour.replace("시", "")) <= 12) {
                int hourValue = Integer.parseInt(hour.substring(0, hour.length() - 1));
                date = date.replace("HH", String.format("%02d", hourValue + 12));
            } else {
                int i = Integer.parseInt(hour.substring(0, hour.length() - 1));
                date = date.replace("HH", String.format("%02d", i));
            }
        }
        Log.d("시", date);

        // 분 추출
        Pattern minutePattern = Pattern.compile("\\d{1,2}분");
        Matcher minuteMatcher = minutePattern.matcher(input);
        if (minuteMatcher.find()) {
            minute = minuteMatcher.group(0);
            date = date.replace("mm", String.format("%02d", Integer.parseInt(minute.substring(0, minute.length() - 1))));
        }
        Log.d("분", date);

        Calendar calendar = Calendar.getInstance();
        date = date.replace("yyyy", String.valueOf(calendar.get(Calendar.YEAR)));
        date = date.replace("MM", String.valueOf(calendar.get(Calendar.MONTH) + 1));
        date = date.replace("dd", String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH)));
        date = date.replace("HH", String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY)));
        date = date.replace("mm", "00");
        Log.d("결과", date);

        return date;
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
