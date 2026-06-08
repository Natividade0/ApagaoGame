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
    private static final float POWER = 5.9f;
    private static final float GRAVITY = 650f;
    private static final float MIN_PULL = 22f;

    private final SurfaceHolder holder;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Level> levels = new ArrayList<>();

    private Thread thread;
    private volatile boolean running;
    private int w, h;
    private float groundY;
    private float time;

    private Scene scene = Scene.MENU;
    private int levelIndex;
    private int attempts;
    private int stars;

    private float launchX, launchY, dragX, dragY;
    private boolean dragging;
    private float x, y, vx, vy, radius, rotation;
    private float stopTimer, fxTimer, shakeTimer;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
        setFocusable(true);
        setupPaints();
        setupLevels();
    }

    private void setupPaints() {
        titlePaint.setColor(Color.WHITE);
        titlePaint.setFakeBoldText(true);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void setupLevels() {
        levels.add(new Level("Flecha no alvo", Mode.TARGET, Obj.ARROW, 0.13f, 0.72f, 0.68f, 0.45f, 0f, 0.92f, 0.12f, Theme.RANGE));
        levels.add(new Level("Bolinha no cesto", Mode.BIN, Obj.PAPER, 0.15f, 0.68f, 0.67f, 0.62f, 0f, 0.88f, 0.32f, Theme.OFFICE));
        levels.add(new Level("Pedra na lampada", Mode.LAMP, Obj.STONE, 0.13f, 0.72f, 0.70f, 0.32f, -10f, 0.96f, 0.25f, Theme.STREET));
        levels.add(new Level("Flecha com vento", Mode.TARGET, Obj.ARROW, 0.13f, 0.72f, 0.72f, 0.42f, 25f, 0.92f, 0.12f, Theme.RANGE));
        levels.add(new Level("Cesto com quique", Mode.BIN, Obj.BALL, 0.14f, 0.68f, 0.70f, 0.63f, 0f, 0.92f, 0.62f, Theme.OFFICE).obstacle(0.46f, 0.71f, 0.57f, 0.77f));
        levels.add(new Level("Pedra com curva", Mode.LAMP, Obj.STONE, 0.13f, 0.74f, 0.74f, 0.30f, -35f, 0.98f, 0.28f, Theme.STREET).obstacle(0.50f, 0.39f, 0.56f, 0.82f));
        levels.add(new Level("Copo em movimento", Mode.CUP, Obj.BALL, 0.13f, 0.68f, 0.68f, 0.63f, 0f, 0.92f, 0.56f, Theme.TABLE).moving(0.055f, 1.05f));
        levels.add(new Level("Trickshot final", Mode.TARGET, Obj.STONE, 0.12f, 0.70f, 0.72f, 0.40f, 0f, 0.96f, 0.45f, Theme.RANGE).obstacle(0.38f, 0.57f, 0.46f, 0.84f).obstacle(0.62f, 0.26f, 0.68f, 0.60f));
    }

    @Override public void surfaceCreated(SurfaceHolder sh) { calc(); reset(false); resume(); }
    @Override public void surfaceChanged(SurfaceHolder sh, int f, int width, int height) { calc(); reset(false); }
    @Override public void surfaceDestroyed(SurfaceHolder sh) { pause(); }

    public void resume() {
        if (running) return;
        running = true;
        thread = new Thread(this, "MiraRealLoop");
        thread.start();
    }

    public void pause() {
        running = false;
        if (thread != null) {
            try { thread.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    @Override public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.033f, (now - last) / 1000000000f);
            last = now;
            update(dt);
            drawFrame();
            try { Thread.sleep(16); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private void calc() {
        w = Math.max(1, getWidth());
        h = Math.max(1, getHeight());
        groundY = h * 0.86f;
        titlePaint.setTextSize(Math.max(40f, h * 0.07f));
        textPaint.setTextSize(Math.max(22f, h * 0.035f));
    }

    private Level level() { return levels.get(levelIndex); }

    private void reset(boolean resetAttempts) {
        Level l = level();
        launchX = l.launchX * w;
        launchY = l.launchY * h;
        x = launchX; y = launchY; vx = 0; vy = 0; rotation = 0;
        dragX = launchX; dragY = launchY; dragging = false;
        radius = objRadius(l.obj);
        stopTimer = 0; fxTimer = 0; shakeTimer = 0;
        if (scene != Scene.MENU) scene = Scene.AIM;
        if (resetAttempts) { attempts = 0; stars = 0; }
    }

    private void update(float dt) {
        time += dt;
        fxTimer = Math.max(0, fxTimer - dt);
        shakeTimer = Math.max(0, shakeTimer - dt);
        if (scene != Scene.FLY) return;

        Level l = level();
        vx += l.wind * dt;
        vy += GRAVITY * l.gravity * dt;
        vx *= Math.max(0.86f, 1f - l.drag * dt);
        vy *= Math.max(0.92f, 1f - l.drag * 0.22f * dt);
        x += vx * dt;
        y += vy * dt;
        rotation = (float) Math.toDegrees(Math.atan2(vy, vx));

        collide(l);
        if (hit(l)) {
            stars = attempts <= 1 ? 3 : attempts <= 3 ? 2 : 1;
            scene = Scene.WIN;
            fxTimer = 1f;
            shakeTimer = 0.22f;
            return;
        }

        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (y + radius >= groundY - 1 && speed < 90) stopTimer += dt; else stopTimer = 0;
        if (stopTimer > 0.8f || x < -220 || x > w + 220 || y > h + 220) {
            scene = Scene.FAIL;
            fxTimer = 0.35f;
        }
    }

    private void collide(Level l) {
        if (y + radius > groundY) {
            y = groundY - radius;
            vy = -Math.abs(vy) * l.bounce;
            vx *= 0.88f;
            shakeTimer = Math.max(shakeTimer, 0.08f);
            if (Math.abs(vy) < 38) vy = 0;
        }
        if (x - radius < 0) { x = radius; vx = Math.abs(vx) * l.bounce; }
        if (x + radius > w) { x = w - radius; vx = -Math.abs(vx) * l.bounce; }
        for (Obstacle o : l.obstacles) {
            RectF r = o.rect(w, h);
            if (circleRect(x, y, radius, r)) resolve(r, l.bounce);
        }
    }

    private void resolve(RectF r, float bounce) {
        float left = Math.abs(x - r.left), right = Math.abs(x - r.right);
        float top = Math.abs(y - r.top), bottom = Math.abs(y - r.bottom);
        float min = Math.min(Math.min(left, right), Math.min(top, bottom));
        if (min == left) { x = r.left - radius; vx = -Math.abs(vx) * bounce; }
        else if (min == right) { x = r.right + radius; vx = Math.abs(vx) * bounce; }
        else if (min == top) { y = r.top - radius; vy = -Math.abs(vy) * bounce; vx *= 0.9f; }
        else { y = r.bottom + radius; vy = Math.abs(vy) * bounce; vx *= 0.9f; }
        shakeTimer = 0.16f;
    }

    private boolean hit(Level l) {
        float tx = targetX(l), ty = l.targetY * h;
        if (l.mode == Mode.BIN || l.mode == Mode.CUP) {
            RectF cup = targetRect(l, tx, ty);
            return x > cup.left && x < cup.right && y > cup.top && y < cup.bottom && vy > -180;
        }
        return distance(x, y, tx, ty) < radius + targetRadius(l);
    }

    private void shoot() {
        float dx = launchX - dragX, dy = launchY - dragY;
        float pull = (float) Math.sqrt(dx * dx + dy * dy);
        if (pull < MIN_PULL) { dragging = false; return; }
        float max = maxPull();
        if (pull > max) { dx = dx / pull * max; dy = dy / pull * max; }
        float mult = speedMult(level().obj);
        attempts++;
        x = launchX; y = launchY;
        vx = dx * POWER * mult;
        vy = dy * POWER * mult;
        scene = Scene.FLY;
        dragging = false;
        stopTimer = 0;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (w <= 0 || h <= 0) return true;
        float px = e.getX(), py = e.getY();
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            if (scene == Scene.MENU) { scene = Scene.AIM; reset(true); return true; }
            if (scene == Scene.WIN) { next(); return true; }
            if (scene == Scene.FAIL) { reset(false); return true; }
            if (scene == Scene.DONE) { levelIndex = 0; attempts = 0; scene = Scene.MENU; reset(true); return true; }
            if (scene == Scene.FLY) return true;
            dragging = true; dragX = px; dragY = py; clampDrag(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE && dragging) { dragX = px; dragY = py; clampDrag(); return true; }
        if ((e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) && dragging) { shoot(); return true; }
        return true;
    }

    private void next() {
        if (levelIndex < levels.size() - 1) { levelIndex++; attempts = 0; reset(false); }
        else scene = Scene.DONE;
    }

    private void clampDrag() {
        float dx = dragX - launchX, dy = dragY - launchY;
        float d = (float) Math.sqrt(dx * dx + dy * dy), max = maxPull();
        if (d > max) { dragX = launchX + dx / d * max; dragY = launchY + dy / d * max; }
    }

    private float maxPull() { return Math.max(330f, Math.min(w * 0.36f, h * 0.58f)); }

    private void drawFrame() {
        Canvas c = null;
        try {
            c = holder.lockCanvas();
            if (c != null) drawGame(c);
        } finally { if (c != null) holder.unlockCanvasAndPost(c); }
    }

    private void drawGame(Canvas c) {
        float sx = shakeTimer > 0 ? (float) Math.sin(time * 80) * 8f * shakeTimer : 0;
        float sy = shakeTimer > 0 ? (float) Math.cos(time * 70) * 6f * shakeTimer : 0;
        c.save(); c.translate(sx, sy);
        background(c);
        if (scene != Scene.MENU) {
            target(c, level());
            obstacles(c, level());
            launcher(c);
            if (scene == Scene.AIM && dragging) aim(c);
            projectile(c, level().obj);
            if (fxTimer > 0) effect(c, level());
        }
        c.restore();
        hud(c);
        overlay(c);
    }

    private void background(Canvas c) {
        if (scene == Scene.MENU) {
            c.drawColor(Color.rgb(17, 20, 29));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(35, 42, 62)); c.drawCircle(w * 0.18f, h * 0.20f, 90, paint);
            paint.setColor(Color.rgb(45, 56, 78)); c.drawCircle(w * 0.82f, h * 0.30f, 120, paint);
            paint.setColor(Color.rgb(55, 66, 88)); c.drawCircle(w * 0.50f, h * 0.76f, 180, paint);
            return;
        }
        Level l = level();
        if (l.theme == Theme.OFFICE) {
            c.drawColor(Color.rgb(36, 40, 49)); floor(c, Color.rgb(68,55,45));
            paint.setColor(Color.rgb(87,62,42)); c.drawRoundRect(new RectF(w*.26f, groundY-85, w*.83f, groundY-20), 14, 14, paint);
        } else if (l.theme == Theme.STREET) {
            c.drawColor(Color.rgb(42, 48, 60)); floor(c, Color.rgb(38,43,48));
            paint.setColor(Color.rgb(20,24,31)); c.drawRect(w*.10f, h*.18f, w*.90f, groundY, paint);
        } else if (l.theme == Theme.TABLE) {
            c.drawColor(Color.rgb(32,34,43)); floor(c, Color.rgb(100,66,42));
            paint.setColor(Color.rgb(130,84,48)); c.drawRect(0, groundY-80, w, groundY-56, paint);
        } else {
            c.drawColor(Color.rgb(24,29,39)); floor(c, Color.rgb(48,60,49));
        }
    }

    private void floor(Canvas c, int color) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(color); c.drawRect(0, groundY, w, h, paint);
        paint.setColor(Color.rgb(42,48,54)); c.drawRect(0, groundY-4, w, groundY+4, paint);
        paint.setStrokeWidth(2); paint.setColor(Color.argb(45,255,255,255));
        for (int i=0;i<6;i++) c.drawLine(0, groundY+20+i*34, w, groundY+20+i*34, paint);
    }

    private void target(Canvas c, Level l) {
        float tx = targetX(l), ty = l.targetY * h;
        if (l.mode == Mode.TARGET) bullseye(c, tx, ty, targetRadius(l));
        else if (l.mode == Mode.BIN) bin(c, targetRect(l, tx, ty));
        else if (l.mode == Mode.LAMP) lamp(c, tx, ty);
        else cup(c, targetRect(l, tx, ty));
    }

    private void bullseye(Canvas c, float tx, float ty, float r) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(240,240,240)); c.drawCircle(tx,ty,r,paint);
        paint.setColor(Color.rgb(221,54,54)); c.drawCircle(tx,ty,r*.72f,paint);
        paint.setColor(Color.WHITE); c.drawCircle(tx,ty,r*.45f,paint);
        paint.setColor(Color.rgb(36,125,224)); c.drawCircle(tx,ty,r*.20f,paint);
    }

    private void bin(Canvas c, RectF r) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(8); paint.setColor(Color.rgb(100,210,160));
        c.drawLine(r.left,r.top,r.right,r.top,paint); paint.setColor(Color.rgb(75,105,95));
        c.drawLine(r.left,r.top,r.left+r.width()*.18f,r.bottom,paint); c.drawLine(r.right,r.top,r.right-r.width()*.18f,r.bottom,paint);
        c.drawLine(r.left+r.width()*.18f,r.bottom,r.right-r.width()*.18f,r.bottom,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(70,100,210,160)); c.drawRect(r,paint);
    }

    private void cup(Canvas c, RectF r) {
        Path p = new Path(); p.moveTo(r.left,r.top); p.lineTo(r.right,r.top); p.lineTo(r.right-r.width()*.22f,r.bottom); p.lineTo(r.left+r.width()*.22f,r.bottom); p.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(80,80,170,240)); c.drawPath(p,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(7); paint.setColor(Color.rgb(120,210,255)); c.drawPath(p,paint);
    }

    private void lamp(Canvas c, float tx, float ty) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5); paint.setColor(Color.rgb(120,120,130)); c.drawLine(tx,0,tx,ty-34,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(55,255,224,95)); c.drawCircle(tx,ty,targetRadius(level())*2.2f,paint);
        paint.setColor(Color.rgb(255,224,95)); c.drawCircle(tx,ty,targetRadius(level()),paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(Color.rgb(80,80,80)); c.drawCircle(tx,ty,targetRadius(level()),paint);
    }

    private void obstacles(Canvas c, Level l) {
        for (Obstacle o : l.obstacles) {
            RectF r = o.rect(w,h); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(88,92,105)); c.drawRoundRect(r,10,10,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.rgb(135,140,155)); c.drawRoundRect(r,10,10,paint);
        }
    }

    private void launcher(Canvas c) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(90,0,0,0)); c.drawOval(new RectF(launchX-56,launchY+28,launchX+56,launchY+55),paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(8); paint.setColor(Color.rgb(160,110,70)); c.drawCircle(launchX,launchY,38,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(215,165,95)); c.drawCircle(launchX,launchY,13,paint);
    }

    private void aim(Canvas c) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(6); paint.setColor(Color.rgb(255,205,80)); c.drawLine(launchX,launchY,dragX,dragY,paint);
        float pull = distance(dragX,dragY,launchX,launchY), pct = Math.min(1f, pull/maxPull());
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.rgb(255,205,80)); c.drawRoundRect(new RectF(36,98,36+260*pct,122),10,10,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE); c.drawRoundRect(new RectF(36,98,296,122),10,10,paint);
        preview(c);
    }

    private void preview(Canvas c) {
        float dx = launchX-dragX, dy = launchY-dragY, pull = distance(dragX,dragY,launchX,launchY), max = maxPull();
        if (pull > max) { dx = dx/pull*max; dy = dy/pull*max; }
        Level l = level(); float mult = speedMult(l.obj), pvx = dx*POWER*mult, pvy = dy*POWER*mult;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(190,255,255,255));
        for (int i=0;i<42;i++) { float t=i*.065f; float px=launchX+pvx*t+.5f*l.wind*t*t; float py=launchY+pvy*t+.5f*GRAVITY*l.gravity*t*t; if (py>groundY || px<0 || px>w) break; c.drawCircle(px,py,4.5f,paint); }
    }

    private void projectile(Canvas c, Obj type) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(90,0,0,0)); c.drawOval(new RectF(x-radius*1.2f,groundY-8,x+radius*1.2f,groundY+8),paint);
        if (type == Obj.ARROW) { c.save(); c.rotate(rotation,x,y); paint.setStrokeWidth(8); paint.setColor(Color.rgb(220,180,85)); c.drawLine(x-34,y,x+36,y,paint); paint.setStyle(Paint.Style.FILL); Path head=new Path(); head.moveTo(x+48,y); head.lineTo(x+24,y-14); head.lineTo(x+24,y+14); head.close(); paint.setColor(Color.rgb(235,235,235)); c.drawPath(head,paint); c.restore(); return; }
        if (type == Obj.PAPER) { paint.setColor(Color.rgb(235,235,225)); c.drawCircle(x,y,radius,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.rgb(170,170,165)); c.drawLine(x-8,y-5,x+9,y+7,paint); c.drawLine(x-6,y+8,x+7,y-8,paint); return; }
        if (type == Obj.STONE) { paint.setColor(Color.rgb(115,118,124)); c.drawCircle(x,y,radius,paint); paint.setColor(Color.rgb(80,82,88)); c.drawCircle(x+5,y-4,radius*.35f,paint); return; }
        paint.setColor(Color.rgb(255,145,70)); c.drawCircle(x,y,radius,paint); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(4); paint.setColor(Color.rgb(120,75,45)); c.drawCircle(x,y,radius*.72f,paint);
    }

    private void effect(Canvas c, Level l) {
        if (scene != Scene.WIN) return; float tx=targetX(l), ty=l.targetY*h, r=30+(1-fxTimer)*160; int a=(int)clamp(190*fxTimer,0,190);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(8*fxTimer+2); paint.setColor(Color.argb(a,255,230,90)); c.drawCircle(tx,ty,r,paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(a,255,245,160)); for(int i=0;i<10;i++){ double ang=i*Math.PI*2/10+time; c.drawCircle(tx+(float)Math.cos(ang)*r*.75f, ty+(float)Math.sin(ang)*r*.75f, 5+5*fxTimer, paint); }
    }

    private void hud(Canvas c) {
        if (scene == Scene.MENU) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(155,0,0,0)); c.drawRoundRect(new RectF(18,16,w-18,92),20,20,paint);
        titlePaint.setTextSize(Math.max(36f,h*.052f)); titlePaint.setTextAlign(Paint.Align.LEFT); titlePaint.setColor(Color.WHITE); c.drawText("Mira Real",38,64,titlePaint);
        textPaint.setTextAlign(Paint.Align.RIGHT); textPaint.setColor(Color.rgb(225,230,240)); Level l=level(); String info=String.format(Locale.getDefault(),"Fase %d/%d | %s | Tentativas: %d",levelIndex+1,levels.size(),l.name,attempts); c.drawText(info,w-36,62,textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setColor(Color.rgb(220,225,235)); String wind=Math.abs(l.wind)<1?"sem vento":(l.wind>0?"vento para direita":"vento para esquerda"); c.drawText("Puxe, mire pela linha e solte. "+wind,36,h-28,textPaint);
    }

    private void overlay(Canvas c) {
        if (scene == Scene.AIM || scene == Scene.FLY) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(scene==Scene.MENU?215:178,0,0,0)); c.drawRect(0,0,w,h,paint);
        titlePaint.setTextAlign(Paint.Align.CENTER); titlePaint.setTextSize(Math.max(42f,h*.07f)); titlePaint.setColor(Color.WHITE); textPaint.setTextAlign(Paint.Align.CENTER); textPaint.setColor(Color.rgb(226,230,238));
        if (scene == Scene.MENU) { c.drawText("MIRA REAL",w/2f,h*.30f,titlePaint); textPaint.setTextSize(Math.max(25f,h*.038f)); c.drawText("fisica de arremesso com fases variadas",w/2f,h*.38f,textPaint); card(c,h*.50f,"Modos diferentes","flecha, papel, pedra, bola e copo"); card(c,h*.61f,"Mais estavel","versao sem 3D bugado"); card(c,h*.72f,"Toque para jogar","arraste para mirar e solte"); return; }
        String title = scene==Scene.WIN?"ACERTOU!":scene==Scene.FAIL?"QUASE!":"MVP COMPLETO";
        String sub = scene==Scene.WIN?"Estrelas: "+stars+" | toque para continuar":scene==Scene.FAIL?"Toque para tentar de novo":"Toque para voltar ao menu";
        c.drawText(title,w/2f,h*.45f,titlePaint); c.drawText(sub,w/2f,h*.53f,textPaint);
    }

    private void card(Canvas c, float cy, String a, String b) {
        RectF r=new RectF(w*.12f,cy-36,w*.88f,cy+36); paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(125,255,255,255)); c.drawRoundRect(r,18,18,paint);
        textPaint.setTextAlign(Paint.Align.LEFT); textPaint.setColor(Color.WHITE); textPaint.setFakeBoldText(true); c.drawText(a,r.left+22,cy-7,textPaint); textPaint.setFakeBoldText(false); textPaint.setColor(Color.rgb(218,222,232)); c.drawText(b,r.left+22,cy+25,textPaint); textPaint.setTextAlign(Paint.Align.CENTER);
    }

    private float targetX(Level l) { return l.targetX*w + (l.moveRange==0?0:(float)Math.sin(time*l.moveSpeed)*l.moveRange*w); }
    private RectF targetRect(Level l,float tx,float ty){ float ww=w*(l.mode==Mode.CUP?.080f:.105f), hh=h*(l.mode==Mode.CUP?.15f:.18f); return new RectF(tx-ww/2,ty-hh/2,tx+ww/2,ty+hh/2); }
    private float targetRadius(Level l){ return l.mode==Mode.LAMP?Math.max(24f,h*.047f):Math.max(38f,h*.078f); }
    private float objRadius(Obj o){ return o==Obj.ARROW?14:o==Obj.PAPER?20:o==Obj.STONE?18:19; }
    private float speedMult(Obj o){ return o==Obj.ARROW?1.08f:o==Obj.PAPER?.92f:o==Obj.STONE?1f:.98f; }
    private boolean circleRect(float cx,float cy,float rr,RectF r){ float nx=clamp(cx,r.left,r.right), ny=clamp(cy,r.top,r.bottom), dx=cx-nx, dy=cy-ny; return dx*dx+dy*dy<=rr*rr; }
    private float distance(float ax,float ay,float bx,float by){ float dx=ax-bx,dy=ay-by; return (float)Math.sqrt(dx*dx+dy*dy); }
    private float clamp(float v,float min,float max){ return Math.max(min,Math.min(max,v)); }

    private enum Scene { MENU, AIM, FLY, WIN, FAIL, DONE }
    private enum Mode { TARGET, BIN, LAMP, CUP }
    private enum Obj { ARROW, PAPER, STONE, BALL }
    private enum Theme { RANGE, OFFICE, STREET, TABLE }

    private static class Level {
        final String name; final Mode mode; final Obj obj; final float launchX, launchY, targetX, targetY, wind, gravity, bounce, drag; final Theme theme; float moveRange, moveSpeed; final List<Obstacle> obstacles=new ArrayList<>();
        Level(String name,Mode mode,Obj obj,float lx,float ly,float tx,float ty,float wind,float gravity,float bounce,Theme theme){ this.name=name; this.mode=mode; this.obj=obj; this.launchX=lx; this.launchY=ly; this.targetX=tx; this.targetY=ty; this.wind=wind; this.gravity=gravity; this.bounce=bounce; this.theme=theme; this.drag=obj==Obj.PAPER?.10f:.025f; }
        Level obstacle(float l,float t,float r,float b){ obstacles.add(new Obstacle(l,t,r,b)); return this; }
        Level moving(float range,float speed){ moveRange=range; moveSpeed=speed; return this; }
    }
    private static class Obstacle { final float l,t,r,b; Obstacle(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;} RectF rect(float w,float h){return new RectF(l*w,t*h,r*w,b*h);} }
}
