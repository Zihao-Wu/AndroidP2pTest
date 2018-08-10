package com.wzh.p2ptest;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * Created by wzh on 02/08/2018.
 */

public class Utils {


    public static void safeShowBitmapDialog(final Activity activity,final Bitmap bitmap) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showBitmapDialog(activity,bitmap);
                }
            });
        } else {
            showBitmapDialog(activity,bitmap);
        }

    }

    public static void showBitmapDialog(Activity activity,final Bitmap bitmap) {
        Activity act = activity;
        LinearLayout ll = new LinearLayout(act);
        ll.setBackgroundColor(Color.BLACK);
        ll.setGravity(Gravity.CENTER);

        ImageView view = new ImageView(act);
        view.setImageBitmap(bitmap);
        ll.addView(view);
        new AlertDialog.Builder(act).setView(ll).show();
    }
}
