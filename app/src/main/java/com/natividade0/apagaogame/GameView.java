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
    private static final float POWER_SCALE = 5.9f;
    private static final float BASE_GRAVITY = 650f;
    private static final float MIN_SHOT_POWER = 22f;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Level> levels = new ArrayList<>();

    private Thread thread;
    private volatile boolean running;

    private int screenW;
    private int screenH;
    private float groundY;
    private float gameTime;

    private int levelIndex;
    private int attempts;
    private int stars;
    private GameState state = GameState.AIMING;

    private float launchX;
    private float launchY;
    private float dragX;
    private float dragY;
    private boolean dragging;

    private float projectileX;
    private float projectileY;
    private float projectileVx;
    private float projectileVy;
    private float projectileRadius;
    private float projectileRotation;
    private float flightTime;
    private float stopTimer;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setupText();
        setupLevels();
    }

    private void setupText() {
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42f);
        textPaint.setFakeBoldText(true);
        smallTextPaint.setColor(Color.WHITE);
        smallTextPaint.setTextSize(28f);
    }

    private void setupLevels() {
        levels.add(new Level("Flecha no alvo", Mode.BULLSEYE, Projectile.ARROW,
                0.13f, 0.72f, 0.68f, 0.45f, 0f, 0.92f, 0.12f));
        levels.add(new Level("Bolinha no cesto", Mode.BIN, Projectile.PAPER_BALL,
                0.15f, 0.68f, 0.67f, 0.62f, 0f, 0.88f, 0.32f));
        levels.add(new Level("Pedra na lampada", Mode.LAMP, Projectile.STONE,
                0.13f, 0.72f, 0.70f, 0.32f, -10f, 0.96f, 0.25f));
        levels.add(new Level("Flecha com vento", Mode.BULLSEYE, Projectile.ARROW,
                0.13f, 0.72f, 0.72f, 0.42f, 25f, 0.92f, 0.12f));
        levels.add(new Level("Cesto com quique", Mode.BIN, Projectile.BALL,
                0.14f, 0.68f, 0.70f, 0.63f, 0f, 0.92f, 0.62f)
                .addObstacle(0.46f, 0.71f, 0.57f, 0.77f));
        levels.add(new Level("Pedra com curva", Mode.LAMP, Projectile.STONE,
                0.13f, 0.74f, 0.74f, 0.30f, -35f, 0.98f, 0.28f)
                .addObstacle(0.50f, 0.39f, 0.56f, 0.82f));
        levels.add(new Level("Copo em movimento", Mode.CUP, Projectile.BALL,
                0.13f, 0.68f, 0.68f, 0.63f, 0f, 0.92f, 0.56f)
                .moving(0.055f, 1.05f));
        levels.add(new Level("Trickshot final", Mode.BULLSEYE, Projectile.STONE,
                0.12f, 0.70f, 0.72f, 0.40f, 0f, 0.96f, 0.45f)
                .addObstacle(0.38f, 0.57f, 0.46f, 0.84f)
                .addObstacle(0.62f, 0.26f, 0.68f, 0.60f));
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        calculateWorld();
        resetLevel(false);
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        calculateWorld();
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
        thread = new Thread(this, "MiraRealLoop");
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
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.033f, (now - lastTime) / 1_000_000_000f);
            lastTime = now;
            update(dt);
            drawFrame();
            try {
                Thread.sleep(16L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void calculateWorld() {
        screenW = Math.max(1, getWidth());
        screenH = Math.max(1, getHeight());
        groundY = screenH * 0.86f;
        textPaint.setTextSize(Math.max(34f, screenH * 0.055f));
        smallTextPaint.setTextSize(Math.max(22f, screenH * 0.035f));
    }

    private Level level() {
        return levels.get(levelIndex);
    }

    private void resetLevel(boolean countAttemptReset) {
        Level level = level();
        launchX = level.launchNX * screenW;
        launchY = level.launchNY * screenH;
        projectileX = launchX;
        projectileY = launchY;
        projectileVx = 0f;
        projectileVy = 0f;
        projectileRotation = 0f;
        flightTime = 0f;
        stopTimer = 0f;
        projectileRadius = radiusFor(level.projectile);
        dragX = launchX;
        dragY = launchY;
        dragging = false;
        state = GameState.AIMING;
        if (countAttemptReset) {
            attempts = 0;
            stars = 0;
        }
    }

    private void update(float dt) {
        gameTime += dt;
        if (state != GameState.FLYING) {
            return;
        }

        Level level = level();
        flightTime += dt;

        projectileVx += level.wind * dt;
        projectileVy += BASE_GRAVITY * level.gravityScale * dt;
        projectileVx *= Math.max(0.86f, 1f - level.airDrag * dt);
        projectileVy *= Math.max(0.92f, 1f - level.airDrag * 0.22f * dt);

        projectileX += projectileVx * dt;
        projectileY += projectileVy * dt;
        projectileRotation = (float) Math.toDegrees(Math.atan2(projectileVy, projectileVx));

        collideWorld(level);
        if (hitsTarget(level)) {
            winShot();
            return;
        }

        float speed = (float) Math.sqrt(projectileVx * projectileVx + projectileVy * projectileVy);
        if (projectileY + projectileRadius >= groundY - 1f && speed < 90f) {
            stopTimer += dt;
        } else {
            stopTimer = 0f;
        }

        if (stopTimer > 0.8f || projectileX < -220f || projectileX > screenW + 220f || projectileY > screenH + 220f) {
            state = GameState.FAILED;
        }
    }

    private void collideWorld(Level level) {
        if (projectileY + projectileRadius > groundY) {
            projectileY = groundY - projectileRadius;
            projectileVy = -Math.abs(projectileVy) * level.bounce;
            projectileVx *= 0.88f;
            if (Math.abs(projectileVy) < 38f) {
                projectileVy = 0f;
            }
        }

        if (projectileX - projectileRadius < 0f) {
            projectileX = projectileRadius;
            projectileVx = Math.abs(projectileVx) * level.bounce;
        } else if (projectileX + projectileRadius > screenW) {
            projectileX = screenW - projectileRadius;
            projectileVx = -Math.abs(projectileVx) * level.bounce;
        }

        for (Obstacle obstacle : level.obstacles) {
            RectF r = obstacle.toRect(screenW, screenH);
            if (circleIntersectsRect(projectileX, projectileY, projectileRadius, r)) {
                resolveObstacleCollision(r, level.bounce);
            }
        }
    }

    private void resolveObstacleCollision(RectF r, float bounce) {
        float leftPen = Math.abs(projectileX - r.left);
        float rightPen = Math.abs(projectileX - r.right);
        float topPen = Math.abs(projectileY - r.top);
        float bottomPen = Math.abs(projectileY - r.bottom);
        float min = Math.min(Math.min(leftPen, rightPen), Math.min(topPen, bottomPen));

        if (min == leftPen) {
            projectileX = r.left - projectileRadius;
            projectileVx = -Math.abs(projectileVx) * bounce;
        } else if (min == rightPen) {
            projectileX = r.right + projectileRadius;
            projectileVx = Math.abs(projectileVx) * bounce;
        } else if (min == topPen) {
            projectileY = r.top - projectileRadius;
            projectileVy = -Math.abs(projectileVy) * bounce;
            projectileVx *= 0.90f;
        } else {
            projectileY = r.bottom + projectileRadius;
            projectileVy = Math.abs(projectileVy) * bounce;
            projectileVx *= 0.90f;
        }
    }

    private boolean hitsTarget(Level level) {
        float tx = currentTargetX(level);
        float ty = level.targetNY * screenH;

        if (level.mode == Mode.BIN || level.mode == Mode.CUP) {
            RectF cup = targetRect(level, tx, ty);
            boolean inside = projectileX > cup.left && projectileX < cup.right && projectileY > cup.top && projectileY < cup.bottom;
            return inside && projectileVy > -180f;
        }

        float targetRadius = targetRadius(level);
        return distance(projectileX, projectileY, tx, ty) < projectileRadius + targetRadius;
    }

    private void winShot() {
        stars = attempts <= 1 ? 3 : attempts <= 3 ? 2 : 1;
        state = GameState.SUCCESS;
    }

    private void nextLevel() {
        if (levelIndex < levels.size() - 1) {
            levelIndex++;
            attempts = 0;
            resetLevel(false);
        } else {
            state = GameState.FINISHED;
        }
    }

    private void shoot() {
        float dx = launchX - dragX;
        float dy = launchY - dragY;
        float pull = (float) Math.sqrt(dx * dx + dy * dy);
        if (pull < MIN_SHOT_POWER) {
            dragging = false;
            dragX = launchX;
            dragY = launchY;
            return;
        }

        float maxPull = maxPull();
        if (pull > maxPull) {
            dx = dx / pull * maxPull;
            dy = dy / pull * maxPull;
        }

        float multiplier = speedMultiplier(level().projectile);
        attempts++;
        projectileX = launchX;
        projectileY = launchY;
        projectileVx = dx * POWER_SCALE * multiplier;
        projectileVy = dy * POWER_SCALE * multiplier;
        state = GameState.FLYING;
        dragging = false;
        flightTime = 0f;
        stopTimer = 0f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (screenW <= 0 || screenH <= 0) {
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (state == GameState.SUCCESS) {
                nextLevel();
                return true;
            }
            if (state == GameState.FAILED) {
                resetLevel(false);
                return true;
            }
            if (state == GameState.FINISHED) {
                levelIndex = 0;
                attempts = 0;
                resetLevel(false);
                return true;
            }
            if (state == GameState.FLYING) {
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
        float dx = dragX - launchX;
        float dy = dragY - launchY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float maxPull = maxPull();
        if (dist > maxPull) {
            dragX = launchX + dx / dist * maxPull;
            dragY = launchY + dy / dist * maxPull;
        }
    }

    private float maxPull() {
        return Math.max(330f, Math.min(screenW * 0.36f, screenH * 0.58f));
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
        canvas.drawColor(Color.rgb(22, 24, 30));
        drawBackground(canvas);
        drawTarget(canvas, level());
        drawObstacles(canvas, level());
        drawLauncher(canvas);
        if (state == GameState.AIMING && dragging) {
            drawAimPreview(canvas);
        }
        drawProjectile(canvas, level().projectile);
        drawHud(canvas);
        drawStateMessage(canvas);
    }

    private void drawBackground(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(32, 37, 46));
        canvas.drawRect(0, groundY, screenW, screenH, paint);
        paint.setColor(Color.rgb(55, 59, 70));
        paint.setStrokeWidth(4f);
        canvas.drawLine(0, groundY, screenW, groundY, paint);

        paint.setStrokeWidth(2f);
        paint.setColor(Color.argb(60, 255, 255, 255));
        for (int i = 0; i < 6; i++) {
            float y = groundY + 20f + i * 34f;
            canvas.drawLine(0, y, screenW, y, paint);
        }
    }

    private void drawTarget(Canvas canvas, Level level) {
        float tx = currentTargetX(level);
        float ty = level.targetNY * screenH;
        if (level.mode == Mode.BULLSEYE) {
            drawBullseye(canvas, tx, ty, targetRadius(level));
        } else if (level.mode == Mode.BIN) {
            drawBin(canvas, targetRect(level, tx, ty));
        } else if (level.mode == Mode.LAMP) {
            drawLamp(canvas, tx, ty);
        } else if (level.mode == Mode.CUP) {
            drawCup(canvas, targetRect(level, tx, ty));
        }
    }

    private void drawBullseye(Canvas canvas, float tx, float ty, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(240, 240, 240));
        canvas.drawCircle(tx, ty, radius, paint);
        paint.setColor(Color.rgb(221, 54, 54));
        canvas.drawCircle(tx, ty, radius * 0.72f, paint);
        paint.setColor(Color.rgb(245, 245, 245));
        canvas.drawCircle(tx, ty, radius * 0.45f, paint);
        paint.setColor(Color.rgb(36, 125, 224));
        canvas.drawCircle(tx, ty, radius * 0.20f, paint);
    }

    private void drawBin(Canvas canvas, RectF r) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.rgb(100, 210, 160));
        canvas.drawLine(r.left, r.top, r.right, r.top, paint);
        paint.setColor(Color.rgb(75, 105, 95));
        canvas.drawLine(r.left, r.top, r.left + r.width() * 0.18f, r.bottom, paint);
        canvas.drawLine(r.right, r.top, r.right - r.width() * 0.18f, r.bottom, paint);
        canvas.drawLine(r.left + r.width() * 0.18f, r.bottom, r.right - r.width() * 0.18f, r.bottom, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(70, 100, 210, 160));
        canvas.drawRect(r, paint);
    }

    private void drawCup(Canvas canvas, RectF r) {
        Path path = new Path();
        path.moveTo(r.left, r.top);
        path.lineTo(r.right, r.top);
        path.lineTo(r.right - r.width() * 0.22f, r.bottom);
        path.lineTo(r.left + r.width() * 0.22f, r.bottom);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(80, 80, 170, 240));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7f);
        paint.setColor(Color.rgb(120, 210, 255));
        canvas.drawPath(path, paint);
        paint.setStrokeWidth(5f);
        canvas.drawLine(r.left, r.top, r.right, r.top, paint);
    }

    private void drawLamp(Canvas canvas, float tx, float ty) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.rgb(120, 120, 130));
        canvas.drawLine(tx, 0, tx, ty - 34f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 224, 95));
        canvas.drawCircle(tx, ty, targetRadius(level()), paint);
        paint.setColor(Color.argb(55, 255, 224, 95));
        canvas.drawCircle(tx, ty, targetRadius(level()) * 2.2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(Color.rgb(80, 80, 80));
        canvas.drawCircle(tx, ty, targetRadius(level()), paint);
    }

    private void drawObstacles(Canvas canvas, Level level) {
        paint.setStyle(Paint.Style.FILL);
        for (Obstacle obstacle : level.obstacles) {
            RectF r = obstacle.toRect(screenW, screenH);
            paint.setColor(Color.rgb(88, 92, 105));
            canvas.drawRoundRect(r, 10f, 10f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.rgb(135, 140, 155));
            canvas.drawRoundRect(r, 10f, 10f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawLauncher(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        paint.setColor(Color.rgb(160, 110, 70));
        canvas.drawCircle(launchX, launchY, 38f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(215, 165, 95));
        canvas.drawCircle(launchX, launchY, 13f, paint);
    }

    private void drawAimPreview(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6f);
        paint.setColor(Color.rgb(255, 205, 80));
        canvas.drawLine(launchX, launchY, dragX, dragY, paint);

        float pull = distance(dragX, dragY, launchX, launchY);
        float pct = Math.min(1f, pull / maxPull());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(255, 205, 80));
        canvas.drawRoundRect(new RectF(36, 98, 36 + 260 * pct, 122), 10f, 10f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(new RectF(36, 98, 296, 122), 10f, 10f, paint);

        float dx = launchX - dragX;
        float dy = launchY - dragY;
        float maxPull = maxPull();
        if (pull > maxPull) {
            dx = dx / pull * maxPull;
            dy = dy / pull * maxPull;
        }

        paint.setStrokeWidth(4f);
        paint.setColor(Color.argb(190, 255, 255, 255));
        float px = launchX;
        float py = launchY;
        Level level = level();
        float multiplier = speedMultiplier(level.projectile);
        float vx = dx * POWER_SCALE * multiplier;
        float vy = dy * POWER_SCALE * multiplier;
        for (int i = 0; i < 42; i++) {
            float t = i * 0.065f;
            float x = px + vx * t + 0.5f * level.wind * t * t;
            float y = py + vy * t + 0.5f * BASE_GRAVITY * level.gravityScale * t * t;
            if (y > groundY || x < 0 || x > screenW) {
                break;
            }
            canvas.drawCircle(x, y, 4.5f, paint);
        }
    }

    private void drawProjectile(Canvas canvas, Projectile type) {
        paint.setStyle(Paint.Style.FILL);
        if (type == Projectile.ARROW) {
            canvas.save();
            canvas.rotate(projectileRotation, projectileX, projectileY);
            paint.setStrokeWidth(8f);
            paint.setColor(Color.rgb(220, 180, 85));
            canvas.drawLine(projectileX - 34f, projectileY, projectileX + 36f, projectileY, paint);
            paint.setStyle(Paint.Style.FILL);
            Path head = new Path();
            head.moveTo(projectileX + 48f, projectileY);
            head.lineTo(projectileX + 24f, projectileY - 14f);
            head.lineTo(projectileX + 24f, projectileY + 14f);
            head.close();
            paint.setColor(Color.rgb(235, 235, 235));
            canvas.drawPath(head, paint);
            paint.setColor(Color.rgb(120, 170, 230));
            canvas.drawCircle(projectileX - 38f, projectileY, 8f, paint);
            canvas.restore();
        } else if (type == Projectile.PAPER_BALL) {
            paint.setColor(Color.rgb(235, 235, 225));
            canvas.drawCircle(projectileX, projectileY, projectileRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(Color.rgb(170, 170, 165));
            canvas.drawLine(projectileX - 8f, projectileY - 5f, projectileX + 9f, projectileY + 7f, paint);
            canvas.drawLine(projectileX - 6f, projectileY + 8f, projectileX + 7f, projectileY - 8f, paint);
        } else if (type == Projectile.STONE) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(115, 118, 124));
            canvas.drawCircle(projectileX, projectileY, projectileRadius, paint);
            paint.setColor(Color.rgb(80, 82, 88));
            canvas.drawCircle(projectileX + 5f, projectileY - 4f, projectileRadius * 0.35f, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 145, 70));
            canvas.drawCircle(projectileX, projectileY, projectileRadius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(Color.rgb(120, 75, 45));
            canvas.drawCircle(projectileX, projectileY, projectileRadius * 0.72f, paint);
        }
    }

    private void drawHud(Canvas canvas) {
        Level level = level();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(140, 0, 0, 0));
        canvas.drawRoundRect(new RectF(18, 16, screenW - 18, 88), 18f, 18f, paint);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Mira Real", 36f, 63f, textPaint);
        smallTextPaint.setTextAlign(Paint.Align.RIGHT);
        smallTextPaint.setColor(Color.WHITE);
        String info = String.format(Locale.getDefault(), "Fase %d/%d • %s • Tentativas: %d", levelIndex + 1, levels.size(), level.name, attempts);
        canvas.drawText(info, screenW - 36f, 61f, smallTextPaint);

        smallTextPaint.setTextAlign(Paint.Align.LEFT);
        smallTextPaint.setColor(Color.rgb(210, 220, 230));
        String wind = Math.abs(level.wind) < 1f ? "sem vento" : (level.wind > 0 ? "vento ->" : "vento <-");
        canvas.drawText("Puxe mais para tras = mais forca. " + wind, 36f, screenH - 28f, smallTextPaint);
    }

    private void drawStateMessage(Canvas canvas) {
        if (state == GameState.AIMING || state == GameState.FLYING) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(190, 0, 0, 0));
        canvas.drawRect(0, 0, screenW, screenH, paint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        String title;
        String subtitle;
        if (state == GameState.SUCCESS) {
            title = "Acertou!";
            subtitle = "Estrelas: " + stars + "  • toque para continuar";
        } else if (state == GameState.FINISHED) {
            title = "Voce completou o MVP!";
            subtitle = "Toque para jogar de novo";
        } else {
            title = "Errou!";
            subtitle = "Toque para tentar novamente";
        }
        canvas.drawText(title, screenW / 2f, screenH / 2f - 24f, textPaint);
        smallTextPaint.setTextAlign(Paint.Align.CENTER);
        smallTextPaint.setColor(Color.rgb(220, 220, 220));
        canvas.drawText(subtitle, screenW / 2f, screenH / 2f + 28f, smallTextPaint);
    }

    private float currentTargetX(Level level) {
        float base = level.targetNX * screenW;
        if (level.moveRangeNX == 0f) {
            return base;
        }
        return base + (float) Math.sin(gameTime * level.moveSpeed) * level.moveRangeNX * screenW;
    }

    private RectF targetRect(Level level, float tx, float ty) {
        float w = screenW * (level.mode == Mode.CUP ? 0.080f : 0.105f);
        float h = screenH * (level.mode == Mode.CUP ? 0.15f : 0.18f);
        return new RectF(tx - w / 2f, ty - h / 2f, tx + w / 2f, ty + h / 2f);
    }

    private float targetRadius(Level level) {
        if (level.mode == Mode.LAMP) {
            return Math.max(24f, screenH * 0.047f);
        }
        return Math.max(38f, screenH * 0.078f);
    }

    private float radiusFor(Projectile projectile) {
        if (projectile == Projectile.ARROW) {
            return 14f;
        }
        if (projectile == Projectile.PAPER_BALL) {
            return 20f;
        }
        if (projectile == Projectile.STONE) {
            return 18f;
        }
        return 19f;
    }

    private float speedMultiplier(Projectile projectile) {
        if (projectile == Projectile.ARROW) {
            return 1.08f;
        }
        if (projectile == Projectile.PAPER_BALL) {
            return 0.92f;
        }
        if (projectile == Projectile.STONE) {
            return 1.00f;
        }
        return 0.98f;
    }

    private boolean circleIntersectsRect(float cx, float cy, float radius, RectF rect) {
        float nearestX = clamp(cx, rect.left, rect.right);
        float nearestY = clamp(cy, rect.top, rect.bottom);
        float dx = cx - nearestX;
        float dy = cy - nearestY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum GameState {
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
        PAPER_BALL,
        STONE,
        BALL
    }

    private static class Level {
        final String name;
        final Mode mode;
        final Projectile projectile;
        final float launchNX;
        final float launchNY;
        final float targetNX;
        final float targetNY;
        final float wind;
        final float gravityScale;
        final float bounce;
        final float airDrag;
        float moveRangeNX;
        float moveSpeed;
        final List<Obstacle> obstacles = new ArrayList<>();

        Level(String name, Mode mode, Projectile projectile, float launchNX, float launchNY,
              float targetNX, float targetNY, float wind, float gravityScale, float bounce) {
            this.name = name;
            this.mode = mode;
            this.projectile = projectile;
            this.launchNX = launchNX;
            this.launchNY = launchNY;
            this.targetNX = targetNX;
            this.targetNY = targetNY;
            this.wind = wind;
            this.gravityScale = gravityScale;
            this.bounce = bounce;
            this.airDrag = projectile == Projectile.PAPER_BALL ? 0.10f : 0.025f;
        }

        Level addObstacle(float left, float top, float right, float bottom) {
            obstacles.add(new Obstacle(left, top, right, bottom));
            return this;
        }

        Level moving(float rangeNX, float speed) {
            this.moveRangeNX = rangeNX;
            this.moveSpeed = speed;
            return this;
        }
    }

    private static class Obstacle {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Obstacle(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        RectF toRect(float w, float h) {
            return new RectF(left * w, top * h, right * w, bottom * h);
        }
    }
}
