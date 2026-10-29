/*
 * PGS palette conversion and Cue layout adapted from AndroidX Media3 PgsParser.
 * Copyright (C) 2018 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.etkn.tv;

import android.graphics.Bitmap;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.Util;
import androidx.media3.extractor.text.CuesWithTiming;
import androidx.media3.extractor.text.SubtitleParser;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** PGS display sets may compose several independently positioned and cropped objects. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class TvPgsParser implements SubtitleParser {
    private static final int MAX_BYTES=16*1024*1024,MAX_PIXELS=3840*2160;
    private final Map<Integer,ObjectData> objects=new HashMap<>();
    private final Map<Integer,int[]> palettes=new HashMap<>();
    private final List<Placement> placements=new ArrayList<>();
    private int width,height,paletteId,cachedBytes;
    private static final class ObjectData {
        final int width,height;final byte[] data;int received;
        ObjectData(int width,int height,int length){this.width=width;this.height=height;data=new byte[length];}
    }
    private static final class Placement {
        int id,x,y,cropX,cropY,cropWidth,cropHeight;
    }
    @Override public int getCueReplacementBehavior(){return Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE;}
    @Override public void reset(){objects.clear();palettes.clear();placements.clear();width=height=paletteId=cachedBytes=0;}
    @Override public void parse(byte[] data,int offset,int length,OutputOptions options,Consumer<CuesWithTiming> output){
        List<Cue> cues=new ArrayList<>();
        if(length>MAX_BYTES){output.accept(new CuesWithTiming(cues,C.TIME_UNSET,C.TIME_UNSET));return;}
        if(length>0&&(data[offset]&255)==0x78){
            byte[] inflated=inflate(data,offset,length);
            if(inflated==null){output.accept(new CuesWithTiming(cues,C.TIME_UNSET,C.TIME_UNSET));return;}
            data=inflated;offset=0;length=data.length;
        }
        ParsableByteArray input=new ParsableByteArray(data,offset+length);input.setPosition(offset);
        boolean sup=length>=2&&data[offset]=='P'&&data[offset+1]=='G';
        List<Cue> previous=null;long cueTimeUs=0,previousTimeUs=0;
        while(input.bytesLeft()>=(sup?13:3)){
            long timeUs=0;
            if(sup){if(input.readUnsignedShort()!=0x5047)break;timeUs=input.readUnsignedInt()*1000000L/90000;input.skipBytes(4);}
            int type=input.readUnsignedByte(),size=input.readUnsignedShort(),end=input.getPosition()+size;
            if(end>input.limit())break;
            int limit=input.limit();input.setLimit(end);
            switch(type){
                case 0x14: palette(input);break;
                case 0x15: object(input);break;
                case 0x16: composition(input);cueTimeUs=timeUs;break;
                case 0x80:
                    cues=buildCues();
                    if(sup){
                        if(previous!=null)output.accept(new CuesWithTiming(previous,previousTimeUs,Math.max(0,cueTimeUs-previousTimeUs)));
                        previous=cues;previousTimeUs=cueTimeUs;
                    }
                    break;
                default: break;
            }
            input.setLimit(limit);input.setPosition(end);
        }
        if(sup){if(previous!=null)output.accept(new CuesWithTiming(previous,previousTimeUs,C.TIME_UNSET));}
        else output.accept(new CuesWithTiming(cues,C.TIME_UNSET,C.TIME_UNSET));
    }
    private static byte[] inflate(byte[] data,int offset,int length){
        Inflater inflater=new Inflater();inflater.setInput(data,offset,length);
        try{
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[8192];
            while(!inflater.finished()){
                int count=inflater.inflate(chunk);
                if(count==0||out.size()>MAX_BYTES-count)return null;
                out.write(chunk,0,count);
            }
            return out.toByteArray();
        }catch(DataFormatException invalid){return null;}finally{inflater.end();}
    }
    private void palette(ParsableByteArray input){
        if(input.bytesLeft()<2||input.bytesLeft()%5!=2)return;
        int id=input.readUnsignedByte();input.skipBytes(1);
        int[] colors=palettes.get(id);if(colors==null){colors=new int[256];palettes.put(id,colors);}
        while(input.bytesLeft()>=5){
            int index=input.readUnsignedByte(),y=input.readUnsignedByte(),cr=input.readUnsignedByte()-128,cb=input.readUnsignedByte()-128,alpha=input.readUnsignedByte();
            colors[index]=alpha<<24|Util.constrainValue((int)(y+1.402*cr),0,255)<<16
                |Util.constrainValue((int)(y-0.34414*cb-0.71414*cr),0,255)<<8|Util.constrainValue((int)(y+1.772*cb),0,255);
        }
    }
    private void object(ParsableByteArray input){
        if(input.bytesLeft()<4)return;
        int id=input.readUnsignedShort();input.skipBytes(1);boolean first=(input.readUnsignedByte()&0x80)!=0;
        ObjectData object=objects.get(id);
        if(first){
            if(input.bytesLeft()<7)return;
            int length=input.readUnsignedInt24()-4,w=input.readUnsignedShort(),h=input.readUnsignedShort();
            if(object!=null){cachedBytes-=object.data.length;objects.remove(id);object=null;}
            if(length<=0||length>MAX_BYTES-cachedBytes||w==0||h==0||(long)w*h>MAX_PIXELS||objects.size()>=64)return;
            object=new ObjectData(w,h,length);objects.put(id,object);cachedBytes+=length;
        }
        if(object==null)return;
        int count=Math.min(input.bytesLeft(),object.data.length-object.received);
        input.readBytes(object.data,object.received,count);object.received+=count;
    }
    private void composition(ParsableByteArray input){
        placements.clear();
        if(input.bytesLeft()<11){width=height=0;return;}
        width=input.readUnsignedShort();height=input.readUnsignedShort();input.skipBytes(3);
        int state=input.readUnsignedByte();input.skipBytes(1);paletteId=input.readUnsignedByte();int count=input.readUnsignedByte();
        if(state!=0){objects.clear();palettes.clear();cachedBytes=0;}
        for(int i=0;i<count&&input.bytesLeft()>=8;i++){
            Placement placement=new Placement();placement.id=input.readUnsignedShort();input.skipBytes(1);boolean crop=(input.readUnsignedByte()&0x80)!=0;
            placement.x=input.readUnsignedShort();placement.y=input.readUnsignedShort();
            if(crop){if(input.bytesLeft()<8)return;placement.cropX=input.readUnsignedShort();placement.cropY=input.readUnsignedShort();placement.cropWidth=input.readUnsignedShort();placement.cropHeight=input.readUnsignedShort();}
            placements.add(placement);
        }
    }
    private List<Cue> buildCues(){
        List<Cue> cues=new ArrayList<>();int[] colors=palettes.get(paletteId);int budget=MAX_PIXELS;
        if(width==0||height==0||colors==null)return cues;
        for(Placement placement:placements){
            ObjectData object=objects.get(placement.id);
            if(object==null||object.received!=object.data.length||(long)object.width*object.height>budget)continue;
            budget-=object.width*object.height;
            int[] pixels=decode(object,colors);if(pixels==null)continue;
            int x=placement.cropX,y=placement.cropY,w=placement.cropWidth>0?placement.cropWidth:object.width,h=placement.cropHeight>0?placement.cropHeight:object.height;
            if(x+w>object.width||y+h>object.height||placement.x+x>=width||placement.y+y>=height)continue;
            Bitmap bitmap=Bitmap.createBitmap(pixels,y*object.width+x,object.width,w,h,Bitmap.Config.ARGB_8888);
            cues.add(new Cue.Builder().setBitmap(bitmap).setPosition((float)(placement.x+x)/width).setPositionAnchor(Cue.ANCHOR_TYPE_START)
                .setLine((float)(placement.y+y)/height,Cue.LINE_TYPE_FRACTION).setLineAnchor(Cue.ANCHOR_TYPE_START)
                .setSize((float)w/width).setBitmapHeight((float)h/height).build());
        }
        return cues;
    }
    private static int[] decode(ObjectData object,int[] colors){
        int[] pixels=new int[object.width*object.height];byte[] data=object.data;int position=0,pixel=0,lineEnd=object.width;
        while(pixel<pixels.length&&position<data.length){
            int color=data[position++]&255;
            if(color!=0){if(pixel>=lineEnd)return null;pixels[pixel++]=colors[color];continue;}
            if(position>=data.length)return null;
            int control=data[position++]&255;
            if(control==0){pixel=lineEnd;lineEnd=Math.min(pixels.length,lineEnd+object.width);continue;}
            int count=control&63;
            if((control&64)!=0){if(position>=data.length)return null;count=count<<8|(data[position++]&255);}
            color=0;
            if((control&128)!=0){if(position>=data.length)return null;color=data[position++]&255;}
            if(count>lineEnd-pixel)return null;
            Arrays.fill(pixels,pixel,pixel+count,colors[color]);pixel+=count;
        }
        return pixel==pixels.length?pixels:null;
    }
}
