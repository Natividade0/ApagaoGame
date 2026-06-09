package com.natividade0.apagaogame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    private static final float MAX_DRAG = 230f;
    private static final float LAUNCH_POWER = 5.25f;
    private static final float GROUND_FRICTION = 0.72f;
    private static final int MAX_LEVELS = 6;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(42);
    private final List<Particle> particles = new ArrayList<>();
    private final List<Obstacle> obstacles = new ArrayList<>();

    private Thread thread;
    private volatile boolean running;
    private long lastFrameNanos;

    private GameState state = GameState.MENU;
    private Level[] levels;
    private Level level;
    private Projectile projectile;
    private int levelIndex;
    private int attempts;
    private int stars;
    private int totalStars;
    private boolean dragging;
    private boolean targetDone;
    private boolean levelCleared;
    private boolean missShown;
    private float dragX;
    private float dragY;
    private float startX;
    private float startY;
    private float groundY;
    private float screenShake;
    private float targetAnim;
    private float messageTimer;
    private String message = "";

    private RectF playButton = new RectF();
    private RectF howToButton = new RectF();
    private RectF targetRect = new RectF();
    private RectF helperRect = new RectF();

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        levels = createLevels();
        level = levels[0];
        projectile = new Projectile(level.projectileType);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        layoutLevel(width, height);
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
        lastFrameNanos = System.nanoTime();
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
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.033f, (now - lastFrameNanos) / 1_000_000_000f);
            lastFrameNanos = now;
            updateGame(dt);
            renderFrame();
            try {
                Thread.sleep(16L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void updateGame(float dt) {
        if (screenShake > 0f) {
            screenShake = Math.max(0f, screenShake - dt * 4.8f);
        }
        if (targetAnim > 0f) {
            targetAnim = Math.max(0f, targetAnim - dt * 2.2f);
        }
        if (messageTimer > 0f) {
            messageTimer = Math.max(0f, messageTimer - dt);
        }
        updateParticles(dt);

        if (state == GameState.FLYING) {
            updateProjectile(dt);
            if (checkTargetHit()) {
                onHit();
            } else if (projectile.y > getHeight() + 120f || projectile.x > getWidth() + 180f || projectile.x < -180f) {
                onMiss();
            } else if (projectile.sleepTimer > 0.55f) {
                onMiss();
            }
        }
    }

    private void updateProjectile(float dt) {
        ProjectileType type = projectile.type;
        projectile.vx += level.wind * type.windInfluence * dt;
        projectile.vy += type.gravity * dt;
        projectile.x += projectile.vx * dt;
        projectile.y += projectile.vy * dt;
        projectile.rotation += projectile.vx * dt * type.spin;

        if (projectile.y + projectile.radius > groundY) {
            projectile.y = groundY - projectile.radius;
            if (Math.abs(projectile.vy) > 160f && type.bounce > 0.05f) {
                projectile.vy = -projectile.vy * type.bounce;
                projectile.vx *= GROUND_FRICTION;
                addDust(projectile.x, projectile.y + projectile.radius, 8, Color.argb(210, 190, 160, 115));
                screenShake = Math.max(screenShake, 0.08f);
            } else {
                projectile.vy = 0f;
                projectile.vx *= 0.88f;
            }
        }

        for (Obstacle obstacle : obstacles) {
            if (circleIntersectsRect(projectile.x, projectile.y, projectile.radius, obstacle.rect)) {
                resolveObstacleHit(obstacle);
            }
        }

        if (Math.abs(projectile.vx) < 18f && Math.abs(projectile.vy) < 22f && projectile.y + projectile.radius >= groundY - 1f) {
            projectile.sleepTimer += dt;
        } else {
            projectile.sleepTimer = 0f;
        }
    }

    private void resolveObstacleHit(Obstacle obstacle) {
        obstacle.hitAnim = 1f;
        projectile.bounces++;
        screenShake = Math.max(screenShake, 0.10f);
        addDust(projectile.x, projectile.y, 10, obstacle.particleColor);

        float leftOverlap = Math.abs(projectile.x + projectile.radius - obstacle.rect.left);
        float rightOverlap = Math.abs(obstacle.rect.right - (projectile.x - projectile.radius));
        float topOverlap = Math.abs(projectile.y + projectile.radius - obstacle.rect.top);
        float minOverlap = Math.min(Math.min(leftOverlap, rightOverlap), topOverlap);

        if (minOverlap == topOverlap && projectile.vy > 0f) {
            projectile.y = obstacle.rect.top - projectile.radius;
            projectile.vy = -Math.abs(projectile.vy) * projectile.type.bounce;
            projectile.vx *= 0.86f;
        } else if (leftOverlap < rightOverlap) {
            projectile.x = obstacle.rect.left - projectile.radius;
            projectile.vx = -Math.abs(projectile.vx) * projectile.type.bounce;
        } else {
            projectile.x = obstacle.rect.right + projectile.radius;
            projectile.vx = Math.abs(projectile.vx) * projectile.type.bounce;
        }
    }

    private boolean checkTargetHit() {
        if (targetDone) {
            return false;
        }
        if (level.targetType == TargetType.TRASH) {
            RectF opening = new RectF(targetRect.left + 12f, targetRect.top - 10f, targetRect.right - 12f, targetRect.top + 24f);
            return projectile.vy > -80f && opening.contains(projectile.x, projectile.y + projectile.radius * 0.25f);
        }
        if (level.targetType == TargetType.LAMP) {
            return distance(projectile.x, projectile.y, targetRect.centerX(), targetRect.centerY()) < projectile.radius + targetRect.width() * 0.38f;
        }
        if (level.targetType == TargetType.CAN) {
            return circleIntersectsRect(projectile.x, projectile.y, projectile.radius, targetRect);
        }
        if (level.targetType == TargetType.CUP) {
            RectF cupMouth = new RectF(targetRect.left - 8f, targetRect.top - 8f, targetRect.right + 8f, targetRect.top + 28f);
            return projectile.vy > 60f && cupMouth.contains(projectile.x, projectile.y + projectile.radius * 0.6f);
        }
        if (level.targetType == TargetType.DOORBELL) {
            return circleIntersectsRect(projectile.x, projectile.y, projectile.radius, targetRect);
        }
        if (level.targetType == TargetType.TRICK_TARGET) {
            return projectile.bounces > 0 && circleIntersectsRect(projectile.x, projectile.y, projectile.radius, targetRect);
        }
        return false;
    }

    private void onHit() {
        targetDone = true;
        levelCleared = true;
        state = GameState.HIT;
        stars = attempts <= 1 ? 3 : (attempts <= 3 ? 2 : 1);
        totalStars += stars;
        message = "Acertou!";
        messageTimer = 2.5f;
        targetAnim = 1f;
        screenShake = 0.35f;
        createHitParticles();
    }

    private void onMiss() {
        state = GameState.MISS;
        missShown = true;
        message = "Quase! Ajuste a mira e tente de novo.";
        messageTimer = 1.7f;
        resetProjectile(false);
    }

    private void createHitParticles() {
        if (level.targetType == TargetType.TRASH) {
            for (int i = 0; i < 24; i++) {
                addParticle(targetRect.centerX(), targetRect.top + 10f, randomRange(-180f, 180f), randomRange(-360f, -90f), randomRange(3f, 7f), Color.rgb(245, 245, 235), 0.9f, ParticleShape.PAPER);
            }
        } else if (level.targetType == TargetType.LAMP) {
            for (int i = 0; i < 30; i++) {
                addParticle(targetRect.centerX(), targetRect.centerY(), randomRange(-260f, 260f), randomRange(-230f, 260f), randomRange(2f, 6f), Color.rgb(185, 230, 255), 0.75f, ParticleShape.GLASS);
            }
        } else if (level.targetType == TargetType.DOORBELL) {
            for (int i = 0; i < 22; i++) {
                addParticle(targetRect.centerX(), targetRect.centerY(), randomRange(-210f, 210f), randomRange(-220f, 180f), randomRange(2f, 5f), Color.rgb(255, 222, 84), 0.65f, ParticleShape.SPARK);
            }
        } else {
            addDust(targetRect.centerX(), targetRect.centerY(), 24, Color.argb(230, 230, 190, 115));
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle particle = particles.get(i);
            particle.life -= dt;
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vy += 620f * dt;
            particle.rotation += particle.spin * dt;
            if (particle.life <= 0f) {
                particles.remove(i);
            }
        }
    }

    private void renderFrame() {
        if (!holder.getSurface().isValid()) {
            return;
        }
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            drawGame(canvas);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawGame(Canvas canvas) {
        int save = canvas.save();
        if (state != GameState.MENU && state != GameState.HOW_TO) {
            applyShake(canvas);
        }

        if (state == GameState.MENU) {
            drawMenu(canvas);
        } else if (state == GameState.HOW_TO) {
            drawHowTo(canvas);
        } else {
            layoutLevel(canvas.getWidth(), canvas.getHeight());
            drawScene(canvas);
            drawObstacles(canvas);
            drawTarget(canvas);
            drawProjectileShadow(canvas);
            drawProjectile(canvas);
            drawParticles(canvas);
            if (state == GameState.AIMING && dragging) {
                drawTrajectory(canvas);
                drawAimLine(canvas);
            }
            drawHud(canvas);
            if (state == GameState.INTRO) {
                drawIntroCard(canvas);
            } else if (state == GameState.HIT) {
                drawHitCard(canvas);
            } else if (state == GameState.MISS && missShown && messageTimer > 0f) {
                drawToast(canvas, message);
            } else if (state == GameState.COMPLETE) {
                drawGameComplete(canvas);
            }
        }
        canvas.restoreToCount(save);
    }

    private void applyShake(Canvas canvas) {
        if (screenShake <= 0f) {
            return;
        }
        float amount = 18f * screenShake;
        canvas.translate(randomRange(-amount, amount), randomRange(-amount, amount));
    }

    private void drawMenu(Canvas canvas) {
        drawVerticalBackground(canvas, Color.rgb(41, 62, 92), Color.rgb(244, 172, 95));
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        drawSoftCircle(canvas, w * 0.78f, h * 0.22f, 110f, Color.argb(55, 255, 255, 255));
        drawSoftCircle(canvas, w * 0.20f, h * 0.78f, 150f, Color.argb(40, 0, 0, 0));

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(58f);
        canvas.drawText("MIRA REAL", w / 2f, h * 0.28f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(22f);
        textPaint.setColor(Color.rgb(244, 247, 250));
        canvas.drawText("Arremesse, erre por pouco, quebre coisas e comemore.", w / 2f, h * 0.34f, textPaint);

        playButton.set(w / 2f - 145f, h * 0.47f, w / 2f + 145f, h * 0.47f + 66f);
        howToButton.set(w / 2f - 145f, h * 0.60f, w / 2f + 145f, h * 0.60f + 58f);
        drawButton(canvas, playButton, "Jogar", Color.rgb(255, 211, 91));
        drawButton(canvas, howToButton, "Como jogar", Color.rgb(236, 243, 252));

        drawPaperBallIcon(canvas, w * 0.26f, h * 0.44f, 30f, 0.3f);
        drawStoneIcon(canvas, w * 0.75f, h * 0.53f, 24f, -0.2f);
        drawBallIcon(canvas, w * 0.33f, h * 0.68f, 26f, 0.1f);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawHowTo(Canvas canvas) {
        drawVerticalBackground(canvas, Color.rgb(28, 35, 48), Color.rgb(61, 79, 106));
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        drawPanel(canvas, w * 0.13f, h * 0.16f, w * 0.87f, h * 0.80f, Color.argb(230, 255, 255, 255));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.rgb(35, 40, 50));
        textPaint.setTextSize(38f);
        canvas.drawText("Como jogar", w / 2f, h * 0.27f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(24f);
        canvas.drawText("1. Arraste o objeto para tras para mirar.", w / 2f, h * 0.39f, textPaint);
        canvas.drawText("2. Use a linha pontilhada e a barra de força.", w / 2f, h * 0.48f, textPaint);
        canvas.drawText("3. Solte para arremessar e causar uma pequena bagunca.", w / 2f, h * 0.57f, textPaint);
        canvas.drawText("Toque para voltar ao menu", w / 2f, h * 0.70f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawScene(Canvas canvas) {
        if (level.theme == Theme.OFFICE) {
            drawVerticalBackground(canvas, Color.rgb(187, 219, 232), Color.rgb(238, 219, 179));
            drawWallAndFloor(canvas, Color.rgb(212, 231, 238), Color.rgb(196, 153, 104));
            drawDesk(canvas, canvas.getWidth() * 0.10f, groundY - 150f, canvas.getWidth() * 0.46f, groundY - 60f);
            drawPaperSheet(canvas, 95f, groundY - 120f, -0.15f);
            drawPaperSheet(canvas, 155f, groundY - 95f, 0.25f);
        } else if (level.theme == Theme.NIGHT_YARD) {
            drawVerticalBackground(canvas, Color.rgb(16, 22, 42), Color.rgb(41, 48, 61));
            drawWallAndFloor(canvas, Color.rgb(42, 50, 69), Color.rgb(48, 58, 48));
            paint.setColor(Color.rgb(27, 30, 38));
            canvas.drawRect(0f, groundY - 145f, canvas.getWidth(), groundY - 132f, paint);
            paint.setColor(targetDone ? Color.argb(35, 255, 230, 130) : Color.argb(115, 255, 232, 120));
            canvas.drawCircle(targetRect.centerX(), targetRect.centerY(), 115f, paint);
        } else if (level.theme == Theme.WALL) {
            drawVerticalBackground(canvas, Color.rgb(138, 203, 224), Color.rgb(235, 207, 152));
            drawWallAndFloor(canvas, Color.rgb(188, 218, 225), Color.rgb(168, 120, 76));
            drawBrickWall(canvas, targetRect.left - 160f, targetRect.bottom - 10f, 340f, 85f);
            drawCrate(canvas, canvas.getWidth() * 0.55f, groundY - 62f, 62f, 62f, 0f);
        } else if (level.theme == Theme.TABLE) {
            drawVerticalBackground(canvas, Color.rgb(238, 227, 205), Color.rgb(198, 161, 124));
            drawWallAndFloor(canvas, Color.rgb(234, 222, 204), Color.rgb(188, 140, 91));
            drawDesk(canvas, canvas.getWidth() * 0.43f, targetRect.bottom + 12f, canvas.getWidth() * 0.90f, targetRect.bottom + 92f);
        } else if (level.theme == Theme.HOUSE) {
            drawVerticalBackground(canvas, Color.rgb(139, 197, 226), Color.rgb(229, 198, 147));
            drawWallAndFloor(canvas, Color.rgb(225, 190, 146), Color.rgb(152, 129, 101));
            drawHouseFront(canvas);
        } else {
            drawVerticalBackground(canvas, Color.rgb(92, 104, 118), Color.rgb(164, 143, 111));
            drawWallAndFloor(canvas, Color.rgb(121, 131, 139), Color.rgb(111, 95, 76));
            drawGarageShelves(canvas);
        }
    }

    private void drawTarget(Canvas canvas) {
        if (level.targetType == TargetType.TRASH) {
            drawTrashCan(canvas, targetRect, targetAnim);
        } else if (level.targetType == TargetType.LAMP) {
            drawLamp(canvas, targetRect, targetDone, targetAnim);
        } else if (level.targetType == TargetType.CAN) {
            drawCan(canvas, targetRect, targetDone, targetAnim);
        } else if (level.targetType == TargetType.CUP) {
            drawCup(canvas, targetRect, targetAnim);
        } else if (level.targetType == TargetType.DOORBELL) {
            drawDoorbell(canvas, targetRect, targetAnim);
        } else if (level.targetType == TargetType.TRICK_TARGET) {
            drawFinalTarget(canvas, targetRect, targetAnim);
        }
    }

    private void drawObstacles(Canvas canvas) {
        for (Obstacle obstacle : obstacles) {
            if (obstacle.hitAnim > 0f) {
                obstacle.hitAnim = Math.max(0f, obstacle.hitAnim - 0.05f);
            }
            float wobble = (float) Math.sin(obstacle.hitAnim * Math.PI * 5f) * 8f;
            canvas.save();
            canvas.translate(wobble, 0f);
            if (obstacle.kind == ObstacleKind.CRATE) {
                drawCrate(canvas, obstacle.rect.left, obstacle.rect.top, obstacle.rect.width(), obstacle.rect.height(), obstacle.hitAnim);
            } else {
                drawBox(canvas, obstacle.rect, obstacle.hitAnim);
            }
            canvas.restore();
        }
    }

    private void drawProjectileShadow(Canvas canvas) {
        if (state == GameState.INTRO || state == GameState.COMPLETE) {
            return;
        }
        float distanceToGround = Math.max(0f, groundY - projectile.y);
        float scale = Math.max(0.28f, 1f - distanceToGround / 520f);
        paint.setColor(Color.argb(75, 0, 0, 0));
        canvas.drawOval(new RectF(projectile.x - projectile.radius * scale * 1.3f, groundY - 8f, projectile.x + projectile.radius * scale * 1.3f, groundY + 5f), paint);
    }

    private void drawProjectile(Canvas canvas) {
        if (state == GameState.INTRO || state == GameState.COMPLETE) {
            return;
        }
        if (projectile.type == ProjectileType.PAPER) {
            drawPaperBallIcon(canvas, projectile.x, projectile.y, projectile.radius, projectile.rotation);
        } else if (projectile.type == ProjectileType.STONE || projectile.type == ProjectileType.SMALL_STONE) {
            drawStoneIcon(canvas, projectile.x, projectile.y, projectile.radius, projectile.rotation);
        } else if (projectile.type == ProjectileType.BALL || projectile.type == ProjectileType.SMALL_BALL) {
            drawBallIcon(canvas, projectile.x, projectile.y, projectile.radius, projectile.rotation);
        } else {
            drawCoinIcon(canvas, projectile.x, projectile.y, projectile.radius, projectile.rotation);
        }
    }

    private void drawTrajectory(Canvas canvas) {
        float[] velocity = currentLaunchVelocity();
        float px = startX;
        float py = startY;
        float vx = velocity[0];
        float vy = velocity[1];
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 34; i++) {
            float t = i * 0.085f;
            float tx = px + vx * t + 0.5f * level.wind * projectile.type.windInfluence * t * t;
            float ty = py + vy * t + 0.5f * projectile.type.gravity * t * t;
            if (ty > groundY || tx < 0f || tx > canvas.getWidth()) {
                break;
            }
            int alpha = Math.max(35, 210 - i * 5);
            paint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(tx, ty, 4.2f, paint);
        }
    }

    private void drawAimLine(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(Color.argb(190, 255, 220, 90));
        canvas.drawLine(startX, startY, dragX, dragY, paint);
        paint.setStyle(Paint.Style.FILL);

        float power = currentPower01();
        RectF bar = new RectF(32f, canvas.getHeight() - 58f, 252f, canvas.getHeight() - 32f);
        paint.setColor(Color.argb(180, 20, 25, 32));
        canvas.drawRoundRect(bar, 12f, 12f, paint);
        paint.setColor(power > 0.82f ? Color.rgb(255, 106, 82) : Color.rgb(255, 211, 91));
        canvas.drawRoundRect(new RectF(bar.left + 4f, bar.top + 4f, bar.left + 4f + (bar.width() - 8f) * power, bar.bottom - 4f), 10f, 10f, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(18f);
        canvas.drawText("Forca", bar.left, bar.top - 8f, textPaint);
    }

    private void drawHud(Canvas canvas) {
        float w = canvas.getWidth();
        paint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRoundRect(new RectF(18f, 16f, w - 18f, 72f), 20f, 20f, paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(23f);
        textPaint.setFakeBoldText(true);
        canvas.drawText(String.format(Locale.US, "Fase %d/%d - %s", levelIndex + 1, MAX_LEVELS, level.title), 34f, 51f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(18f);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("Tentativas: " + attempts + "  Estrelas: " + totalStars, w - 34f, 51f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawIntroCard(Canvas canvas) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        paint.setColor(Color.argb(155, 0, 0, 0));
        canvas.drawRect(0f, 0f, w, h, paint);
        drawPanel(canvas, w * 0.15f, h * 0.18f, w * 0.85f, h * 0.78f, Color.rgb(250, 247, 239));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.rgb(36, 42, 52));
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(36f);
        canvas.drawText("Fase " + (levelIndex + 1) + " - " + level.title, w / 2f, h * 0.32f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(25f);
        canvas.drawText("Objeto: " + level.objectName, w / 2f, h * 0.44f, textPaint);
        canvas.drawText("Objetivo: " + level.objective, w / 2f, h * 0.53f, textPaint);
        textPaint.setTextSize(23f);
        textPaint.setColor(Color.rgb(92, 92, 92));
        canvas.drawText("Toque para comecar", w / 2f, h * 0.67f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawHitCard(Canvas canvas) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        paint.setColor(Color.argb(95, 0, 0, 0));
        canvas.drawRect(0f, 0f, w, h, paint);
        drawPanel(canvas, w * 0.26f, h * 0.20f, w * 0.74f, h * 0.63f, Color.argb(238, 255, 255, 255));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.rgb(38, 45, 55));
        textPaint.setTextSize(42f);
        canvas.drawText("Acertou!", w / 2f, h * 0.34f, textPaint);
        drawStars(canvas, w / 2f, h * 0.44f, stars);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(22f);
        textPaint.setColor(Color.rgb(82, 88, 96));
        canvas.drawText("Toque para continuar", w / 2f, h * 0.56f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGameComplete(Canvas canvas) {
        drawVerticalBackground(canvas, Color.rgb(48, 78, 109), Color.rgb(245, 195, 103));
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(46f);
        canvas.drawText("Bagunca concluida!", w / 2f, h * 0.34f, textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(26f);
        canvas.drawText("Total de estrelas: " + totalStars + " / 18", w / 2f, h * 0.45f, textPaint);
        canvas.drawText("Toque para voltar ao menu", w / 2f, h * 0.58f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawToast(Canvas canvas, String text) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        RectF toast = new RectF(w / 2f - 225f, h * 0.18f, w / 2f + 225f, h * 0.18f + 58f);
        paint.setColor(Color.argb(210, 30, 35, 44));
        canvas.drawRoundRect(toast, 20f, 20f, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(21f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText(text, w / 2f, toast.centerY() + 8f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float alpha = Math.max(0f, Math.min(1f, particle.life / particle.maxLife));
            paint.setColor(withAlpha(particle.color, (int) (255 * alpha)));
            canvas.save();
            canvas.translate(particle.x, particle.y);
            canvas.rotate(particle.rotation);
            if (particle.shape == ParticleShape.PAPER) {
                canvas.drawRect(-particle.size, -particle.size * 0.6f, particle.size, particle.size * 0.6f, paint);
            } else if (particle.shape == ParticleShape.GLASS) {
                Path path = new Path();
                path.moveTo(0f, -particle.size);
                path.lineTo(particle.size, particle.size);
                path.lineTo(-particle.size, particle.size * 0.4f);
                path.close();
                canvas.drawPath(path, paint);
            } else if (particle.shape == ParticleShape.SPARK) {
                paint.setStrokeWidth(3f);
                canvas.drawLine(-particle.size, 0f, particle.size, 0f, paint);
            } else {
                canvas.drawCircle(0f, 0f, particle.size, paint);
            }
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int action = event.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            return handleTouchDown(x, y);
        }
        if (action == MotionEvent.ACTION_MOVE && state == GameState.AIMING && dragging) {
            updateDrag(x, y);
            return true;
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && state == GameState.AIMING && dragging) {
            launchProjectile();
            return true;
        }
        return true;
    }

    private boolean handleTouchDown(float x, float y) {
        if (state == GameState.MENU) {
            if (playButton.contains(x, y)) {
                startGame();
            } else if (howToButton.contains(x, y)) {
                state = GameState.HOW_TO;
            }
            return true;
        }
        if (state == GameState.HOW_TO) {
            state = GameState.MENU;
            return true;
        }
        if (state == GameState.INTRO) {
            state = GameState.AIMING;
            return true;
        }
        if (state == GameState.HIT) {
            if (levelIndex >= MAX_LEVELS - 1) {
                state = GameState.COMPLETE;
            } else {
                levelIndex++;
                loadLevel(levelIndex);
            }
            return true;
        }
        if (state == GameState.COMPLETE) {
            state = GameState.MENU;
            return true;
        }
        if (state == GameState.MISS) {
            state = GameState.AIMING;
            missShown = false;
            return true;
        }
        if (state == GameState.AIMING && distance(x, y, projectile.x, projectile.y) < projectile.radius * 2.5f) {
            dragging = true;
            updateDrag(x, y);
            return true;
        }
        return true;
    }

    private void startGame() {
        totalStars = 0;
        levelIndex = 0;
        loadLevel(levelIndex);
    }

    private void loadLevel(int index) {
        level = levels[index];
        attempts = 0;
        targetDone = false;
        levelCleared = false;
        missShown = false;
        message = "";
        messageTimer = 0f;
        targetAnim = 0f;
        particles.clear();
        projectile = new Projectile(level.projectileType);
        layoutLevel(getWidth(), getHeight());
        resetProjectile(true);
        state = GameState.INTRO;
    }

    private void resetProjectile(boolean resetAttemptsMessage) {
        projectile.type = level.projectileType;
        projectile.radius = level.projectileType.radius;
        projectile.x = startX;
        projectile.y = startY;
        projectile.vx = 0f;
        projectile.vy = 0f;
        projectile.rotation = 0f;
        projectile.sleepTimer = 0f;
        projectile.bounces = 0;
        dragging = false;
        dragX = startX;
        dragY = startY;
        if (resetAttemptsMessage) {
            message = "";
        }
    }

    private void updateDrag(float x, float y) {
        float dx = x - startX;
        float dy = y - startY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > MAX_DRAG) {
            dx = dx / len * MAX_DRAG;
            dy = dy / len * MAX_DRAG;
        }
        dragX = startX + dx;
        dragY = startY + dy;
    }

    private void launchProjectile() {
        if (currentPower01() < 0.05f) {
            dragging = false;
            return;
        }
        float[] v = currentLaunchVelocity();
        projectile.vx = v[0];
        projectile.vy = v[1];
        projectile.sleepTimer = 0f;
        projectile.bounces = 0;
        attempts++;
        dragging = false;
        state = GameState.FLYING;
    }

    private float[] currentLaunchVelocity() {
        return new float[]{(startX - dragX) * LAUNCH_POWER * projectile.type.power, (startY - dragY) * LAUNCH_POWER * projectile.type.power};
    }

    private float currentPower01() {
        return Math.min(1f, distance(startX, startY, dragX, dragY) / MAX_DRAG);
    }

    private void layoutLevel(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        groundY = height * level.groundRatio;
        startX = width * level.startXRatio;
        startY = groundY - projectile.radius;
        targetRect.set(width * level.targetLeft, height * level.targetTop, width * level.targetRight, height * level.targetBottom);
        obstacles.clear();
        if (level.targetType == TargetType.TRICK_TARGET) {
            helperRect.set(width * 0.48f, groundY - 95f, width * 0.60f, groundY - 20f);
            obstacles.add(new Obstacle(new RectF(helperRect), ObstacleKind.CRATE, Color.argb(220, 190, 155, 105)));
        } else if (level.targetType == TargetType.CAN) {
            obstacles.add(new Obstacle(new RectF(width * 0.55f, groundY - 64f, width * 0.62f, groundY), ObstacleKind.CRATE, Color.argb(220, 190, 155, 105)));
        }
        if (!dragging && (state == GameState.AIMING || state == GameState.INTRO || state == GameState.MISS)) {
            resetProjectile(false);
        }
    }

    private Level[] createLevels() {
        return new Level[]{
                new Level("Escritorio", "Bolinha de papel", "acerte a lixeira", ProjectileType.PAPER, TargetType.TRASH, Theme.OFFICE, 0.16f, 0.78f, 0.66f, 0.84f, 0.78f, 0.46f, -34f),
                new Level("Quintal a noite", "Pedra", "quebre a lampada", ProjectileType.STONE, TargetType.LAMP, Theme.NIGHT_YARD, 0.15f, 0.74f, 0.30f, 0.80f, 0.45f, 0.82f, 8f),
                new Level("Lata no muro", "Bola", "derrube a lata", ProjectileType.BALL, TargetType.CAN, Theme.WALL, 0.15f, 0.74f, 0.42f, 0.70f, 0.56f, 0.83f, -8f),
                new Level("Copo na mesa", "Moeda", "caia dentro do copo", ProjectileType.COIN, TargetType.CUP, Theme.TABLE, 0.16f, 0.70f, 0.50f, 0.73f, 0.66f, 0.80f, 18f),
                new Level("Campainha distante", "Pedra pequena", "acerte a campainha", ProjectileType.SMALL_STONE, TargetType.DOORBELL, Theme.HOUSE, 0.14f, 0.78f, 0.38f, 0.83f, 0.49f, 0.84f, -12f),
                new Level("Trickshot de garagem", "Bola", "quique na caixa e acerte o alvo", ProjectileType.BALL, TargetType.TRICK_TARGET, Theme.GARAGE, 0.14f, 0.77f, 0.42f, 0.78f, 0.48f, 0.86f, 0f)
        };
    }

    private void drawVerticalBackground(Canvas canvas, int top, int bottom) {
        LinearGradient gradient = new LinearGradient(0f, 0f, 0f, canvas.getHeight(), top, bottom, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0f, 0f, canvas.getWidth(), canvas.getHeight(), paint);
        paint.setShader(null);
    }

    private void drawWallAndFloor(Canvas canvas, int wallColor, int floorColor) {
        paint.setColor(wallColor);
        canvas.drawRect(0f, 0f, canvas.getWidth(), groundY, paint);
        paint.setColor(floorColor);
        canvas.drawRect(0f, groundY, canvas.getWidth(), canvas.getHeight(), paint);
        paint.setColor(Color.argb(45, 0, 0, 0));
        canvas.drawRect(0f, groundY - 5f, canvas.getWidth(), groundY + 2f, paint);
    }

    private void drawDesk(Canvas canvas, float left, float top, float right, float bottom) {
        paint.setColor(Color.rgb(126, 77, 45));
        canvas.drawRoundRect(new RectF(left, top, right, bottom), 12f, 12f, paint);
        paint.setColor(Color.rgb(91, 54, 35));
        canvas.drawRect(left + 18f, bottom, left + 38f, groundY, paint);
        canvas.drawRect(right - 38f, bottom, right - 18f, groundY, paint);
        paint.setColor(Color.argb(45, 0, 0, 0));
        canvas.drawOval(new RectF(left + 8f, groundY - 8f, right - 8f, groundY + 12f), paint);
    }

    private void drawPaperSheet(Canvas canvas, float x, float y, float rotation) {
        canvas.save();
        canvas.rotate(rotation * 57f, x, y);
        paint.setColor(Color.rgb(251, 251, 241));
        canvas.drawRoundRect(new RectF(x - 24f, y - 16f, x + 24f, y + 16f), 4f, 4f, paint);
        paint.setColor(Color.rgb(190, 205, 218));
        canvas.drawRect(x - 16f, y - 6f, x + 15f, y - 3f, paint);
        canvas.drawRect(x - 16f, y + 3f, x + 10f, y + 6f, paint);
        canvas.restore();
    }

    private void drawTrashCan(Canvas canvas, RectF rect, float anim) {
        float wobble = (float) Math.sin(anim * Math.PI * 6f) * 8f;
        canvas.save();
        canvas.rotate(wobble, rect.centerX(), rect.bottom);
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(new RectF(rect.left - 10f, rect.bottom - 4f, rect.right + 10f, rect.bottom + 14f), paint);
        paint.setColor(Color.rgb(77, 130, 150));
        Path body = new Path();
        body.moveTo(rect.left + 12f, rect.top + 18f);
        body.lineTo(rect.right - 12f, rect.top + 18f);
        body.lineTo(rect.right - 24f, rect.bottom);
        body.lineTo(rect.left + 24f, rect.bottom);
        body.close();
        canvas.drawPath(body, paint);
        paint.setColor(Color.rgb(99, 158, 180));
        canvas.drawOval(new RectF(rect.left + 4f, rect.top, rect.right - 4f, rect.top + 34f), paint);
        paint.setColor(Color.rgb(50, 91, 112));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawOval(new RectF(rect.left + 8f, rect.top + 5f, rect.right - 8f, rect.top + 28f), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawLamp(Canvas canvas, RectF rect, boolean broken, float anim) {
        paint.setColor(Color.rgb(25, 27, 33));
        paint.setStrokeWidth(5f);
        canvas.drawLine(rect.centerX(), 0f, rect.centerX(), rect.top, paint);
        if (!broken) {
            paint.setColor(Color.argb(120, 255, 235, 120));
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * (1.4f + anim), paint);
        }
        paint.setColor(broken ? Color.rgb(80, 90, 100) : Color.rgb(255, 236, 115));
        canvas.drawOval(rect, paint);
        paint.setColor(Color.rgb(80, 74, 66));
        canvas.drawRect(rect.left + rect.width() * 0.28f, rect.top - 10f, rect.right - rect.width() * 0.28f, rect.top + 8f, paint);
        if (broken) {
            paint.setColor(Color.rgb(23, 25, 32));
            canvas.drawRect(0f, rect.bottom + 30f, canvas.getWidth(), groundY, paint);
        }
    }

    private void drawCan(Canvas canvas, RectF rect, boolean fallen, float anim) {
        canvas.save();
        if (fallen) {
            canvas.rotate(80f + anim * 35f, rect.centerX(), rect.bottom);
        } else {
            canvas.rotate((float) Math.sin(anim * 15f) * 10f, rect.centerX(), rect.bottom);
        }
        paint.setColor(Color.argb(65, 0, 0, 0));
        canvas.drawOval(new RectF(rect.left - 8f, rect.bottom - 2f, rect.right + 8f, rect.bottom + 12f), paint);
        paint.setColor(Color.rgb(224, 62, 68));
        canvas.drawRoundRect(rect, 12f, 12f, paint);
        paint.setColor(Color.rgb(246, 225, 90));
        canvas.drawRect(rect.left, rect.top + rect.height() * 0.35f, rect.right, rect.top + rect.height() * 0.58f, paint);
        paint.setColor(Color.rgb(190, 45, 51));
        canvas.drawOval(new RectF(rect.left, rect.top - 8f, rect.right, rect.top + 13f), paint);
        canvas.restore();
    }

    private void drawCup(Canvas canvas, RectF rect, float anim) {
        canvas.save();
        canvas.rotate((float) Math.sin(anim * Math.PI * 7f) * 7f, rect.centerX(), rect.bottom);
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(new RectF(rect.left - 8f, rect.bottom - 3f, rect.right + 8f, rect.bottom + 12f), paint);
        paint.setColor(Color.argb(210, 210, 235, 245));
        Path cup = new Path();
        cup.moveTo(rect.left, rect.top + 10f);
        cup.lineTo(rect.right, rect.top + 10f);
        cup.lineTo(rect.right - 13f, rect.bottom);
        cup.lineTo(rect.left + 13f, rect.bottom);
        cup.close();
        canvas.drawPath(cup, paint);
        paint.setColor(Color.rgb(245, 252, 255));
        canvas.drawOval(new RectF(rect.left - 6f, rect.top, rect.right + 6f, rect.top + 24f), paint);
        paint.setColor(Color.rgb(120, 175, 205));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        canvas.drawOval(new RectF(rect.left - 6f, rect.top, rect.right + 6f, rect.top + 24f), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawDoorbell(Canvas canvas, RectF rect, float anim) {
        paint.setColor(Color.rgb(95, 71, 58));
        canvas.drawRoundRect(new RectF(rect.left - 24f, rect.top - 42f, rect.right + 24f, rect.bottom + 42f), 14f, 14f, paint);
        paint.setColor(anim > 0f ? Color.rgb(255, 218, 70) : Color.rgb(235, 236, 224));
        canvas.drawRoundRect(rect, 10f, 10f, paint);
        paint.setColor(Color.rgb(70, 72, 82));
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.23f + anim * 8f, paint);
        if (anim > 0f) {
            paint.setColor(Color.argb((int) (120 * anim), 255, 220, 80));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.8f, paint);
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 1.15f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawFinalTarget(Canvas canvas, RectF rect, float anim) {
        canvas.save();
        canvas.rotate((float) Math.sin(anim * 20f) * 8f, rect.centerX(), rect.centerY());
        paint.setColor(Color.rgb(240, 243, 246));
        canvas.drawOval(rect, paint);
        paint.setColor(Color.rgb(226, 73, 73));
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.34f, paint);
        paint.setColor(Color.rgb(255, 225, 90));
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.15f, paint);
        canvas.restore();
    }

    private void drawHouseFront(Canvas canvas) {
        paint.setColor(Color.rgb(109, 84, 70));
        canvas.drawRect(canvas.getWidth() * 0.68f, groundY - 210f, canvas.getWidth() * 0.96f, groundY, paint);
        paint.setColor(Color.rgb(71, 90, 104));
        canvas.drawRect(canvas.getWidth() * 0.38f, groundY - 125f, canvas.getWidth() * 0.67f, groundY, paint);
        paint.setColor(Color.rgb(54, 62, 70));
        canvas.drawRect(canvas.getWidth() * 0.40f, groundY - 112f, canvas.getWidth() * 0.45f, groundY, paint);
        canvas.drawRect(canvas.getWidth() * 0.50f, groundY - 112f, canvas.getWidth() * 0.55f, groundY, paint);
        canvas.drawRect(canvas.getWidth() * 0.60f, groundY - 112f, canvas.getWidth() * 0.65f, groundY, paint);
    }

    private void drawGarageShelves(Canvas canvas) {
        paint.setColor(Color.rgb(75, 67, 60));
        canvas.drawRect(canvas.getWidth() * 0.66f, groundY - 185f, canvas.getWidth() * 0.93f, groundY - 172f, paint);
        canvas.drawRect(canvas.getWidth() * 0.66f, groundY - 105f, canvas.getWidth() * 0.93f, groundY - 92f, paint);
        drawBox(canvas, new RectF(canvas.getWidth() * 0.70f, groundY - 165f, canvas.getWidth() * 0.78f, groundY - 108f), 0f);
        drawBox(canvas, new RectF(canvas.getWidth() * 0.81f, groundY - 155f, canvas.getWidth() * 0.90f, groundY - 108f), 0f);
    }

    private void drawBrickWall(Canvas canvas, float left, float top, float width, float height) {
        paint.setColor(Color.rgb(155, 104, 76));
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height), 8f, 8f, paint);
        paint.setColor(Color.argb(60, 80, 45, 35));
        paint.setStrokeWidth(3f);
        for (int i = 1; i < 4; i++) {
            canvas.drawLine(left, top + height * i / 4f, left + width, top + height * i / 4f, paint);
        }
        for (int i = 1; i < 7; i++) {
            canvas.drawLine(left + width * i / 7f, top, left + width * i / 7f, top + height, paint);
        }
    }

    private void drawCrate(Canvas canvas, float left, float top, float width, float height, float anim) {
        RectF rect = new RectF(left, top, left + width, top + height);
        paint.setColor(Color.rgb(157, 110, 63));
        canvas.drawRoundRect(rect, 6f, 6f, paint);
        paint.setColor(Color.rgb(112, 74, 43));
        paint.setStrokeWidth(5f);
        canvas.drawLine(rect.left + 8f, rect.top + 8f, rect.right - 8f, rect.bottom - 8f, paint);
        canvas.drawLine(rect.right - 8f, rect.top + 8f, rect.left + 8f, rect.bottom - 8f, paint);
        canvas.drawRect(rect.left + 6f, rect.top + 6f, rect.right - 6f, rect.top + 13f, paint);
    }

    private void drawBox(Canvas canvas, RectF rect, float anim) {
        paint.setColor(Color.rgb(172, 132, 83));
        canvas.drawRoundRect(rect, 7f, 7f, paint);
        paint.setColor(Color.rgb(118, 85, 55));
        paint.setStrokeWidth(4f);
        canvas.drawLine(rect.left + 8f, rect.centerY(), rect.right - 8f, rect.centerY(), paint);
        canvas.drawLine(rect.centerX(), rect.top + 7f, rect.centerX(), rect.bottom - 7f, paint);
    }

    private void drawPaperBallIcon(Canvas canvas, float x, float y, float radius, float rotation) {
        canvas.save();
        canvas.rotate(rotation * 57f, x, y);
        paint.setColor(Color.rgb(244, 244, 232));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.rgb(205, 210, 208));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, radius * 0.10f));
        canvas.drawArc(new RectF(x - radius * 0.7f, y - radius * 0.45f, x + radius * 0.6f, y + radius * 0.45f), 190f, 100f, false, paint);
        canvas.drawLine(x - radius * 0.55f, y + radius * 0.15f, x + radius * 0.45f, y - radius * 0.22f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawStoneIcon(Canvas canvas, float x, float y, float radius, float rotation) {
        canvas.save();
        canvas.rotate(rotation * 57f, x, y);
        Path path = new Path();
        path.moveTo(x - radius * 0.85f, y - radius * 0.25f);
        path.lineTo(x - radius * 0.20f, y - radius * 0.85f);
        path.lineTo(x + radius * 0.80f, y - radius * 0.42f);
        path.lineTo(x + radius * 0.68f, y + radius * 0.48f);
        path.lineTo(x - radius * 0.35f, y + radius * 0.82f);
        path.close();
        paint.setColor(Color.rgb(105, 111, 116));
        canvas.drawPath(path, paint);
        paint.setColor(Color.rgb(145, 151, 154));
        canvas.drawCircle(x - radius * 0.15f, y - radius * 0.25f, radius * 0.24f, paint);
        canvas.restore();
    }

    private void drawBallIcon(Canvas canvas, float x, float y, float radius, float rotation) {
        paint.setColor(Color.rgb(246, 132, 71));
        canvas.drawCircle(x, y, radius, paint);
        canvas.save();
        canvas.rotate(rotation * 57f, x, y);
        paint.setColor(Color.rgb(255, 210, 110));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, radius * 0.12f));
        canvas.drawLine(x - radius, y, x + radius, y, paint);
        canvas.drawArc(new RectF(x - radius * 0.55f, y - radius, x + radius * 0.55f, y + radius), 90f, 180f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restore();
    }

    private void drawCoinIcon(Canvas canvas, float x, float y, float radius, float rotation) {
        canvas.save();
        canvas.scale(1f, 0.55f + 0.25f * Math.abs((float) Math.cos(rotation)), x, y);
        paint.setColor(Color.rgb(230, 176, 59));
        canvas.drawCircle(x, y, radius, paint);
        paint.setColor(Color.rgb(255, 221, 100));
        canvas.drawCircle(x - radius * 0.18f, y - radius * 0.18f, radius * 0.45f, paint);
        canvas.restore();
    }

    private void drawButton(Canvas canvas, RectF rect, String label, int color) {
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(new RectF(rect.left + 4f, rect.top + 7f, rect.right + 4f, rect.bottom + 7f), 22f, 22f, paint);
        paint.setColor(color);
        canvas.drawRoundRect(rect, 22f, 22f, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(26f);
        textPaint.setColor(Color.rgb(31, 36, 45));
        canvas.drawText(label, rect.centerX(), rect.centerY() + 9f, textPaint);
        textPaint.setFakeBoldText(false);
    }

    private void drawPanel(Canvas canvas, float left, float top, float right, float bottom, int color) {
        paint.setColor(Color.argb(85, 0, 0, 0));
        canvas.drawRoundRect(new RectF(left + 6f, top + 10f, right + 6f, bottom + 10f), 28f, 28f, paint);
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), 28f, 28f, paint);
    }

    private void drawStars(Canvas canvas, float cx, float cy, int count) {
        for (int i = 0; i < 3; i++) {
            paint.setColor(i < count ? Color.rgb(255, 199, 54) : Color.rgb(205, 208, 215));
            drawStar(canvas, cx + (i - 1) * 62f, cy, 24f);
        }
    }

    private void drawStar(Canvas canvas, float cx, float cy, float radius) {
        Path path = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float r = (i % 2 == 0) ? radius : radius * 0.45f;
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawSoftCircle(Canvas canvas, float x, float y, float radius, int color) {
        paint.setColor(color);
        canvas.drawCircle(x, y, radius, paint);
    }

    private void addDust(float x, float y, int count, int color) {
        for (int i = 0; i < count; i++) {
            addParticle(x, y, randomRange(-180f, 180f), randomRange(-230f, 80f), randomRange(2f, 6f), color, 0.65f, ParticleShape.DOT);
        }
    }

    private void addParticle(float x, float y, float vx, float vy, float size, int color, float life, ParticleShape shape) {
        Particle particle = new Particle();
        particle.x = x;
        particle.y = y;
        particle.vx = vx;
        particle.vy = vy;
        particle.size = size;
        particle.color = color;
        particle.life = life;
        particle.maxLife = life;
        particle.shape = shape;
        particle.rotation = randomRange(0f, 360f);
        particle.spin = randomRange(-420f, 420f);
        particles.add(particle);
    }

    private boolean circleIntersectsRect(float cx, float cy, float radius, RectF rect) {
        float nearestX = clamp(cx, rect.left, rect.right);
        float nearestY = clamp(cy, rect.top, rect.bottom);
        return distance(cx, cy, nearestX, nearestY) <= radius;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float randomRange(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private enum GameState {
        MENU,
        HOW_TO,
        INTRO,
        AIMING,
        FLYING,
        MISS,
        HIT,
        COMPLETE
    }

    private enum Theme {
        OFFICE,
        NIGHT_YARD,
        WALL,
        TABLE,
        HOUSE,
        GARAGE
    }

    private enum TargetType {
        TRASH,
        LAMP,
        CAN,
        CUP,
        DOORBELL,
        TRICK_TARGET
    }

    private enum ObstacleKind {
        CRATE,
        BOX
    }

    private enum ParticleShape {
        DOT,
        PAPER,
        GLASS,
        SPARK
    }

    private enum ProjectileType {
        PAPER(21f, 1.04f, 680f, 1.35f, 0.12f, 0.018f),
        STONE(18f, 1.12f, 900f, 0.18f, 0.08f, 0.026f),
        BALL(23f, 1.00f, 780f, 0.24f, 0.70f, 0.032f),
        COIN(13f, 1.16f, 840f, 0.42f, 0.22f, 0.052f),
        SMALL_STONE(15f, 1.18f, 880f, 0.24f, 0.12f, 0.035f),
        SMALL_BALL(17f, 1.06f, 780f, 0.30f, 0.62f, 0.040f);

        final float radius;
        final float power;
        final float gravity;
        final float windInfluence;
        final float bounce;
        final float spin;

        ProjectileType(float radius, float power, float gravity, float windInfluence, float bounce, float spin) {
            this.radius = radius;
            this.power = power;
            this.gravity = gravity;
            this.windInfluence = windInfluence;
            this.bounce = bounce;
            this.spin = spin;
        }
    }

    private static class Level {
        final String title;
        final String objectName;
        final String objective;
        final ProjectileType projectileType;
        final TargetType targetType;
        final Theme theme;
        final float startXRatio;
        final float groundRatio;
        final float targetTop;
        final float targetLeft;
        final float targetBottom;
        final float targetRight;
        final float wind;

        Level(String title, String objectName, String objective, ProjectileType projectileType, TargetType targetType, Theme theme,
              float startXRatio, float groundRatio, float targetTop, float targetLeft, float targetBottom, float targetRight, float wind) {
            this.title = title;
            this.objectName = objectName;
            this.objective = objective;
            this.projectileType = projectileType;
            this.targetType = targetType;
            this.theme = theme;
            this.startXRatio = startXRatio;
            this.groundRatio = groundRatio;
            this.targetTop = targetTop;
            this.targetLeft = targetLeft;
            this.targetBottom = targetBottom;
            this.targetRight = targetRight;
            this.wind = wind;
        }
    }

    private static class Projectile {
        ProjectileType type;
        float x;
        float y;
        float vx;
        float vy;
        float radius;
        float rotation;
        float sleepTimer;
        int bounces;

        Projectile(ProjectileType type) {
            this.type = type;
            this.radius = type.radius;
        }
    }

    private static class Obstacle {
        final RectF rect;
        final ObstacleKind kind;
        final int particleColor;
        float hitAnim;

        Obstacle(RectF rect, ObstacleKind kind, int particleColor) {
            this.rect = rect;
            this.kind = kind;
            this.particleColor = particleColor;
        }
    }

    private static class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float life;
        float maxLife;
        float rotation;
        float spin;
        int color;
        ParticleShape shape;
    }
}
