package xyz.etkn.tv;

import android.graphics.Bitmap;

/** Small, bounded CPU blur for API 24+ TVs. The shared source bitmap is never modified. */
final class BackdropBlur {
    static Bitmap create(Bitmap source){
        float scale=Math.min(1f,320f/Math.max(source.getWidth(),source.getHeight()));
        int width=Math.max(1,Math.round(source.getWidth()*scale)),height=Math.max(1,Math.round(source.getHeight()*scale));
        Bitmap sample=Bitmap.createScaledBitmap(source,width,height,true);
        int[] pixels=new int[width*height],scratch=new int[pixels.length];
        sample.getPixels(pixels,0,width,0,0,width,height);if(sample!=source)sample.recycle();
        for(int pass=0;pass<3;pass++){blur(pixels,scratch,width,height,true);blur(scratch,pixels,width,height,false);}
        return Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
    }
    private static void blur(int[] source,int[] target,int width,int height,boolean horizontal){
        int radius=6,window=radius*2+1,lines=horizontal?height:width,length=horizontal?width:height,step=horizontal?1:width;
        for(int line=0;line<lines;line++){
            int start=horizontal?line*width:line,a=0,r=0,g=0,b=0;
            for(int k=-radius;k<=radius;k++){int pixel=source[start+Math.max(0,Math.min(length-1,k))*step];a+=pixel>>>24;r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;}
            for(int i=0;i<length;i++){
                target[start+i*step]=(a/window)<<24|(r/window)<<16|(g/window)<<8|b/window;
                int outgoing=source[start+Math.max(0,i-radius)*step],incoming=source[start+Math.min(length-1,i+radius+1)*step];
                a+=(incoming>>>24)-(outgoing>>>24);r+=((incoming>>16)&255)-((outgoing>>16)&255);g+=((incoming>>8)&255)-((outgoing>>8)&255);b+=(incoming&255)-(outgoing&255);
            }
        }
    }
}
