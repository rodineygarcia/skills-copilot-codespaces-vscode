package com.asciiaventura.game;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.Locale;

public final class MainActivity extends Activity {
    Game game;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        game = new Game(this);
        setContentView(game);
    }
    @Override protected void onPause() {
        if (game != null) game.save();
        super.onPause();
    }

    static final class Game extends View {
        static final int W=40,H=24;
        static final int BG=Color.rgb(8,14,24), GREEN=Color.rgb(90,232,174);
        static final int GOLD=Color.rgb(247,214,115), RED=Color.rgb(255,102,116);
        final Paint p=new Paint(3);
        final SharedPreferences prefs;
        final char[][] map=new char[H][W];
        final int[][] stars={{5,5},{20,5},{32,6},{6,13},{19,19},{34,20}};
        final int[][] mobs={{7,4},{18,10},{30,17}};
        final int[] mobHP={2,2,2};
        final RectF[] buttons=new RectF[7];
        final String[] labels={"^","v","<",">","ATK","2D/3D","NOVO"};
        final boolean[] down=new boolean[4];
        float x=3.5f,y=3.5f,angle=0f;
        boolean firstPerson=false,won=false,lost=false;
        int hp=100, collected=0,kills=0;
        long lastTime=0,hitTime=0;
        float scale,cell,baseX,baseY;
        int cols=90,rows=20;
        String msg="Colete 6 estrelas * e encontre a saida E";

        Game(Context c) {
            super(c);
            prefs=c.getSharedPreferences("asciigame",0);
            setFocusable(true);
            setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            createMap();
            restore();
        }
        void createMap() {
            for(int j=0;j<H;j++) for(int i=0;i<W;i++)
                map[j][i]=j==0||i==0||j==H-1||i==W-1?'#':'.';
            for(int i=2;i<W-2;i++) for(int j:new int[]{7,15})
                if(i!=9&&i!=22&&i!=32)map[j][i]='#';
            for(int j=2;j<H-2;j++) for(int i:new int[]{12,25})
                if(j!=5&&j!=13&&j!=19)map[j][i]='#';
            for(int j=2;j<H-2;j++)for(int i=2;i<W-2;i++)
                if(map[j][i]=='.'&&((i*13+j*19)%89==0))map[j][i]='T';
            for(int[] s:stars)map[s[1]][s[0]]='*';
            map[21][37]='E';map[3][3]='.';
        }
        boolean pass(float xx,float yy) {
            int tx=(int)xx,ty=(int)yy;
            return tx>=0&&ty>=0&&tx<W&&ty<H&&map[ty][tx]!='#'&&map[ty][tx]!='T';
        }
        void restore() {
            x=prefs.getFloat("x",3.5f);y=prefs.getFloat("y",3.5f);
            angle=prefs.getFloat("angle",0);hp=prefs.getInt("hp",100);
            firstPerson=prefs.getBoolean("3d",false);
            collected=prefs.getInt("stars",0);kills=prefs.getInt("kills",0);
            won=prefs.getBoolean("won",false);lost=prefs.getBoolean("lost",false);
            if(!Float.isFinite(x)||!Float.isFinite(y)||!pass(x,y)) { x=3.5f;y=3.5f; }
            for(int i=0;i<stars.length;i++)
                if(prefs.getBoolean("star"+i,false))map[stars[i][1]][stars[i][0]]='.';
            for(int i=0;i<mobHP.length;i++)mobHP[i]=prefs.getInt("mob"+i,2);
        }
        void save() {
            SharedPreferences.Editor e=prefs.edit();
            e.putFloat("x",x).putFloat("y",y).putFloat("angle",angle);
            e.putInt("hp",hp).putInt("stars",collected).putInt("kills",kills);
            e.putBoolean("3d",firstPerson).putBoolean("won",won).putBoolean("lost",lost);
            for(int i=0;i<stars.length;i++)
                e.putBoolean("star"+i,map[stars[i][1]][stars[i][0]]!='*');
            for(int i=0;i<mobHP.length;i++)e.putInt("mob"+i,mobHP[i]);
            e.apply();
        }
        void reset() {
            prefs.edit().clear().apply();
            x=3.5f;y=3.5f;angle=0;hp=100;collected=0;kills=0;won=false;lost=false;
            firstPerson=false;msg="Nova aventura!";lastTime=0;
            for(int i=0;i<3;i++)mobHP[i]=2;
            createMap();invalidate();
        }
        void drawText(Canvas c,String t,float xx,float yy,float sz,int color) {
            p.setStyle(Paint.Style.FILL);p.setTextSize(sz);p.setColor(color);
            c.drawText(t,xx,yy,p);
        }
        @Override protected void onDraw(Canvas c) {
            long now=System.nanoTime();float dt=lastTime==0?0:Math.min(.06f,(now-lastTime)/1e9f);
            lastTime=now;update(dt);
            c.drawColor(BG);
            float w=getWidth(),h=getHeight();
            scale=Math.max(1,getResources().getDisplayMetrics().density);
            float controlH=Math.min(150*scale,h*.40f);
            float usableH=h-controlH-56*scale;
            cell=Math.max(7,Math.min(13*scale,usableH/24f));
            rows=Math.max(9,Math.min(26,(int)(usableH/cell)));
            cols=Math.max(22,Math.min(145,(int)((w-20*scale)/(cell*.61f))));
            baseX=(w-cols*cell*.61f)/2f;baseY=52*scale;
            p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));
            p.setTextAlign(Paint.Align.LEFT);
            drawText(c,"ASCII // AVENTURA",13*scale,19*scale,15*scale,GREEN);
            drawText(c,String.format(Locale.US,"HP %d  * %d/6  KO %d  [%s]",hp,collected,kills,firstPerson?"3D":"2D"),
                13*scale,38*scale,11*scale,Color.WHITE);
            if(firstPerson)draw3D(c); else draw2D(c);
            drawButtons(c,w,h,controlH);
            if(won||lost) {
                p.setColor(Color.argb(235,10,25,40));
                c.drawRoundRect(new RectF(w*.22f,h*.23f,w*.78f,h*.57f),14,14,p);
                p.setTextAlign(Paint.Align.CENTER);
                drawText(c,won?"VITORIA!":"FIM DE JOGO",w/2,h*.39f,24*scale,won?GREEN:RED);
                drawText(c,"Toque NOVO para reiniciar",w/2,h*.48f,11*scale,Color.WHITE);
            }
            postInvalidateDelayed(33);
        }
        void glyph(Canvas c,char ch,int col,int row,int color) {
            if(ch==' ')return;
            drawText(c,String.valueOf(ch),baseX+col*cell*.61f,baseY+(row+1)*cell,cell,color);
        }
        void draw2D(Canvas c) {
            int ox=(int)x-cols/2,oy=(int)y-rows/2;
            for(int row=0;row<rows;row++)for(int col=0;col<cols;col++) {
                int xx=ox+col,yy=oy+row;
                char ch=xx<0||xx>=W||yy<0||yy>=H?'#':map[yy][xx];
                int color=ch=='#'?Color.rgb(125,170,192):ch=='T'?GREEN:
                    ch=='*'?GOLD:ch=='E'?GREEN:Color.rgb(65,101,95);
                if(ch=='.') ch=(xx+yy)%5==0?'.':' ';
                glyph(c,ch,col,row,color);
            }
            for(int i=0;i<mobs.length;i++) if(mobHP[i]>0)
                glyph(c,'g',mobs[i][0]-ox,mobs[i][1]-oy,RED);
            glyph(c,'@',cols/2,rows/2,GOLD);
        }
        void draw3D(Canvas c) {
            float[] distances=new float[cols];
            for(int col=0;col<cols;col++) {
                float ray=angle+(float)Math.atan((col-cols/2f)/cols*1.3f);
                float dist=.04f;float rx,ry;
                while(dist<22) {
                    rx=x+(float)Math.cos(ray)*dist;ry=y+(float)Math.sin(ray)*dist;
                    if(!pass(rx,ry))break;dist+=.05f;
                }
                dist=Math.max(.1f,dist*(float)Math.cos(ray-angle));
                distances[col]=dist;
                int wallH=(int)(rows/Math.max(.1,dist));
                int top=rows/2-wallH/2,bot=rows/2+wallH/2;
                for(int row=0;row<rows;row++) {
                    char ch;int color;
                    if(row<top) {ch=row%4==0?'.':' ';color=Color.rgb(70,104,131);}
                    else if(row<=bot) {
                        ch=dist<2?'#':dist<4?'X':dist<7?'+':':';
                        int lum=Math.max(45,(int)(210/(1+dist*.1)));
                        color=Color.rgb((int)(lum*.67), (int)(lum*.85),lum);
                    } else {ch=row%3==0?':':'.';color=Color.rgb(57,100,80);}
                    glyph(c,ch,col,row,color);
                }
            }
            for(int i=0;i<mobs.length;i++)if(mobHP[i]>0) {
                float dx=mobs[i][0]+.5f-x,dy=mobs[i][1]+.5f-y;
                float distance=(float)Math.hypot(dx,dy);
                float da=(float)Math.atan2(Math.sin(Math.atan2(dy,dx)-angle),Math.cos(Math.atan2(dy,dx)-angle));
                int col=(int)(cols/2f+Math.tan(da)*cols/1.3f);
                if(distance<12&&col>=0&&col<cols&&distance<distances[col]) {
                    int sz=Math.min(rows,Math.max(1,(int)(rows/distance*.8f)));
                    for(int py=-sz/2;py<=sz/2;py++)
                        for(int px=-Math.max(0,sz/5);px<=Math.max(0,sz/5);px++)
                            glyph(c,'M',col+px,rows/2+py,RED);
                }
            }
            glyph(c,'+',cols/2,rows/2,GOLD);
            glyph(c,'/',cols/2-1,rows-2,GOLD);
            glyph(c,'|',cols/2,rows-2,GOLD);
            glyph(c,'\\',cols/2+1,rows-2,GOLD);
        }
        void drawButtons(Canvas c,float w,float h,float bh) {
            float size=Math.min(bh/3f,w/10.5f),left=10*scale,cy=h-10*scale;
            buttons[0]=new RectF(left+size,cy-3*size,left+2*size,cy-2*size);
            buttons[1]=new RectF(left+size,cy-size,left+2*size,cy);
            buttons[2]=new RectF(left,cy-2*size,left+size,cy-size);
            buttons[3]=new RectF(left+2*size,cy-2*size,left+3*size,cy-size);
            buttons[4]=new RectF(w-2.15f*size,cy-2*size,w-10*scale,cy);
            buttons[5]=new RectF(w-4.4f*size,cy-size,w-2.3f*size,cy);
            buttons[6]=new RectF(w-4.4f*size,cy-2*size,w-2.3f*size,cy-size);
            for(int i=0;i<7;i++) {
                RectF r=buttons[i];p.setColor(i<4&&down[i]?Color.rgb(44,108,118):Color.rgb(26,49,65));
                c.drawRoundRect(r,9*scale,9*scale,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f*scale);p.setColor(Color.rgb(62,119,133));
                c.drawRoundRect(r,9*scale,9*scale,p);p.setStyle(Paint.Style.FILL);
                p.setTextAlign(Paint.Align.CENTER);
                drawText(c,labels[i],r.centerX(),r.centerY()+5*scale,
                    (i>3?11:19)*scale,i==4?RED:i==5?GREEN:Color.WHITE);
            }
            p.setTextAlign(Paint.Align.CENTER);
            drawText(c,msg,w/2f,h-10*scale,Math.min(10*scale,w/72f),Color.rgb(126,163,172));
        }
        void update(float dt) {
            if(won||lost||dt<=0)return;
            float vx=0,vy=0,vel=3;
            if(firstPerson) {
                angle+=(down[3]?1:0)*dt*2.3f-(down[2]?1:0)*dt*2.3f;
                float forward=(down[0]?1:0)-(down[1]?1:0);
                vx=(float)Math.cos(angle)*forward;vy=(float)Math.sin(angle)*forward;
            } else {
                vx=(down[3]?1:0)-(down[2]?1:0);
                vy=(down[1]?1:0)-(down[0]?1:0);
                float norm=(float)Math.hypot(vx,vy);
                if(norm>0) {vx/=norm;vy/=norm;angle=(float)Math.atan2(vy,vx);}
            }
            float nx=x+vx*dt*vel,ny=y+vy*dt*vel;
            if(pass(nx+.17f, y+.17f)&&pass(nx-.17f,y-.17f))x=nx;
            if(pass(x+.17f,ny+.17f)&&pass(x-.17f,ny-.17f))y=ny;
            int tx=(int)x,ty=(int)y;
            if(map[ty][tx]=='*') {
                map[ty][tx]='.';collected++;msg="Estrela coletada! "+collected+"/6";
            }
            if(map[ty][tx]=='E') {
                if(collected==6){won=true;msg="Voce venceu!";}
                else msg="Faltam "+(6-collected)+" estrelas";
            }
            if(System.currentTimeMillis()-hitTime>1500) {
                for(int i=0;i<mobs.length;i++)if(mobHP[i]>0 &&
                    Math.hypot(x-mobs[i][0]-.5f,y-mobs[i][1]-.5f)<1.1) {
                    hp-=10;hitTime=System.currentTimeMillis();msg="Monstro atacou!";
                    if(hp<=0){lost=true;msg="Fim de jogo";}
                    break;
                }
            }
        }
        void attack() {
            if(won||lost)return;
            for(int i=0;i<mobs.length;i++) if(mobHP[i]>0) {
                float dx=mobs[i][0]+.5f-x,dy=mobs[i][1]+.5f-y;
                float distance=(float)Math.hypot(dx,dy);
                float da=(float)Math.atan2(Math.sin(Math.atan2(dy,dx)-angle),Math.cos(Math.atan2(dy,dx)-angle));
                if(distance<2f&&(!firstPerson||Math.abs(da)<.8f)) {
                    mobHP[i]--;msg="Acertou!";if(mobHP[i]==0){kills++;msg="Monstro derrotado!";}
                    invalidate();return;
                }
            }
            msg="Ataque no vazio";
        }
        void sync(MotionEvent e,int skip) {
            for(int j=0;j<4;j++)down[j]=false;
            for(int n=0;n<e.getPointerCount();n++)if(n!=skip)for(int i=0;i<4;i++)
                if(buttons[i]!=null&&buttons[i].contains(e.getX(n),e.getY(n)))down[i]=true;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            int act=e.getActionMasked(),idx=e.getActionIndex();
            if(act==MotionEvent.ACTION_DOWN||act==MotionEvent.ACTION_POINTER_DOWN) {
                for(int i=4;i<7;i++) if(buttons[i]!=null&&buttons[i].contains(e.getX(idx),e.getY(idx))) {
                    if(i==4)attack();if(i==5)firstPerson=!firstPerson;if(i==6)reset();
                }
                sync(e,-1);performClick();
            } else if(act==MotionEvent.ACTION_MOVE)sync(e,-1);
            else if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_POINTER_UP)sync(e,idx);
            else if(act==MotionEvent.ACTION_CANCEL)for(int i=0;i<4;i++)down[i]=false;
            invalidate();return true;
        }
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
