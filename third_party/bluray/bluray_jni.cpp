// ISO navigation uses libbluray/libudfread; FFmpeg only demuxes the selected title.
// Transport remains in the application's DataSource. No FFmpeg network protocols.
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <vector>
extern "C" {
#include "libbluray/bluray.h"
#include "util/log_control.h"
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libavutil/channel_layout.h"
}

struct Disc {
    JNIEnv *env=nullptr;
    jobject reader=nullptr,input=nullptr;
    jmethodID readBlocks=nullptr,readInput=nullptr;
    BLURAY *bd=nullptr;
    BLURAY_TITLE_INFO *title=nullptr;
    AVFormatContext *format=nullptr;
    AVIOContext *io=nullptr;
    AVPacket *packet=nullptr;
    AVCodecContext *pcm=nullptr;
    AVFrame *frame=nullptr;
    bool pending=false;
    unsigned clip=0;
    std::vector<uint64_t> offsets;
};
static Disc *disc(jlong handle){return reinterpret_cast<Disc *>(handle);}
static void fail(JNIEnv *env,const char *message){if(!env->ExceptionCheck())env->ThrowNew(env->FindClass("java/io/IOException"),message);}
static int blocks(void *opaque,void *buffer,int lba,int count){
    Disc *d=static_cast<Disc *>(opaque);JNIEnv *env=d->env;
    if(lba<0||count<=0||count>4096||env->ExceptionCheck())return 0;
    jbyteArray bytes=static_cast<jbyteArray>(env->CallObjectMethod(d->reader,d->readBlocks,static_cast<jlong>(static_cast<uint32_t>(lba))*2048,count*2048));
    if(env->ExceptionCheck()||!bytes)return 0;
    int size=env->GetArrayLength(bytes);
    if(size==count*2048)env->GetByteArrayRegion(bytes,0,size,static_cast<jbyte *>(buffer));
    env->DeleteLocalRef(bytes);return size==count*2048?count:0;
}
static int inputRead(void *opaque,uint8_t *buffer,int length){
    Disc *d=static_cast<Disc *>(opaque);JNIEnv *env=d->env;
    if(env->ExceptionCheck())return AVERROR(EIO);
    jbyteArray bytes=env->NewByteArray(length);
    if(!bytes)return AVERROR(ENOMEM);
    int result=env->CallIntMethod(d->input,d->readInput,bytes,0,length);
    if(!env->ExceptionCheck()&&result>0)env->GetByteArrayRegion(bytes,0,result,reinterpret_cast<jbyte *>(buffer));
    env->DeleteLocalRef(bytes);
    return env->ExceptionCheck()?AVERROR(EIO):result==-1?AVERROR_EOF:result;
}
static int64_t inputSeek(void *opaque,int64_t offset,int whence){
    Disc *d=static_cast<Disc *>(opaque);
    return whence==AVSEEK_SIZE?static_cast<int64_t>(bd_get_title_size(d->bd)):AVERROR(ENOSYS);
}
static void reset(Disc *d){
    av_packet_free(&d->packet);avcodec_free_context(&d->pcm);av_frame_free(&d->frame);
    avformat_close_input(&d->format);
    if(d->io){av_freep(&d->io->buffer);avio_context_free(&d->io);}
    d->pending=false;
}
static void close(JNIEnv *env,Disc *d){
    if(!d)return;
    reset(d);if(d->title)bd_free_title_info(d->title);if(d->bd)bd_close(d->bd);
    if(d->reader)env->DeleteGlobalRef(d->reader);delete d;
}
#define JNI(name) extern "C" JNIEXPORT
#define METHOD(name) Java_xyz_etkn_tv_BlurayNative_##name
JNI(open) jlong JNICALL METHOD(open)(JNIEnv *env,jclass,jobject reader){
    bd_set_debug_mask(0);av_log_set_level(AV_LOG_QUIET);
    Disc *d=new Disc();d->env=env;d->reader=env->NewGlobalRef(reader);
    jclass cls=env->GetObjectClass(reader);d->readBlocks=env->GetMethodID(cls,"read","(JI)[B");env->DeleteLocalRef(cls);
    d->bd=bd_init();
    if(!d->bd||!bd_open_stream(d->bd,d,blocks)||env->ExceptionCheck()){
        fail(env,"无法读取蓝光 ISO 文件系统");close(env,d);return 0;
    }
    const BLURAY_DISC_INFO *info=bd_get_disc_info(d->bd);
    if(info&&((info->aacs_detected&&!info->aacs_handled)||(info->bdplus_detected&&!info->bdplus_handled))){
        fail(env,"该原盘仍受加密保护，无法直接播放");close(env,d);return 0;
    }
    uint32_t count=bd_get_titles(d->bd,TITLES_RELEVANT,0);
    int main=bd_get_main_title(d->bd);
    if(!count||main<0||!bd_select_title(d->bd,static_cast<uint32_t>(main))){
        fail(env,"蓝光原盘没有可播放的正片");close(env,d);return 0;
    }
    d->title=bd_get_title_info(d->bd,static_cast<uint32_t>(main),0);
    if(!d->title||!d->title->clip_count){fail(env,"蓝光正片播放列表不完整");close(env,d);return 0;}
    uint64_t offset=0;
    for(unsigned i=0;i<d->title->clip_count;i++){d->offsets.push_back(offset);offset+=static_cast<uint64_t>(d->title->clips[i].pkt_count)*192;}
    return reinterpret_cast<jlong>(d);
}
JNI(size) jlong JNICALL METHOD(size)(JNIEnv *,jclass,jlong handle){return bd_get_title_size(disc(handle)->bd);}
JNI(duration) jlong JNICALL METHOD(duration)(JNIEnv *,jclass,jlong handle){return av_rescale(disc(handle)->title->duration,1000000,90000);}
JNI(seekPoint) jlong JNICALL METHOD(seekPoint)(JNIEnv *env,jclass,jlong handle,jlong time){
    Disc *d=disc(handle);d->env=env;
    return bd_seek_time(d->bd,std::min<uint64_t>(d->title->duration,av_rescale(std::max<jlong>(0,time),90000,1000000)));
}
JNI(seek) jlong JNICALL METHOD(seek)(JNIEnv *env,jclass,jlong handle,jlong position){
    Disc *d=disc(handle);d->env=env;
    int64_t result=bd_seek(d->bd,position);
    if(result<0)fail(env,"蓝光正片跳转失败");
    return result;
}
JNI(read) jint JNICALL METHOD(read)(JNIEnv *env,jclass,jlong handle,jbyteArray output,jint offset,jint length){
    Disc *d=disc(handle);d->env=env;
    std::vector<uint8_t> bytes(length);int count=bd_read(d->bd,bytes.data(),length);
    if(count>0&&!env->ExceptionCheck())env->SetByteArrayRegion(output,offset,count,reinterpret_cast<jbyte *>(bytes.data()));
    if(count<0)fail(env,"蓝光正片数据读取失败");return count;
}
static const char *mime(AVCodecID id){
    switch(id){
        case AV_CODEC_ID_H264:return "video/avc";
        case AV_CODEC_ID_HEVC:return "video/hevc";
        case AV_CODEC_ID_MPEG2VIDEO:return "video/mpeg2";
        case AV_CODEC_ID_VC1:return "video/wvc1";
        case AV_CODEC_ID_AAC:case AV_CODEC_ID_AAC_LATM:return "audio/mp4a-latm";
        case AV_CODEC_ID_MP2:case AV_CODEC_ID_MP3:return "audio/mpeg";
        case AV_CODEC_ID_AC3:return "audio/ac3";
        case AV_CODEC_ID_EAC3:return "audio/eac3";
        case AV_CODEC_ID_DTS:return "audio/vnd.dts.hd";
        case AV_CODEC_ID_TRUEHD:case AV_CODEC_ID_MLP:return "audio/true-hd";
        case AV_CODEC_ID_PCM_BLURAY:return "audio/raw";
        case AV_CODEC_ID_HDMV_PGS_SUBTITLE:return "application/pgs";
        default:return nullptr;
    }
}
static bool demux(Disc *d,jobject input,int64_t position){
    JNIEnv *env=d->env;d->input=input;
    jclass cls=env->GetObjectClass(input);d->readInput=env->GetMethodID(cls,"read","([BII)I");env->DeleteLocalRef(cls);
    d->io=avio_alloc_context(static_cast<unsigned char *>(av_malloc(32768)),32768,0,d,inputRead,nullptr,inputSeek);
    if(!d->io)return false;
    d->io->seekable=0;d->io->pos=position;
    d->format=avformat_alloc_context();if(!d->format)return false;
    d->format->pb=d->io;d->format->flags|=AVFMT_FLAG_CUSTOM_IO;
    d->format->probesize=2*1024*1024;d->format->max_analyze_duration=5000000;
    if(avformat_open_input(&d->format,nullptr,av_find_input_format("mpegts"),nullptr)<0||avformat_find_stream_info(d->format,nullptr)<0)return false;
    d->packet=av_packet_alloc();return d->packet!=nullptr;
}
JNI(tracks) jobjectArray JNICALL METHOD(tracks)(JNIEnv *env,jclass,jlong handle,jobject input,jlong position){
    Disc *d=disc(handle);d->env=env;
    if(!demux(d,input,position)){fail(env,"蓝光正片音视频解析失败");return nullptr;}
    jclass cls=env->FindClass("xyz/etkn/tv/BlurayNative$Track");
    jmethodID constructor=env->GetMethodID(cls,"<init>","(IIIIIIILjava/lang/String;Ljava/lang/String;[B)V");
    unsigned count=0;for(unsigned i=0;i<d->format->nb_streams;i++)if(mime(d->format->streams[i]->codecpar->codec_id))count++;
    jobjectArray result=env->NewObjectArray(count,cls,nullptr);unsigned index=0;
    for(unsigned i=0;i<d->format->nb_streams&&!env->ExceptionCheck();i++){
        AVStream *stream=d->format->streams[i];AVCodecParameters *p=stream->codecpar;const char *name=mime(p->codec_id);if(!name)continue;
        int encoding=2;
        if(p->codec_id==AV_CODEC_ID_PCM_BLURAY){
            d->pcm=avcodec_alloc_context3(avcodec_find_decoder(p->codec_id));
            if(!d->pcm||avcodec_parameters_to_context(d->pcm,p)<0||avcodec_open2(d->pcm,d->pcm->codec,nullptr)<0){fail(env,"蓝光 LPCM 音轨解析失败");return nullptr;}
            d->frame=av_frame_alloc();encoding=p->bits_per_raw_sample>16||p->bits_per_coded_sample>16?0x30000000:2;
        }
        int type=p->codec_type==AVMEDIA_TYPE_VIDEO?2:p->codec_type==AVMEDIA_TYPE_AUDIO?1:3;
        AVDictionaryEntry *language=av_dict_get(stream->metadata,"language",nullptr,0);
        jstring m=env->NewStringUTF(name),lang=env->NewStringUTF(language?language->value:"");
        jbyteArray extra=env->NewByteArray(p->extradata_size);
        if(p->extradata_size)env->SetByteArrayRegion(extra,0,p->extradata_size,reinterpret_cast<jbyte *>(p->extradata));
        jobject track=env->NewObject(cls,constructor,i,type,p->width,p->height,p->sample_rate,p->ch_layout.nb_channels,encoding,m,lang,extra);
        env->SetObjectArrayElement(result,index++,track);env->DeleteLocalRef(track);env->DeleteLocalRef(m);env->DeleteLocalRef(lang);env->DeleteLocalRef(extra);
    }
    return result;
}
JNI(packet) jint JNICALL METHOD(packet)(JNIEnv *env,jclass,jlong handle,jobject input,jbyteArray output,jlongArray metadata){
    Disc *d=disc(handle);d->env=env;d->input=input;
    AVPacket *packet=d->packet;
    if(!d->pending){
        int status=av_read_frame(d->format,packet);
        if(env->ExceptionCheck())return -1;
        if(status==AVERROR_EOF)return -1;
        if(status<0){fail(env,"蓝光正片解封装失败");return -1;}
        d->pending=true;
    }
    uint8_t *bytes=packet->data;int size=packet->size;
    AVStream *stream=d->format->streams[packet->stream_index];
    if(stream->codecpar->codec_id==AV_CODEC_ID_PCM_BLURAY){
        if(!d->frame||avcodec_send_packet(d->pcm,packet)<0||avcodec_receive_frame(d->pcm,d->frame)<0){fail(env,"蓝光 LPCM 数据损坏");return -1;}
        bytes=d->frame->data[0];size=d->frame->nb_samples*d->frame->ch_layout.nb_channels*av_get_bytes_per_sample(static_cast<AVSampleFormat>(d->frame->format));
    }
    if(size>env->GetArrayLength(output))return -size;
    if(packet->pos>=0){auto it=std::upper_bound(d->offsets.begin(),d->offsets.end(),static_cast<uint64_t>(packet->pos));d->clip=it==d->offsets.begin()?0:static_cast<unsigned>(it-d->offsets.begin()-1);}
    BLURAY_CLIP_INFO *clip=&d->title->clips[d->clip];
    int64_t pts=packet->pts==AV_NOPTS_VALUE?packet->dts:packet->pts;
    int64_t time=pts==AV_NOPTS_VALUE?av_rescale(clip->start_time,1000000,90000):av_rescale_q(pts,stream->time_base,AVRational{1,1000000})+av_rescale(static_cast<int64_t>(clip->start_time)-static_cast<int64_t>(clip->in_time),1000000,90000);
    jlong values[3]={packet->stream_index,time,(packet->flags&AV_PKT_FLAG_KEY)?1:0};
    env->SetByteArrayRegion(output,0,size,reinterpret_cast<jbyte *>(bytes));env->SetLongArrayRegion(metadata,0,3,values);
    av_packet_unref(packet);if(d->frame)av_frame_unref(d->frame);d->pending=false;return size;
}
JNI(reset) void JNICALL METHOD(reset)(JNIEnv *,jclass,jlong handle){reset(disc(handle));}
JNI(close) void JNICALL METHOD(close)(JNIEnv *env,jclass,jlong handle){close(env,disc(handle));}
