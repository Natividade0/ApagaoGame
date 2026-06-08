package com.natividade0.apagaogame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GameView extends SurfaceView implements Runnable, SurfaceHolder.Callback {
    private static final int EMPTY = 0;
    private static final int WALL = 1;
    private static final int EXIT = 2;
    private static final float PLAYER_SPEED = 4.8f;
    private static final float ENEMY_PATROL_SPEED = 1.55f;
    private static final float ENEMY_HUNT_SPEED = 2.85f;
    private static final float PLAYER_RADIUS = 0.28f;
    private static final float ENEMY_RADIUS = 0.30f;
    private static final float LIGHT_DURATION = 2.2f;
    private static final float LIGHT_RADIUS = 4.4f;
    private static final float SAFE_REVEAL_RADIUS = 1.25f;
    private static final float BATTERY_MAX = 100f;
    private static final float BATTERY_COST = 22f;
    private static final float BATTERY_RECHARGE = 4.5f;
    private static final float ENEMY_ATTRACTION_TIME = 4.2f;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Enemy> enemies = new ArrayList<>();

    private Thread thread;
    private volatile boolean running;
    private int[][] tiles;
    private int cols;
    private int rows;
    private int levelIndex;
    private int completedLevels;
    private float playerX;
    private float playerY;
    private float startX;
    private float startY;
    private float exitX;
    private float exitY;
    private float battery = BATTERY_MAX;
    private float lightTimer;
    private float dangerTimer;
    private float lastPulseX;
    private float lastPulseY;
    private float moveX;
    private float moveY;
    private float cellSize;
    private float offsetX;
    private float offsetY;
    private RectF lightButton = new RectF();
    private RectF restartButton = new RectF();
    private GameState state = GameState.PLAYING;
    private String message = "";

    private final String[][] levels = new String[][]{
            {
                    "###############",
                    "#P....#.......#",
                    "#.###.#.#####.#",
                    "#...#...#...#.#",
                    "###.#####.#.#.#",
                    "#...#.....#...#",
                    "#.###.#######.#",
                    "#.....#....E..#",
                    "###############"
            },
            {
                    "#################",
                    "#P..#...........#",
                    "###.#.#########.#",
                    "#...#.....#.....#",
                    "#.#######.#.#####",
                    "#.......#.#.....#",
                    "#.#####.#.#####.#",
                    "#.#.....#.....#.#",
                    "#.#.#########.#.#",
                    "#.......E.....#.#",
                    "#################"
            },
            {
                    "###################",
                    "#P....#...........#",
                    "#.###.#.#########.#",
                    "#...#.#.....#.....#",
                    "###.#.#####.#.###.#",
                    "#...#.....#.#.#...#",
                    "#.#######.#.#.#.###",
                    "#.....#...#...#...#",
                    "#####.#.#########.#",
                    "#.....#.......E...#",
                    "###################"
            }
    };

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.LEFT);
        loadLevel(0);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        resume();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        calculateBoard(width, height);
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
        thread = new Thread(this, "ApagaoGameLoop");
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
            sleepToSaveBattery();
        }
    }

    private void update(float dt) {
        if (state != GameState.PLAYING) {
            return;
        }

        if (lightTimer > 0f) {
            lightTimer = Math.max(0f, lightTimer - dt);
        } else {
            battery = Math.min(BATTERY_MAX, battery + BATTERY_RECHARGE * dt);
        }
        dangerTimer = Math.max(0f, dangerTimer - dt);

        movePlayer(dt);
        for (Enemy enemy : enemies) {
            enemy.update(dt);
            if (distance(playerX, playerY, enemy.x, enemy.y) < PLAYER_RADIUS + ENEMY_RADIUS) {
                state = GameState.DEFEAT;
                message = "Derrota! O perigo encontrou você.";
            }
        }

        if (distance(playerX, playerY, exitX, exitY) < 0.45f) {
            completedLevels++;
            if (completedLevels >= levels.length) {
                state = GameState.VICTORY;
                message = "Vitória! Você escapou de todas as fases.";
            } else {
                loadLevel(completedLevels);
            }
        }
    }

    private void movePlayer(float dt) {
        float length = (float) Math.sqrt(moveX * moveX + moveY * moveY);
        if (length <= 0.05f) {
            return;
        }
        float vx = moveX / length * PLAYER_SPEED * dt;
        float vy = moveY / length * PLAYER_SPEED * dt;
        float nextX = playerX + vx;
        if (!collides(nextX, playerY, PLAYER_RADIUS)) {
            playerX = nextX;
        }
        float nextY = playerY + vy;
        if (!collides(playerX, nextY, PLAYER_RADIUS)) {
            playerY = nextY;
        }
    }

    private void drawFrame() {
        if (!holder.getSurface().isValid()) {
            return;
        }
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return;
        }
        try {
            canvas.drawColor(Color.rgb(5, 5, 8));
            calculateBoard(canvas.getWidth(), canvas.getHeight());
            drawWorld(canvas);
            drawDarkness(canvas);
            drawHud(canvas);
            if (state != GameState.PLAYING) {
                drawEndPanel(canvas);
            }
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawWorld(Canvas canvas) {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (!isVisible(x + 0.5f, y + 0.5f) && state == GameState.PLAYING) {
                    continue;
                }
                float left = offsetX + x * cellSize;
                float top = offsetY + y * cellSize;
                if (tiles[y][x] == WALL) {
                    paint.setColor(Color.rgb(62, 68, 83));
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
                    paint.setColor(Color.rgb(35, 39, 48));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2f);
                    canvas.drawRect(left + 2f, top + 2f, left + cellSize - 2f, top + cellSize - 2f, paint);
                    paint.setStyle(Paint.Style.FILL);
                } else if (tiles[y][x] == EXIT) {
                    paint.setColor(Color.rgb(56, 178, 95));
                    canvas.drawRoundRect(new RectF(left + 6f, top + 6f, left + cellSize - 6f, top + cellSize - 6f), 12f, 12f, paint);
                } else {
                    paint.setColor(Color.rgb(21, 24, 32));
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, paint);
                }
            }
        }
        drawEnemies(canvas);
        drawPlayer(canvas);
    }

    private void drawEnemies(Canvas canvas) {
        for (Enemy enemy : enemies) {
            if (!isVisible(enemy.x, enemy.y) && state == GameState.PLAYING) {
                continue;
            }
            paint.setColor(dangerTimer > 0f ? Color.rgb(230, 56, 71) : Color.rgb(155, 63, 78));
            canvas.drawCircle(worldX(enemy.x), worldY(enemy.y), ENEMY_RADIUS * cellSize, paint);
            paint.setColor(Color.rgb(30, 0, 0));
            canvas.drawCircle(worldX(enemy.x - 0.08f), worldY(enemy.y - 0.06f), 0.035f * cellSize, paint);
            canvas.drawCircle(worldX(enemy.x + 0.08f), worldY(enemy.y - 0.06f), 0.035f * cellSize, paint);
        }
    }

    private void drawPlayer(Canvas canvas) {
        paint.setColor(Color.rgb(104, 176, 255));
        canvas.drawCircle(worldX(playerX), worldY(playerY), PLAYER_RADIUS * cellSize, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(worldX(playerX + 0.08f), worldY(playerY - 0.08f), 0.045f * cellSize, paint);
    }

    private void drawDarkness(Canvas canvas) {
        if (state != GameState.PLAYING) {
            return;
        }
        float radius = (lightTimer > 0f ? LIGHT_RADIUS : SAFE_REVEAL_RADIUS) * cellSize;
        float centerX = worldX(playerX);
        float centerY = worldY(playerY);
        int transparent = Color.argb(lightTimer > 0f ? 15 : 95, 0, 0, 0);
        RadialGradient gradient = new RadialGradient(centerX, centerY, radius,
                new int[]{Color.TRANSPARENT, transparent, Color.argb(245, 0, 0, 0)},
                new float[]{0f, 0.60f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
        paint.setShader(null);
    }

    private void drawHud(Canvas canvas) {
        float w = canvas.getWidth();
        float h = canvas.getHeight();
        textPaint.setTextSize(32f);
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Apagão", 24f, 42f, textPaint);
        textPaint.setTextSize(22f);
        canvas.drawText(String.format(Locale.US, "Fase %d/%d", levelIndex + 1, levels.length), 24f, 72f, textPaint);

        paint.setColor(Color.rgb(46, 49, 59));
        canvas.drawRoundRect(new RectF(24f, 88f, 254f, 116f), 12f, 12f, paint);
        paint.setColor(battery >= BATTERY_COST ? Color.rgb(248, 211, 106) : Color.rgb(122, 88, 49));
        canvas.drawRoundRect(new RectF(28f, 92f, 28f + 222f * (battery / BATTERY_MAX), 112f), 10f, 10f, paint);
        textPaint.setTextSize(18f);
        textPaint.setColor(Color.rgb(220, 226, 235));
        canvas.drawText("Bateria", 264f, 110f, textPaint);

        float buttonSize = Math.min(138f, h * 0.20f);
        lightButton.set(w - buttonSize - 34f, h - buttonSize - 34f, w - 34f, h - 34f);
        paint.setColor(battery >= BATTERY_COST ? Color.rgb(249, 211, 106) : Color.rgb(75, 72, 65));
        canvas.drawOval(lightButton, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(22f);
        textPaint.setColor(Color.rgb(14, 16, 22));
        canvas.drawText("LUZ", lightButton.centerX(), lightButton.centerY() + 8f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);

        if (dangerTimer > 0f) {
            textPaint.setTextSize(22f);
            textPaint.setColor(Color.rgb(255, 115, 119));
            canvas.drawText("Perigo atraído pela luz!", 24f, 148f, textPaint);
        }

        textPaint.setTextSize(18f);
        textPaint.setColor(Color.rgb(190, 198, 210));
        canvas.drawText("Arraste no lado esquerdo para andar. Toque LUZ para revelar o labirinto.", 24f, h - 22f, textPaint);
    }

    private void drawEndPanel(Canvas canvas) {
        paint.setColor(Color.argb(215, 0, 0, 0));
        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42f);
        canvas.drawText(message, canvas.getWidth() / 2f, canvas.getHeight() / 2f - 44f, textPaint);
        textPaint.setTextSize(24f);
        canvas.drawText("Toque para jogar novamente", canvas.getWidth() / 2f, canvas.getHeight() / 2f + 2f, textPaint);
        restartButton.set(canvas.getWidth() / 2f - 150f, canvas.getHeight() / 2f + 28f, canvas.getWidth() / 2f + 150f, canvas.getHeight() / 2f + 88f);
        paint.setColor(Color.rgb(104, 176, 255));
        canvas.drawRoundRect(restartButton, 18f, 18f, paint);
        textPaint.setColor(Color.rgb(8, 12, 18));
        textPaint.setTextSize(24f);
        canvas.drawText("Reiniciar", canvas.getWidth() / 2f, canvas.getHeight() / 2f + 67f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (state != GameState.PLAYING) {
                completedLevels = 0;
                loadLevel(0);
                return true;
            }
            if (lightButton.contains(x, y)) {
                pulseLight();
                return true;
            }
        }
        if (state == GameState.PLAYING) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                moveX = 0f;
                moveY = 0f;
            } else if (x < getWidth() * 0.58f) {
                float baseX = getWidth() * 0.18f;
                float baseY = getHeight() * 0.72f;
                moveX = clamp((x - baseX) / 90f, -1f, 1f);
                moveY = clamp((y - baseY) / 90f, -1f, 1f);
            }
        }
        return true;
    }

    private void pulseLight() {
        if (battery < BATTERY_COST) {
            return;
        }
        battery -= BATTERY_COST;
        lightTimer = LIGHT_DURATION;
        dangerTimer = ENEMY_ATTRACTION_TIME;
        lastPulseX = playerX;
        lastPulseY = playerY;
    }

    private void loadLevel(int index) {
        levelIndex = index;
        String[] map = levels[index];
        rows = map.length;
        cols = map[0].length();
        tiles = new int[rows][cols];
        enemies.clear();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                char c = map[y].charAt(x);
                if (c == '#') {
                    tiles[y][x] = WALL;
                } else if (c == 'P') {
                    startX = x + 0.5f;
                    startY = y + 0.5f;
                } else if (c == 'E') {
                    tiles[y][x] = EXIT;
                    exitX = x + 0.5f;
                    exitY = y + 0.5f;
                }
            }
        }
        playerX = startX;
        playerY = startY;
        battery = BATTERY_MAX;
        lightTimer = 0f;
        dangerTimer = 0f;
        moveX = 0f;
        moveY = 0f;
        state = GameState.PLAYING;
        message = "";
        lastPulseX = startX;
        lastPulseY = startY;
        spawnEnemies(index + 1);
    }

    private void spawnEnemies(int amount) {
        int spawned = 0;
        for (int y = rows - 2; y >= 1 && spawned < amount; y--) {
            for (int x = cols - 2; x >= 1 && spawned < amount; x--) {
                if (tiles[y][x] == EMPTY && distance(x + 0.5f, y + 0.5f, startX, startY) > 5f && distance(x + 0.5f, y + 0.5f, exitX, exitY) > 1.5f) {
                    enemies.add(new Enemy(x + 0.5f, y + 0.5f));
                    spawned++;
                }
            }
        }
    }

    private boolean collides(float x, float y, float radius) {
        int minX = (int) Math.floor(x - radius);
        int maxX = (int) Math.floor(x + radius);
        int minY = (int) Math.floor(y - radius);
        int maxY = (int) Math.floor(y + radius);
        for (int ty = minY; ty <= maxY; ty++) {
            for (int tx = minX; tx <= maxX; tx++) {
                if (tx < 0 || ty < 0 || tx >= cols || ty >= rows || tiles[ty][tx] == WALL) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isVisible(float x, float y) {
        float radius = lightTimer > 0f ? LIGHT_RADIUS : SAFE_REVEAL_RADIUS;
        return distance(x, y, playerX, playerY) <= radius;
    }

    private void calculateBoard(int width, int height) {
        if (width <= 0 || height <= 0 || cols == 0 || rows == 0) {
            return;
        }
        float hudPadding = 28f;
        cellSize = Math.min((width - hudPadding * 2f) / cols, (height - 110f) / rows);
        offsetX = (width - cols * cellSize) / 2f;
        offsetY = Math.max(92f, (height - rows * cellSize) / 2f);
    }

    private float worldX(float tileX) {
        return offsetX + tileX * cellSize;
    }

    private float worldY(float tileY) {
        return offsetY + tileY * cellSize;
    }

    private float distance(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sleepToSaveBattery() {
        try {
            Thread.sleep(16L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private enum GameState {
        PLAYING,
        VICTORY,
        DEFEAT
    }

    private class Enemy {
        float x;
        float y;
        float wanderAngle;

        Enemy(float x, float y) {
            this.x = x;
            this.y = y;
            this.wanderAngle = (x + y) % 6.28f;
        }

        void update(float dt) {
            float targetX;
            float targetY;
            float speed;
            if (dangerTimer > 0f) {
                targetX = lastPulseX;
                targetY = lastPulseY;
                speed = ENEMY_HUNT_SPEED;
            } else {
                wanderAngle += dt * 0.8f;
                targetX = x + (float) Math.cos(wanderAngle);
                targetY = y + (float) Math.sin(wanderAngle * 0.7f);
                speed = ENEMY_PATROL_SPEED;
            }
            float dx = targetX - x;
            float dy = targetY - y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 0.05f) {
                return;
            }
            float stepX = dx / length * speed * dt;
            float stepY = dy / length * speed * dt;
            if (!collides(x + stepX, y, ENEMY_RADIUS)) {
                x += stepX;
            } else {
                wanderAngle += 1.4f;
            }
            if (!collides(x, y + stepY, ENEMY_RADIUS)) {
                y += stepY;
            } else {
                wanderAngle += 1.1f;
            }
        }
    }
}
