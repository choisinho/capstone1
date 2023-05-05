package app.bqlab.febblindrecorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class JoystickView extends SurfaceView implements SurfaceHolder.Callback {
    private float centerX;
    private float centerY;
    private float baseRadius;
    private float hatRadius;
    private JoystickListener joystickCallback;

    public JoystickView(Context context) {
        super(context);
        init(null, 0);
        setFocusable(true);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
        setFocusable(true);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
        setFocusable(true);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // SurfaceHolder 객체 생성
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 조이스틱 초기 위치 설정
        centerX = getWidth() / 2;
        centerY = getHeight() / 2;
        baseRadius = Math.min(getWidth(), getHeight()) / 3;
        hatRadius = Math.min(getWidth(), getHeight()) / 6;
        drawJoystick(centerX, centerY);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    private void drawJoystick(float newX, float newY) {
        if (getHolder().getSurface().isValid()) {
            Canvas canvas = getHolder().lockCanvas();
            // 배경 그리기
            canvas.drawColor(Color.WHITE);
            Paint baseCircle = new Paint();
            baseCircle.setARGB(255, 100, 100, 100);
            canvas.drawCircle(centerX, centerY, baseRadius, baseCircle);
            // 조이스틱 그리기
            Paint hatCircle = new Paint();
            hatCircle.setARGB(255, 0, 0, 255);

            canvas.drawCircle(newX, newY, hatRadius, hatCircle);
            getHolder().unlockCanvasAndPost(canvas);
        }
    }

    // 이벤트 처리 메서드를 추가
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (joystickCallback == null) {
            return true;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // 조이스틱 이동 시
                float displacement = (float) Math.sqrt(Math.pow(event.getX() - centerX, 2) +
                        Math.pow(event.getY() - centerY, 2));
                if (displacement < baseRadius) {
                    joystickCallback.onJoystickMoved((event.getX() - centerX) / baseRadius,
                            (event.getY() - centerY) / baseRadius);
                    drawJoystick(event.getX(), event.getY());
                } else {
                    float ratio = baseRadius / displacement;
                    float constrainedX = centerX + (event.getX() - centerX) * ratio;
                    float constrainedY = centerY + (event.getY() - centerY) * ratio;
                    joystickCallback.onJoystickMoved((constrainedX - centerX) / baseRadius,
                            (constrainedY - centerY) / baseRadius);
                    drawJoystick(constrainedX, constrainedY);
                }
                break;
            case MotionEvent.ACTION_UP:
                // 조이스틱에서 손을 뗀 경우
                joystickCallback.onJoystickReleased(getId());
                drawJoystick(centerX, centerY);
                break;
            default:
                return false;
        }
        return true;
    }

    public void setJoystickListener(JoystickListener listener) {
        this.joystickCallback = listener;
    }

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent);
        void onJoystickReleased(int id);
    }
}
