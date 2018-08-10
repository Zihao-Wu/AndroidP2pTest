package com.wzh.p2ptest;

import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.io.IOException;

/**
 * Created by wzh on 06/08/2018.
 */

public class ScreenActivity extends AppCompatActivity{

    private MediaProjectionManager manager;
    private MediaProjection mediaProject;
    private DisplayMetrics metrics;
    private MediaRecorder mediaRecorder;
    private String TAG="ScreenActivity";
    private boolean running;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen);

        initView();

        metrics=getResources().getDisplayMetrics();
    }

    private void initView() {

        manager= (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Intent intent= null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            intent = manager.createScreenCaptureIntent();
        }
        startActivityForResult(intent,10);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void onClick(View view){
        switch(view.getId()){
            case R.id.button:
                startRecord();
              break;
            case R.id.button1:
                mediaRecorder.stop();
                mediaProject.stop();
                break;
            default:

              break;
        }
    }

    private void initRecorder() {
        mediaRecorder =new MediaRecorder();

        File file = new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis() + ".mp4");
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(file.getAbsolutePath());
        mediaRecorder.setVideoSize(metrics.widthPixels, metrics.heightPixels);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setVideoFrameRate(30);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean startRecord() {
        if (mediaProject == null || running) {
            return false;
        }
        initRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            createVirtualDisplay();
        }
        mediaRecorder.start();
        running = true;
        return true;
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==10){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaProject=manager.getMediaProjection(resultCode,data);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createVirtualDisplay() {
        mediaProject.createVirtualDisplay("mainScreen",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(),new VirtualDisplay.Callback() {

                    @Override
                    public void onPaused() {
                        super.onPaused();
                        Log.d(TAG,"onPaused");

                    }

                    @Override
                    public void onResumed() {
                        super.onResumed();
                        Log.d(TAG,"onResumed");

                    }

                    @Override
                    public void onStopped() {
                        super.onStopped();
                        Log.d(TAG,"onStopped");
                    }
                },null);
    }
}
