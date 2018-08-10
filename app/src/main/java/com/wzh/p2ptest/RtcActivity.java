package com.wzh.p2ptest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wzh.p2ptest.WebRtcClient.IMessageReceiver;

import org.json.JSONException;
import org.webrtc.DataChannel;
import org.webrtc.VideoRendererGui;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

//发送请求端
public class RtcActivity extends AppCompatActivity implements WebRtcClient.RtcListener {

    private WebRtcClient client;
    private String mSocketAddress;
    private String callerId;

    public static final String TAG = "RtcActivity";
    private TextView mTvContent;
    private Button mBtConnect;
    private EditText mEditText;
    private DataChannel dataChannel;
    private ScrollView mScrollView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        mSocketAddress = "http://" + getResources().getString(R.string.host);
        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");

        callerId = "";//先用一台设备运行，然后通过日志获取id,填入id,再运行到另一台设备上
        initView();
        init();
    }

    private void initView() {
        mTvContent = (TextView) findViewById(R.id.tv_content);
        mBtConnect = (Button) findViewById(R.id.bt_connect);
        mEditText = (EditText) findViewById(R.id.ed_text);
        mScrollView = (ScrollView) findViewById(R.id.scrollView);

        appText("服务器:" + mSocketAddress);

    }

    private void init() {
        client = new WebRtcClient(this, mSocketAddress, VideoRendererGui.getEGLContext());

        client.setIMessageReceiver(new IMessageReceiver() {
            @Override
            public void onReceiverStart() {
                appText("开始接收");
            }

            @Override
            public void onReceiverProcess(float process) {
                appText("接收中" + process);
            }

            @Override
            public void onReceiverSuccess(byte[] data, int type) {
                appText("接收完成" + data.length);

                if (type == 1) {//text
                    appText("收到 " + new String(data));
                } else if (type == 2) {//bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                    appText("收到  bitmap" + bitmap.getWidth() + " *" + bitmap.getHeight() + " =" + bitmap.getByteCount());
                    Utils.safeShowBitmapDialog(RtcActivity.this, bitmap);
                } else if (type == 3) {//文件
                    //写入文件。。。
                    Log.d(TAG, "file size=" + data.length);
                    appText("收到 文件 fileSize"+data.length);
                }
            }

        });
    }

    public void onClick(View view) throws FileNotFoundException {
        if (dataChannel == null) {
            appText("p2p连接未建立");
            return;
        } else if (dataChannel.state() != DataChannel.State.OPEN) {
            appText("p2p连接未打开");
            return;
        }
        switch (view.getId()) {
            case R.id.bt_connect:
                String text = mEditText.getText().toString().trim();
                if (TextUtils.isEmpty(text))
                    return;
                client.sendData(dataChannel, text.getBytes(), 1);
                appText(text + " 已发送");
                break;
            case R.id.bt_sendimg:
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        try {

                            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.aaa);
                            Log.d(TAG, "发送" + bitmap.getWidth() + "*" + bitmap.getHeight() + "=" + bitmap.getByteCount());
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
                            byte[] arrays = bos.toByteArray();//362296
                            bos.close();

                            client.sendData(dataChannel, arrays, 2);

                            appText("bitmap 已发送 " + arrays.length);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();

                break;
            case R.id.bt_sendfile:
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            InputStream stream = getAssets().open("music.mp3");
                            Log.d(TAG, "send file size=" + stream.available());
                            byte[] data = new byte[stream.available()];
                            stream.read(data);
                            client.sendData(dataChannel, data, 3);
                            appText("文件已发送 send file size=" + stream.available());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                }).start();


                break;
            default:

                break;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (client != null) {
            client.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (client != null) {
            client.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if (client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    @Override
    public void onCallReady(String callId) {

        String streamName = "androidP2pControl";
        appText("我的id 为:" + callId + " stream name=" + streamName + " 等待连接");
        client.start(streamName);

        if (callerId != null) {
            try {
                answer(callerId);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    private void appText(final String msg) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTvContent.append(msg + "\n");

                    mScrollView.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    }, 100);
                }
            });
        } else {
            mTvContent.append(msg + "\n");
        }
    }

    public void answer(String callerId) throws JSONException {
        client.sendMessage(callerId, "init", null);
        appText("开始连接" + callerId);

    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
                appText(newStatus);
            }
        });
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        this.dataChannel = dataChannel;
    }

}