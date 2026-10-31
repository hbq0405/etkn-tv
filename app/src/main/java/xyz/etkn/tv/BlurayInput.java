package xyz.etkn.tv;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.*;

/** Bounded random access to the original ISO. Redirect targets never receive ETKN headers. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class BlurayInput implements AutoCloseable {
    private static final int CHUNK=1024*1024, CACHE_CHUNKS=8;
    private static final OkHttpClient HTTP=new OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).build();
    private static final Pattern CONTENT_RANGE=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)");
    private final DataSource.Factory factory;
    private final Uri uri;
    private final Map<Long,byte[]> cache=new LinkedHashMap<Long,byte[]>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<Long,byte[]> entry){return size()>CACHE_CHUNKS;}
    };
    private DataSource upstream;
    private Response response;
    private java.io.InputStream network;
    private HttpUrl resolved;
    private long upstreamPosition=-1,size=C.LENGTH_UNSET;
    BlurayInput(DataSource.Factory factory,Uri uri){this.factory=factory;this.uri=uri;}
    // Called only by libbluray's loader thread, in 2048-byte sectors.
    public byte[] read(long position,int length)throws IOException{
        if(position<0||length<=0||length>8*CHUNK)throw new IOException("原盘读取范围无效");
        byte[] result=new byte[length];int copied=0;
        while(copied<length){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            long at=position+copied,base=at/CHUNK*CHUNK;
            byte[] block=cache.get(base);
            if(block==null){block=load(base);cache.put(base,block);}
            int offset=(int)(at-base),count=Math.min(length-copied,block.length-offset);
            if(count<=0)throw new EOFException("原盘数据不完整");
            System.arraycopy(block,offset,result,copied,count);copied+=count;
        }
        return result;
    }
    private byte[] load(long position)throws IOException{
        if((upstream==null&&network==null)||upstreamPosition!=position){
            closeUpstream();
            try{
                if("http".equals(uri.getScheme())||"https".equals(uri.getScheme())){
                    openHttp(position,resolved==null?HttpUrl.get(uri.toString()):resolved);
                    if((response.code()==401||response.code()==403)&&resolved!=null){closeUpstream();resolved=null;openHttp(position,HttpUrl.get(uri.toString()));}
                    String range=response.header("Content-Range","");Matcher match=CONTENT_RANGE.matcher(range);
                    if(response.code()!=206||!match.matches()||Long.parseLong(match.group(1))!=position)
                        throw new IOException("原盘服务器不支持 Range 随机读取（HTTP "+response.code()+"）");
                    size=Long.parseLong(match.group(3));resolved=response.request().url();
                    if(response.body()==null)throw new EOFException("原盘响应为空");network=response.body().byteStream();
                }else{
                    upstream=factory.createDataSource();long available=upstream.open(new DataSpec.Builder().setUri(uri).setPosition(position).build());
                    if(available!=C.LENGTH_UNSET)size=position+available;
                }
                upstreamPosition=position;
            }catch(IOException|RuntimeException error){closeUpstream();throw error;}
        }
        int wanted=(int)(size==C.LENGTH_UNSET?CHUNK:Math.min(CHUNK,size-position));
        if(wanted<=0)throw new EOFException("原盘读取超出文件范围");
        byte[] bytes=new byte[wanted];int got=0;
        while(got<wanted){int count=network==null?upstream.read(bytes,got,wanted-got):network.read(bytes,got,wanted-got);if(count==C.RESULT_END_OF_INPUT)throw new EOFException("原盘数据不完整");got+=count;upstreamPosition+=count;}
        return bytes;
    }
    private void openHttp(long position,HttpUrl address)throws IOException{response=HTTP.newCall(new Request.Builder().url(address).header("User-Agent",EtknApi.USER_AGENT).header("Range","bytes="+position+"-").build()).execute();}
    private void closeUpstream()throws IOException{DataSource previous=upstream;upstream=null;upstreamPosition=-1;network=null;if(response!=null){response.close();response=null;}if(previous!=null)previous.close();}
    @Override public void close()throws IOException{try{closeUpstream();}finally{cache.clear();}}
}
