package xyz.etkn.tv;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;

/** Draws only outside the surface; translucent fills never reveal a caster underneath. */
final class OuterShadow extends Drawable implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener {
    private final View view;
    private View root;
    private final Matrix transform=new Matrix(),nextTransform=new Matrix(),ancestorTransform=new Matrix();
    private final RectF clip=new RectF(),nextClip=new RectF(),temporary=new RectF(),body=new RectF();
    private final Path hole=new Path();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shader[] gradients=new Shader[8];
    private final RectF[] areas=new RectF[8];
    private float elevation,strength,opacity,radius;
    private int width,height,color;
    private boolean dark,visible,dirty=true;

    static void apply(View view,float elevation,float strength,boolean dark){
        OuterShadow shadow=(OuterShadow)view.getTag(R.id.appearance_outer_shadow);
        if(shadow==null){if(elevation<=0)return;shadow=new OuterShadow(view);view.setTag(R.id.appearance_outer_shadow,shadow);}
        shadow.elevation=elevation;shadow.strength=strength;shadow.dark=dark;shadow.dirty=true;shadow.sync();
    }
    private OuterShadow(View view){this.view=view;for(int i=0;i<areas.length;i++)areas[i]=new RectF();view.addOnAttachStateChangeListener(this);if(view.isAttachedToWindow())attach();}
    private void attach(){root=view.getRootView();root.getOverlay().add(this);root.getViewTreeObserver().addOnPreDrawListener(this);sync();}
    @Override public void onViewAttachedToWindow(View view){attach();}
    @Override public void onViewDetachedFromWindow(View view){if(root!=null){root.getOverlay().remove(this);if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnPreDrawListener(this);root=null;}}
    @Override public boolean onPreDraw(){sync();return true;}
    private void matrixToRoot(View target,Matrix matrix){
        matrix.reset();for(View current=target;current!=root&&current.getParent() instanceof View;){matrix.postConcat(current.getMatrix());View parent=(View)current.getParent();matrix.postTranslate(current.getLeft()-parent.getScrollX(),current.getTop()-parent.getScrollY());current=parent;}
    }
    private void sync(){
        if(root==null)return;
        boolean showing=elevation>0&&strength>0&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0;
        float alpha=1;nextClip.set(0,0,root.getWidth(),root.getHeight());matrixToRoot(view,nextTransform);
        for(View current=view;current!=root&&current.getParent() instanceof View;){
            alpha*=current.getAlpha();View parent=(View)current.getParent();
            if(parent instanceof ViewGroup&&((ViewGroup)parent).getClipChildren()){
                ViewGroup group=(ViewGroup)parent;boolean padding=group.getClipToPadding();temporary.set(padding?group.getPaddingLeft():0,padding?group.getPaddingTop():0,group.getWidth()-(padding?group.getPaddingRight():0),group.getHeight()-(padding?group.getPaddingBottom():0));matrixToRoot(group,ancestorTransform);ancestorTransform.mapRect(temporary);if(!nextClip.intersect(temporary))nextClip.setEmpty();
            }current=parent;
        }
        temporary.set(0,0,view.getWidth(),view.getHeight());nextTransform.mapRect(temporary);temporary.inset(-elevation*3,-elevation*3);showing&=alpha>0&&RectF.intersects(temporary,nextClip);
        boolean changed=dirty||showing!=visible||alpha!=opacity||width!=view.getWidth()||height!=view.getHeight()||!transform.equals(nextTransform)||!clip.equals(nextClip);
        if(!changed)return;
        visible=showing;opacity=alpha;transform.set(nextTransform);clip.set(nextClip);width=view.getWidth();height=view.getHeight();setBounds(0,0,root.getWidth(),root.getHeight());
        if(dirty||body.width()!=width||body.height()!=height)build();dirty=false;invalidateSelf();
    }
    private void build(){
        radius=Math.min(12*view.getResources().getDisplayMetrics().density,Math.min(width,height)/2f);
        float spread=Math.max(1,elevation*1.25f),drop=elevation*.6f,reach=radius+spread;
        color=(TvAppearance.shadowColor(dark)&0xffffff)|(Math.round((dark?.28f:.20f)*strength*255)<<24);
        body.set(0,drop,width,height+drop);hole.reset();hole.addRoundRect(new RectF(0,0,width,height),radius,radius,Path.Direction.CW);
        float[] x={radius,width-radius,width-radius,radius},y={drop+radius,drop+radius,height+drop-radius,height+drop-radius};
        areas[0].set(-spread,drop-spread,radius,drop+radius);areas[1].set(width-radius,drop-spread,width+spread,drop+radius);
        areas[2].set(width-radius,height+drop-radius,width+spread,height+drop+spread);areas[3].set(-spread,height+drop-radius,radius,height+drop+spread);
        for(int i=0;i<4;i++)gradients[i]=new RadialGradient(x[i],y[i],reach,new int[]{color,color,color&0xffffff},new float[]{0,radius/reach,1},Shader.TileMode.CLAMP);
        areas[4].set(radius,drop-spread,width-radius,drop);gradients[4]=new LinearGradient(0,drop-spread,0,drop,color&0xffffff,color,Shader.TileMode.CLAMP);
        areas[5].set(width,drop+radius,width+spread,height+drop-radius);gradients[5]=new LinearGradient(width,0,width+spread,0,color,color&0xffffff,Shader.TileMode.CLAMP);
        areas[6].set(radius,height+drop,width-radius,height+drop+spread);gradients[6]=new LinearGradient(0,height+drop,0,height+drop+spread,color,color&0xffffff,Shader.TileMode.CLAMP);
        areas[7].set(-spread,drop+radius,0,height+drop-radius);gradients[7]=new LinearGradient(-spread,0,0,0,color&0xffffff,color,Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas canvas){
        if(!visible)return;int saved=canvas.save();canvas.clipRect(clip);canvas.concat(transform);canvas.clipPath(hole,Region.Op.DIFFERENCE);
        paint.setShader(null);paint.setColor(color);paint.setAlpha(Math.round(Color.alpha(color)*opacity));canvas.drawRect(radius,body.top,width-radius,body.bottom,paint);canvas.drawRect(0,body.top+radius,radius,body.bottom-radius,paint);canvas.drawRect(width-radius,body.top+radius,width,body.bottom-radius,paint);
        paint.setAlpha(Math.round(255*opacity));for(int i=0;i<areas.length;i++){paint.setShader(gradients[i]);canvas.drawRect(areas[i],paint);}canvas.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){}
    @Override public void setColorFilter(ColorFilter filter){}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
