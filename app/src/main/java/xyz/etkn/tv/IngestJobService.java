package xyz.etkn.tv;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;

public final class IngestJobService extends JobService {
    @Override public boolean onStartJob(JobParameters parameters){
        TvActivity.IO.execute(()->{boolean retry=false;try{IngestNotifications.check(getApplicationContext());}catch(Exception error){retry=true;}boolean again=retry;new Handler(Looper.getMainLooper()).post(()->jobFinished(parameters,again));});
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
