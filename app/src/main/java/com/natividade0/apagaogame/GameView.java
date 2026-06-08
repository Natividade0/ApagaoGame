package com.natividade0.apagaogame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    private static final float BASE_GRAVITY = 2.35f;
    private static final float START_HEIGHT = 0.08f;
    private static final float MIN_PULL = 24f;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Level> levels = new ArrayList<>();

    private Thread thread;
    private volatile boolean running;

    private int screenW;
    private int screenH;
    private float horizonY;
    private float nearY;
    private float launchScreenX;
    private float launchScreenY;
    private float gameTime;

    private Scene scene = Scene.MENU;
    private int levelIndex;
    private int attempts;
    private int stars;

    private boolean dragging;
    private float dragX;
    private float dragY;

    private float pX;
    private float pY;
    private float pZ;
    private float vX;
    private float vY;
    private float vZ;
    private float spin;
    private float stopTimer;
    private float effectTimer;
    private float cameraShake;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setupPaints();
        setupLevels();
    }

    private void setupPaints() {
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        smallTextPaint.setColor(Color.WHITE);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void setupLevels() {
        levels.add(new Level("Treino de Flecha", Mode.BULLSEYE, Projectile.ARROW,
                0.00f, 0.68f, 0.52f, 0f, 1.00f, 0.12f));
        levels.add(new Level("Bolinha no Lixo", Mode.BIN, Projectile.PAPER,
                -0.08f, 0.66f, 0.16f, 0f, 0.92f, 0.34f));
        levels.add(new Level("Pedra na Lampada", Mode.LAMP, Projectile.STONE,
                0.12f, 0.74f, 0.88f, -0.04f, 1.05f, 0.22f));
        levels.add(new Level("Alvo com Vento", Mode.BULLSEYE, Projectile.ARROW,
                -0.16f, 0.76f, 0.55f, 0.12f, 1.00f, 0.12f));
        levels.add(new Level("Copo no Fundo", Mode.CUP, Projectile.BALL,
                0.10f, 0.72f, 0.18f, 0f, 0.94f, 0.55f));
        levels.add(new Level("Mesa no Caminho", Mode.BIN, Projectile.BALL,
                0.16f, 0.78f, 0.16f, 0f, 0.96f, 0.58f)
                .block(0.00f, 0.46f, 0.42f, 0.16f, 0.17f));
        levels.add(new Level("Lampada Distante", Mode.LAMP, Projectile.STONE,
                -0.12f, 0.84f, 0.90f, -0.08f, 1.08f, 0.24f)
                .block(0.18f, 0.58f, 0.22f, 0.13f, 0.45f));
        levels.add(new Level("Trickshot 3D", Mode.BULLSEYE, Projectile.BALL,
                0.20f, 0.86f, 0.45f, 0.06f, 0.98f, 0.70f)
                .block(-0.18f, 0.45f, 0.22f, 0.16f, 0.34f)
                .moving(0.12f, 1.15f));
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        calculateScreen();
        resetLevel(false);
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        calculateScreen();
        resetLevel(false);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        pause();
    }

    public void resume() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this, "MiraReal25DLoop");
        thread.start();
    }

    public void pause() {
        running = false;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.033f, (now - last) / 1_000_000_000f);
            last = now;
            update(dt);
            drawFrame();
            try {
                Thread.sleep(16L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void calculateScreen() {
        screenW = Math.max(1, getWidth());
        screenH = Math.max(1, getHeight());
        horizonY = screenH * 0.285f;
        nearY = screenH * 0.895f;
        launchScreenX = screenW * 0.50f;
        launchScreenY = screenH * 0.815f;
        textPaint.setTextSize(Math.max(42f, screenH * 0.065f));
        smallTextPaint.setTextSize(Math.max(24f, screenH * 0.034f));
    }

    private Level level() {
        return levels.get(levelIndex);
    }

    private void update(float dt) {
        gameTime += dt;
        effectTimer = Math.max(0f, effectTimer - dt);
        cameraShake = Math.max(0f, cameraShake - dt * 2.5f);

        if (scene != Scene.FLYING) {
            return;
        }

        Level level = level();
        vX += level.wind * dt;
        vY -= BASE_GRAVITY * level.gravityScale * dt;
        vX *= Math.max(0.80f, 1f - level.drag * dt);
        vZ *= Math.max(0.84f, 1f - level.drag * 0.55f * dt);
        spin += (vX * 140f + vZ * 60f) * dt;

        pX += vX * dt;
        pY += vY * dt;
        pZ += vZ * dt;

        if (pY <= 0f) {
            pY = 0f;
            if (vY < 0f) {
                vY = -vY * level.bounce;
                vX *= 0.82f;
                vZ *= 0.86f;
                cameraShake = Math.max(cameraShake, 0.08f);
            }
            if (Math.abs(vY) < 0.10f) {
                vY = 0f;
            }
        }

        collideBlocks(level);

        if (hitsTarget(level)) {
            stars = attempts <= 1 ? 3 : attempts <= 3 ? 2 : 1;
            scene = Scene.SUCCESS;
            effectTimer = 1.0f;
            cameraShake = 0.35f;
            return;
        }

        float speed = Math.abs(vX) + Math.abs(vY) + Math.abs(vZ);
        if (pY <= 0.01f && speed < 0.22f) {
            stopTimer += dt;
        } else {
            stopTimer = 0f;
        }

        if (stopTimer > 0.75f || pZ > 1.18f || pZ < -0.10f || Math.abs(pX) > 1.35f) {
            scene = Scene.FAILED;
            effectTimer = 0.45f;
        }
    }

    private void collideBlocks(Level level) {
        for (Block block : level.blocks) {
            boolean closeZ = Math.abs(pZ - block.z) < block.depth;
            boolean closeX = Math.abs(pX - block.x) < block.width;
            if (closeZ && closeX && pY < block.height) {
                if (Math.abs(vZ) > Math.abs(vX)) {
                    vZ = -vZ * Math.max(0.25f, level.bounce);
                } else {
                    vX = -vX * Math.max(0.25f, level.bounce);
                }
                vY = Math.max(vY, 0.30f);
                pZ -= 0.015f;
                cameraShake = 0.18f;
            }
        }
    }

    private boolean hitsTarget(Level level) {
        float targetX = currentTargetX(level);
        float dz = Math.abs(pZ - level.targetZ);
        float dx = Math.abs(pX - targetX);
        float dy = Math.abs(pY - level.targetH);

        if (level.mode == Mode.BIN || level.mode == Mode.CUP) {
            return dz < 0.075f && dx < 0.145f && dy < 0.22f && vY < 0.65f;
        }
        if (level.mode == Mode.LAMP) {
            return dz < 0.075f && dx < 0.115f && dy < 0.18f;
        }
        return dz < 0.080f && dx < 0.125f && dy < 0.20f;
    }

    private void resetLevel(boolean resetAttempts) {
        pX = 0f;
        pY = START_HEIGHT;
        pZ = 0f;
        vX = 0f;
        vY = 0f;
        vZ = 0f;
        spin = 0f;
        stopTimer = 0f;
        effectTimer = 0f;
        cameraShake = 0f;
        dragging = false;
        dragX = launchScreenX;
        dragY = launchScreenY;
        if (resetAttempts) {
            attempts = 0;
            stars = 0;
        }
    }

    private void shoot() {
        float pull = distance(dragX, dragY, launchScreenX, launchScreenY);
        if (pull < MIN_PULL) {
            dragging = false;
            return;
        }
        float max = maxPull();
        float power = clamp(pull / max, 0.08f, 1f);
        float side = clamp((launchScreenX - dragX) / max, -1f, 1f);
        float down = clamp((dragY - launchScreenY) / max, -0.35f, 1f);

        Level level = level();
        float mult = projectileSpeed(level.projectile);
        vX = side * 0.92f * mult;
        vZ = (0.62f + power * 1.72f) * mult;
        vY = (0.52f + power * 1.25f + down * 0.25f) * level.arc;
        pX = 0f;
        pY = START_HEIGHT;
        pZ = 0f;
        attempts++;
        stopTimer = 0f;
        dragging = false;
        scene = Scene.FLYING;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (scene == Scene.MENU) {
                scene = Scene.AIMING;
                resetLevel(true);
                return true;
            }
            if (scene == Scene.SUCCESS) {
                if (levelIndex < levels.size() - 1) {
                    levelIndex++;
                    attempts = 0;
                    scene = Scene.AIMING;
                    resetLevel(false);
                } else {
                    scene = Scene.FINISHED;
                }
                return true;
            }
            if (scene == Scene.FAILED) {
                scene = Scene.AIMING;
                resetLevel(false);
                return true;
            }
            if (scene == Scene.FINISHED) {
                levelIndex = 0;
                attempts = 0;
                scene = Scene.MENU;
                resetLevel(true);
                return true;
            }
            if (scene == Scene.FLYING) {
                return true;
            }
            dragging = true;
            dragX = x;
            dragY = y;
            clampDrag();
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE && dragging) {
            dragX = x;
            dragY = y;
            clampDrag();
            return true;
        }

        if ((event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) && dragging) {
            shoot();
            return true;
        }
        return true;
    }

    private void clampDrag() {
        float pull = distance(dragX, dragY, launchScreenX, launchScreenY);
        float max = maxPull();
        if (pull > max) {
            float dx = dragX - launchScreenX;
            float dy = dragY - launchScreenY;
            dragX = launchScreenX + dx / pull * max;
            dragY = launchScreenY + dy / pull * max;
        }
    }

    private float maxPull() {
        return Math.max(320f, Math.min(screenW * 0.38f, screenH * 0.52f));
    }

    private void drawFrame() {
        Canvas canvas = null;
        try {
            canvas = holder.lockCanvas();
            if (canvas != null) {
                drawGame(canvas);
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    private void drawGame(Canvas canvas) {
        float shakeX = cameraShake > 0f ? (float) Math.sin(gameTime * 75f) * cameraShake * 18f : 0f;
        float shakeY = cameraShake > 0f ? (float) Math.cos(gameTime * 92f) * cameraShake * 12f : 0f;
        canvas.save();
        canvas.translate(shakeX, shakeY);

        drawSkyAndRoom(canvas);
        drawPerspectiveFloor(canvas);
        drawScenery(canvas, level());
        drawBlocks(canvas, level());
        drawTarget(canvas, level());
        if (scene == Scene.AIMING && dragging) {
            drawAim(canvas);
        }
        drawProjectile(canvas, level().projectile);
        drawLauncher(canvas);
        if (effectTimer > 0f) {
            drawHitEffect(canvas, level());
        }
        canvas.restore();

        drawHud(canvas);
        drawOverlay(canvas);
    }

    private void drawSkyAndRoom(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(34, 38, 48));
        canvas.drawRect(0, 0, screenW, horizonY, paint);
        paint.setColor(Color.rgb(24, 28, 36));
        canvas.drawRect(0, horizonY, screenW, screenH, paint);

        paint.setColor(Color.rgb(44, 50, 64));
        Path leftWall = new Path();
        leftWall.moveTo(0, horizonY);
        leftWall.lineTo(screenW * 0.5f, horizonY);
        leftWall.lineTo(0, screenH);
        leftWall.close();
        canvas.drawPath(leftWall, paint);

        paint.setColor(Color.rgb(39, 45, 58));
        Path rightWall = new Path();
        rightWall.moveTo(screenW, horizonY);
        rightWall.lineTo(screenW * 0.5f, horizonY);
        rightWall.lineTo(screenW, screenH);
        rightWall.close();
        canvas.drawPath(rightWall, paint);
    }

    private void drawPerspectiveFloor(Canvas canvas) {
        Path floor = new Path();
        floor.moveTo(screenW * 0.5f, horizonY);
        floor.lineTo(screenW, screenH);
        floor.lineTo(0, screenH);
        floor.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(54, 58, 66));
        canvas.drawPath(floor, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(80, 255, 255, 255));
        for (int i = -5; i <= 5; i++) {
            float nearX = screenW * 0.5f + i * screenW * 0.12f;
            canvas.drawLine(screenW * 0.5f, horizonY, nearX, screenH, paint);
        }
        for (int i = 1; i <= 9; i++) {
            float z = i / 10f;
            float y = floorY(z);
            float half = screenW * (0.52f - z * 0.35f);
            canvas.drawLine(screenW * 0.5f - half, y, screenW * 0.5f + half, y, paint);
        }
    }

    private void drawScenery(Canvas canvas, Level level) {
        paint.setStyle(Paint.Style.FILL);
        if (level.mode == Mode.BIN || level.mode == Mode.CUP) {
            RectF table = tableRect(0.55f);
            paint.setColor(Color.rgb(88, 58, 38));
            canvas.drawRoundRect(table, 16f, 16f, paint);
            paint.setColor(Color.rgb(120, 78, 48));
            canvas.drawRect(table.left, table.top, table.right, table.top + 18f, paint);
        }
        if (level.mode == Mode.LAMP) {
            paint.setColor(Color.rgb(24, 24, 30));
            canvas.drawRect(screenW * 0.14f, 0, screenW * 0.86f, horizonY * 0.96f, paint);
            paint.setColor(Color.rgb(70, 72, 82));
            canvas.drawRect(screenW * 0.18f, 24f, screenW * 0.82f, horizonY * 0.92f, paint);
        }
    }

    private RectF tableRect(float z) {
        float y = floorY(z) + 10f;
        float s = scale(z);
        float w = screenW * 0.62f * s;
        float h = screenH * 0.11f * s;
        return new RectF(screenW * 0.5f - w / 2f, y - h, screenW * 0.5f + w / 2f, y + h * 0.25f);
    }

    private void drawBlocks(Canvas canvas, Level level) {
        for (Block block : level.blocks) {
            float cx = sx(block.x, block.z);
            float base = floorY(block.z);
            float s = scale(block.z);
            float w = screenW * block.width * s;
            float h = screenH * block.height * 0.60f * s;
            RectF r = new RectF(cx - w, base - h, cx + w, base);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(105, 111, 126));
            canvas.drawRoundRect(r, 10f, 10f, paint);
            paint.setColor(Color.rgb(70, 74, 88));
            canvas.drawRect(r.left, r.bottom - 8f * s, r.right, r.bottom, paint);
        }
    }

    private void drawTarget(Canvas canvas, Level level) {
        float tx = currentTargetX(level);
        float x = sx(tx, level.targetZ);
        float y = sy(level.targetH, level.targetZ);
        float s = scale(level.targetZ);

        if (level.mode == Mode.BULLSEYE) {
            drawBullseye(canvas, x, y, 72f * s);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(95, 70, 45));
            canvas.drawRect(x - 5f * s, y + 72f * s, x + 5f * s, floorY(level.targetZ), paint);
        } else if (level.mode == Mode.BIN) {
            drawBin(canvas, x, level.targetZ, s);
        } else if (level.mode == Mode.LAMP) {
            drawLamp(canvas, x, y, s);
        } else {
            drawCup(canvas, x, level.targetZ, s);
        }
    }

    private void drawBullseye(Canvas canvas, float x, float y, float r) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(238, 238, 238));
        canvas.drawCircle(x, y, r, paint);
        paint.setColor(Color.rgb(220, 55, 55));
        canvas.drawCircle(x, y, r * 0.74f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, r * 0.48f, paint);
        paint.setColor(Color.rgb(35, 130, 225));
        canvas.drawCircle(x, y, r * 0.23f, paint);
    }

    private void drawBin(Canvas canvas, float x, float z, float s) {
        float base = floorY(z);
        float top = sy(0.20f, z);
        float wTop = 76f * s;
        float wBottom = 48f * s;
        Path bin = new Path();
        bin.moveTo(x - wTop, top);
        bin.lineTo(x + wTop, top);
        bin.lineTo(x + wBottom, base);
        bin.lineTo(x - wBottom, base);
        bin.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(125, 70, 190, 145));
        canvas.drawPath(bin, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f * s);
        paint.setColor(Color.rgb(105, 235, 175));
        canvas.drawPath(bin, paint);
        canvas.drawLine(x - wTop, top, x + wTop, top, paint);
    }

    private void drawCup(Canvas canvas, float x, float z, float s) {
        float base = floorY(z);
        float top = sy(0.22f, z);
        float wTop = 62f * s;
        float wBottom = 36f * s;
        Path cup = new Path();
        cup.moveTo(x - wTop, top);
        cup.lineTo(x + wTop, top);
        cup.lineTo(x + wBottom, base);
        cup.lineTo(x - wBottom, base);
        cup.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(120, 90, 170, 245));
        canvas.drawPath(cup, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f * s);
        paint.setColor(Color.rgb(150, 220, 255));
        canvas.drawPath(cup, paint);
    }

    private void drawLamp(Canvas canvas, float x, float y, float s) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * s);
        paint.setColor(Color.rgb(150, 150, 160));
        canvas.drawLine(x, 0, x, y - 42f * s, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(55, 255, 225, 90));
        canvas.drawCircle(x, y, 76f * s, paint);
        paint.setColor(Color.rgb(255, 226, 92));
        canvas.drawCircle(x, y, 34f * s, paint);
        paint.setColor(Color.rgb(88, 80, 58));
        canvas.drawRect(x - 20f * s, y - 48f * s, x + 20f * s, y - 28f * s, paint);
    }

    private void drawAim(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f);
        paint.setColor(Color.rgb(255, 202, 76));
        canvas.drawLine(launchScreenX, launchScreenY, dragX, dragY, paint);

        float power = clamp(distance(dragX, dragY, launchScreenX, launchScreenY) / maxPull(), 0f, 1f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 202, 76));
        canvas.drawRoundRect(new RectF(40, 106, 40 + screenW * 0.34f * power, 132), 12f, 12f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(40, 106, 40 + screenW * 0.34f, 132), 12f, 12f, paint);

        simulatePreview(canvas);
    }

    private void simulatePreview(Canvas canvas) {
        float pull = distance(dragX, dragY, launchScreenX, launchScreenY);
        float max = maxPull();
        float power = clamp(pull / max, 0.08f, 1f);
        float side = clamp((launchScreenX - dragX) / max, -1f, 1f);
        float down = clamp((dragY - launchScreenY) / max, -0.35f, 1f);
        Level level = level();
        float simX = 0f;
        float simY = START_HEIGHT;
        float simZ = 0f;
        float simVX = side * 0.92f * projectileSpeed(level.projectile);
        float simVZ = (0.62f + power * 1.72f) * projectileSpeed(level.projectile);
        float simVY = (0.52f + power * 1.25f + down * 0.25f) * level.arc;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 255, 255, 255));
        for (int i = 0; i < 42; i++) {
            float dt = 0.055f;
            simVX += level.wind * dt;
            simVY -= BASE_GRAVITY * level.gravityScale * dt;
            simX += simVX * dt;
            simY += simVY * dt;
            simZ += simVZ * dt;
            if (simY < 0f) {
                simY = 0f;
                simVY = -simVY * level.bounce;
                simVZ *= 0.86f;
            }
            if (simZ > 1.12f || Math.abs(simX) > 1.2f) {
                break;
            }
            float s = scale(simZ);
            canvas.drawCircle(sx(simX, simZ), sy(simY, simZ), Math.max(2f, 5f * s), paint);
        }
    }

    private void drawProjectile(Canvas canvas, Projectile type) {
        float x = sx(pX, pZ);
        float y = sy(pY, pZ);
        float s = scale(pZ);
        float radius = projectileRadius(type) * s;

        drawShadow(canvas, pX, pZ, radius);

        if (type == Projectile.ARROW) {
            canvas.save();
            canvas.rotate((float) Math.toDegrees(Math.atan2(-vY, vZ)) * 0.35f, x, y);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(7f * s);
            paint.setColor(Color.rgb(225, 182, 86));
            canvas.drawLine(x - 48f * s, y, x + 44f * s, y, paint);
            paint.setStyle(Paint.Style.FILL);
            Path head = new Path();
            head.moveTo(x + 60f * s, y);
            head.lineTo(x + 32f * s, y - 16f * s);
            head.lineTo(x + 32f * s, y + 16f * s);
            head.close();
            paint.setColor(Color.rgb(240, 240, 240));
            canvas.drawPath(head, paint);
            canvas.restore();
            return;
        }

        canvas.save();
        canvas.rotate(spin, x, y);
        paint.setStyle(Paint.Style.FILL);
        if (type == Projectile.PAPER) {
            paint.setColor(Color.rgb(235, 235, 225));
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * s);
            paint.setColor(Color.rgb(166, 166, 160));
            canvas.drawLine(x - radius * 0.6f, y, x + radius * 0.5f, y + radius * 0.3f, paint);
            canvas.drawLine(x - radius * 0.2f, y + radius * 0.6f, x + radius * 0.4f, y - radius * 0.6f, paint);
        } else if (type == Projectile.STONE) {
            paint.setColor(Color.rgb(112, 116, 124));
            canvas.drawCircle(x, y, radius, paint);
            paint.setColor(Color.rgb(74, 78, 86));
            canvas.drawCircle(x + radius * 0.35f, y - radius * 0.25f, radius * 0.30f, paint);
        } else {
            paint.setColor(Color.rgb(255, 145, 70));
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f * s);
            paint.setColor(Color.rgb(120, 72, 42));
            canvas.drawCircle(x, y, radius * 0.72f, paint);
        }
        canvas.restore();
    }

    private void drawShadow(Canvas canvas, float worldX, float worldZ, float radius) {
        float x = sx(worldX, worldZ);
        float y = floorY(worldZ);
        float h = clamp(pY, 0f, 1.2f);
        float alpha = clamp(150f - h * 90f, 25f, 150f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb((int) alpha, 0, 0, 0));
        RectF shadow = new RectF(x - radius * 1.5f, y - radius * 0.35f, x + radius * 1.5f, y + radius * 0.35f);
        canvas.drawOval(shadow, paint);
    }

    private void drawLauncher(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(45, 32, 24));
        canvas.drawOval(new RectF(launchScreenX - 70f, launchScreenY + 28f, launchScreenX + 70f, launchScreenY + 70f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(12f);
        paint.setColor(Color.rgb(158, 96, 48));
        canvas.drawLine(launchScreenX - 32f, launchScreenY + 20f, launchScreenX - 54f, launchScreenY - 52f, paint);
        canvas.drawLine(launchScreenX + 32f, launchScreenY + 20f, launchScreenX + 54f, launchScreenY - 52f, paint);
        paint.setStrokeWidth(6f);
        paint.setColor(Color.rgb(235, 205, 120));
        canvas.drawLine(launchScreenX - 54f, launchScreenY - 52f, launchScreenX, launchScreenY, paint);
        canvas.drawLine(launchScreenX + 54f, launchScreenY - 52f, launchScreenX, launchScreenY, paint);
    }

    private void drawHitEffect(Canvas canvas, Level level) {
        float tx = currentTargetX(level);
        float x = sx(tx, level.targetZ);
        float y = sy(level.targetH, level.targetZ);
        float progress = 1f - effectTimer;
        float r = 30f + progress * 160f;
        int alpha = (int) clamp(190f * effectTimer, 0f, 190f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f * effectTimer + 2f);
        paint.setColor(Color.argb(alpha, 255, 230, 90));
        canvas.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha, 255, 245, 160));
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2.0 / 10.0 + gameTime;
            float px = x + (float) Math.cos(a) * r * 0.75f;
            float py = y + (float) Math.sin(a) * r * 0.75f;
            canvas.drawCircle(px, py, 5f + 5f * effectTimer, paint);
        }
    }

    private void drawHud(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(160, 0, 0, 0));
        canvas.drawRoundRect(new RectF(18f, 16f, screenW - 18f, 92f), 20f, 20f, paint);

        textPaint.setTextSize(Math.max(36f, screenH * 0.052f));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Mira Real 3D Lite", 38f, 64f, textPaint);

        smallTextPaint.setTextAlign(Paint.Align.RIGHT);
        smallTextPaint.setColor(Color.rgb(225, 230, 240));
        Level level = level();
        String info = String.format(Locale.getDefault(), "Fase %d/%d  |  %s  |  Tentativas: %d", levelIndex + 1, levels.size(), level.name, attempts);
        canvas.drawText(info, screenW - 36f, 62f, smallTextPaint);

        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setColor(Color.rgb(220, 225, 235));
        String wind = Math.abs(level.wind) < 0.01f ? "sem vento" : (level.wind > 0f ? "vento para direita" : "vento para esquerda");
        canvas.drawText("Puxe o estilingue, mire pela linha pontilhada e solte. " + wind, 36f, screenH - 28f, smallTextPaint);
    }

    private void drawOverlay(Canvas canvas) {
        if (scene == Scene.AIMING || scene == Scene.FLYING) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(scene == Scene.MENU ? 210 : 178, 0, 0, 0));
        canvas.drawRect(0, 0, screenW, screenH, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.max(42f, screenH * 0.070f));
        textPaint.setColor(Color.WHITE);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        smallTextPaint.setTextSize(Math.max(24f, screenH * 0.036f));
        smallTextPaint.setColor(Color.rgb(226, 230, 238));

        if (scene == Scene.MENU) {
            canvas.drawText("MIRA REAL", screenW / 2f, screenH * 0.30f, textPaint);
            smallTextPaint.setTextSize(Math.max(26f, screenH * 0.040f));
            canvas.drawText("versao 3D Lite", screenW / 2f, screenH * 0.37f, smallTextPaint);
            drawMenuCard(canvas, screenH * 0.48f, "Perspectiva 2.5D", "o alvo parece estar no fundo da cena");
            drawMenuCard(canvas, screenH * 0.58f, "Fisica de arremesso", "forca, arco, vento, quique e sombra");
            drawMenuCard(canvas, screenH * 0.68f, "Modos variados", "flecha, cesto, lampada, copo e trickshot");
            canvas.drawText("Toque para jogar", screenW / 2f, screenH * 0.82f, textPaint);
            return;
        }

        String title;
        String subtitle;
        if (scene == Scene.SUCCESS) {
            title = "ACERTOU!";
            subtitle = "Estrelas: " + stars + "   |   toque para continuar";
        } else if (scene == Scene.FAILED) {
            title = "QUASE!";
            subtitle = "Toque para tentar de novo";
        } else {
            title = "MVP COMPLETO";
            subtitle = "Toque para voltar ao menu";
        }
        canvas.drawText(title, screenW / 2f, screenH * 0.45f, textPaint);
        canvas.drawText(subtitle, screenW / 2f, screenH * 0.53f, smallTextPaint);
    }

    private void drawMenuCard(Canvas canvas, float centerY, String title, String subtitle) {
        RectF card = new RectF(screenW * 0.12f, centerY - 36f, screenW * 0.88f, centerY + 36f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(125, 255, 255, 255));
        canvas.drawRoundRect(card, 18f, 18f, paint);
        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setColor(Color.WHITE);
        smallTextPaint.setFakeBoldText(true);
        canvas.drawText(title, card.left + 22f, centerY - 7f, smallTextPaint);
        smallTextPaint.setFakeBoldText(false);
        smallTextPaint.setColor(Color.rgb(218, 222, 232));
        canvas.drawText(subtitle, card.left + 22f, centerY + 25f, smallTextPaint);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float currentTargetX(Level level) {
        if (level.moveRange == 0f) {
            return level.targetX;
        }
        return level.targetX + (float) Math.sin(gameTime * level.moveSpeed) * level.moveRange;
    }

    private float sx(float worldX, float z) {
        return screenW * 0.5f + worldX * screenW * 0.46f * scale(z);
    }

    private float sy(float height, float z) {
        return floorY(z) - height * screenH * 0.42f * scale(z);
    }

    private float floorY(float z) {
        float clamped = clamp(z, 0f, 1.12f);
        float perspective = (float) Math.pow(clamped, 0.72f);
        return nearY - (nearY - horizonY) * perspective;
    }

    private float scale(float z) {
        return clamp(1.15f - z * 0.75f, 0.32f, 1.15f);
    }

    private float projectileRadius(Projectile projectile) {
        if (projectile == Projectile.ARROW) {
            return 13f;
        }
        if (projectile == Projectile.PAPER) {
            return 22f;
        }
        if (projectile == Projectile.STONE) {
            return 20f;
        }
        return 21f;
    }

    private float projectileSpeed(Projectile projectile) {
        if (projectile == Projectile.ARROW) {
            return 1.10f;
        }
        if (projectile == Projectile.PAPER) {
            return 0.94f;
        }
        if (projectile == Projectile.STONE) {
            return 1.02f;
        }
        return 0.98f;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Scene {
        MENU,
        AIMING,
        FLYING,
        SUCCESS,
        FAILED,
        FINISHED
    }

    private enum Mode {
        BULLSEYE,
        BIN,
        LAMP,
        CUP
    }

    private enum Projectile {
        ARROW,
        PAPER,
        STONE,
        BALL
    }

    private static class Level {
        final String name;
        final Mode mode;
        final Projectile projectile;
        final float targetX;
        final float targetZ;
        final float targetH;
        final float wind;
        final float gravityScale;
        final float bounce;
        final float drag;
        final float arc;
        float moveRange;
        float moveSpeed;
        final List<Block> blocks = new ArrayList<>();

        Level(String name, Mode mode, Projectile projectile, float targetX, float targetZ,
              float targetH, float wind, float gravityScale, float bounce) {
            this.name = name;
            this.mode = mode;
            this.projectile = projectile;
            this.targetX = targetX;
            this.targetZ = targetZ;
            this.targetH = targetH;
            this.wind = wind;
            this.gravityScale = gravityScale;
            this.bounce = bounce;
            this.drag = projectile == Projectile.PAPER ? 0.17f : 0.055f;
            this.arc = projectile == Projectile.ARROW ? 0.93f : projectile == Projectile.STONE ? 1.05f : 1.0f;
        }

        Level block(float x, float z, float width, float depth, float height) {
            blocks.add(new Block(x, z, width, depth, height));
            return this;
        }

        Level moving(float range, float speed) {
            this.moveRange = range;
            this.moveSpeed = speed;
            return this;
        }
    }

    private static class Block {
        final float x;
        final float z;
        final float width;
        final float depth;
        final float height;

        Block(float x, float z, float width, float depth, float height) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.depth = depth;
            this.height = height;
        }
    }
}
