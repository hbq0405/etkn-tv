package xyz.etkn.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** 未捕获异常落盘 + 下次启动提示一次，用于定位电视端偶发闪退（拿不到 adb 时的唯一线索）。 */
final class CrashLog {
    private static boolean installed;
    private static Context app;
    static synchronized void install(Context context){
        if(installed)return;installed=true;app=context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{
                StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));
                File file=new File(app.getFilesDir(),"last-crash.txt");
                try(FileOutputStream output=new FileOutputStream(file,false)){
                    output.write((System.currentTimeMillis()+" "+error+"\n"+trace).getBytes(StandardCharsets.UTF_8));
                }
            }catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
    static void reportLast(Activity activity){
        try{
            File file=new File(app.getFilesDir(),"last-crash.txt");
            if(!file.isFile())return;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(FileInputStream input=new FileInputStream(file)){
                byte[] buffer=new byte[4096];
                for(int count;(count=input.read(buffer))!=-1;)bytes.write(buffer,0,count);
            }
            file.delete();
            String[] lines=new String(bytes.toByteArray(),StandardCharsets.UTF_8).split("\n");
            StringBuilder summary=new StringBuilder();
            for(int i=0;i<lines.length;i++){
                if(lines[i].isEmpty())continue;
                if(summary.length()>0)summary.append('\n');
                summary.append(lines[i]);
                if(summary.length()>600||i>=5)break;
            }
            String report=summary.toString();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                if(activity.isFinishing()||activity.isDestroyed())return;
                try{new AlertDialog.Builder(activity).setTitle("上次异常退出").setMessage(report+"\n\n如反复出现，请把以上信息反馈给开发者。").setPositiveButton("知道了",null).show();}catch(Throwable ignored){}
            });
        }catch(Throwable ignored){}
    }
}
