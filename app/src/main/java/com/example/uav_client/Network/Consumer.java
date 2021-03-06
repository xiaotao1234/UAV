package com.example.uav_client.Network;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.example.uav_client.Application.SApplication;
import com.example.uav_client.Application.SysApplication;
import com.example.uav_client.Data.Common.ReceiveBody;
import com.example.uav_client.Data.Common.RequestBuildUtil;
import com.example.uav_client.Data.Common.Station;
import com.example.uav_client.Data.Main.DataListSource;
import com.example.uav_client.R;
import com.example.uav_client.Utils.AppExecutors;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import androidx.annotation.Nullable;

public class Consumer {
    private static List<Consumer> consumerPool = new ArrayList<>();
    private static DataListSource.getDataCallBack observerMap = null;
    private static DataListSource.getDataCallBack observerMapStation = null;
    private static DataListSource.getDataCallBack observerMaponLine = null;
    private static DataListSource.getDataCallBack mainobserver = null;
    private static DataListSource.getDataCallBack mainobserverStation = null;

    int requestCode;

    DataListSource.getDataCallBack observer;
    static AppExecutors appExecutors;

    public Consumer(int requestCode, DataListSource.getDataCallBack observer, AppExecutors appExecutors) {
        this.requestCode = requestCode;
        this.observer = observer;
        if (this.appExecutors == null) {
            this.appExecutors = appExecutors;
        }
        enqueue(this);
    }

    public int getRequestCode() {
        return requestCode;
    }

    public static void enqueue(Consumer consumer) {
        if (consumerPool.contains(consumer)) {
            consumerPool.set(consumerPool.indexOf(consumer), consumer);
        } else {
            consumerPool.add(consumer);
        }
    }

    public static void addObserverMap(DataListSource.getDataCallBack observerMap) {
        Consumer.observerMap = observerMap;
    }

    public static void addObserverMapStation(DataListSource.getDataCallBack observerMap) {
        Consumer.observerMapStation = observerMap;
    }

    public static void addUnLineObserverMap(DataListSource.getDataCallBack observerMap) {
        Consumer.observerMaponLine = observerMap;
    }

    public static void addMainObserver(DataListSource.getDataCallBack observerMap) {
        Consumer.mainobserver = observerMap;
    }

    public static void addMainObserverStation(DataListSource.getDataCallBack observerMap) {
        Consumer.mainobserverStation = observerMap;
    }

    public static Consumer getConsumer(int requestCode) {
        for (Consumer consumer : consumerPool) {
            if (consumer.requestCode == requestCode) {
                return consumer;
            }
        }
        return null;
    }

    static byte[] temSave = new byte[0];
    boolean end = false;

    public static void back(final byte[] bytes, String s) {
        int requestCode = RequestBuildUtil.unPackrequestCode(bytes, 8);
        Log.d("datacomereback", String.valueOf(requestCode));
        if (requestCode == 1) {
            SysApplication.setAlarmArea(ReceiveBody.getAlarm(ReceiveBody.initialParse(RequestBuildUtil.unPackString(bytes), ";")));
            if (observerMap != null) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        byte[] b = new byte[0];
                        observerMap.dataGet(b);
                    }
                });
            }
            if (mainobserver != null) {
                appExecutors.mainThread().execute(new Runnable(){
                    @Override
                    public void run() {
                        byte[] b = new byte[0];
                        mainobserver.dataGet(b);
                    }
                });
            }

        } else if (requestCode == 2) {
            if (observerMap != null) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        observerMap.dataGet(bytes);
                    }
                });
            } else if (mainobserver != null) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        mainobserver.dataGet(bytes);
                    }
                });
            }

            final List<String> lis = ReceiveBody.initialParse(RequestBuildUtil.unPackString(bytes), "|");
            if (Integer.parseInt(lis.get(lis.size() - 1)) == 1) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        popWindow(SApplication.activity1, lis);
                    }
                });
            }
            Log.d("datacome1", s);

        } else if (requestCode == RequestBuildUtil.getSEARCH_UAV_RESULT()) {
            if (temSave.length == 0) {
                if (RequestBuildUtil.getDataLength(bytes) > (bytes.length - 4)) {
                    temSave = new byte[bytes.length];
                    System.arraycopy(bytes, 0, temSave, 0, bytes.length);
                    temSave = bytes;
                } else {
                    BackToMain(bytes, requestCode);
                }
            } else {

            }
        }else if(requestCode == RequestBuildUtil.getSTATION_ITEM()){
            List<String> strings = ReceiveBody.initialParse(RequestBuildUtil.unPackString(bytes), ";");
            SysApplication.setStationItem(strings);
            if (observerMapStation != null) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        byte[] b = new byte[0];
                        observerMapStation.dataGet(b);
                    }
                });
            }
            if (mainobserverStation != null) {
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
                        byte[] b = new byte[0];
                        mainobserverStation.dataGet(b);
                    }
                });
            }
        }else {
            BackToMain(bytes, requestCode);
        }
    }

    private static void BackToMain(final byte[] bytes, int requestCode) {
        final Consumer consumer = getConsumer(requestCode - 1);
        if (consumer != null) {
            appExecutors.mainThread().execute(new Runnable() {
                @Override
                public void run() {
                    consumer.observer.dataGet(bytes);
                }
            });
            consumerPool.remove(consumer);
        } else {
//            consumer.observer.error();
        }
    }

    static View popupView;
    static PopupWindow newWindow = new PopupWindow(popupView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
    static TextView textView;

    public static void popWindow(Activity activity, List<String> list) {
        if (!newWindow.isShowing()) {
            popupView = activity.getLayoutInflater().inflate(R.layout.alarm_layout, null);
            popupView.setPadding(50, 0, 50, 0);
            newWindow = new PopupWindow(popupView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
            newWindow.setWidth((int) activity.getResources().getDimension(R.dimen.dp_280));
            newWindow.setAnimationStyle(R.style.popup_window_anim);
            newWindow.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#00000000")));
            newWindow.setFocusable(false);
            newWindow.setOutsideTouchable(true);
            newWindow.update();
            newWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.TOP, 0, 20);
            textView = popupView.findViewById(R.id.alarm_list);
        }
        if (textView != null) {
            StringBuilder sall = new StringBuilder();
            sall.append("无人机ID："+list.get(0));
            sall.append("频率："+list.get(1));
            sall.append("纬度："+list.get(2));
            sall.append("经度："+list.get(3));
            sall.append("俯仰角："+list.get(4));
            sall.append("日期时间："+list.get(5));
            sall.append("是否报警"+list.get(6));
            textView.setText(sall);
        }
        MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.music);
        mediaPlayer.start(); // no need to call prepare(); create() does that for you
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof Consumer) {
            if (this.requestCode == ((Consumer) obj).getRequestCode()) {
                return true;
            }
        }
        return false;
    }

    public static void stringToTime() {
        List<Date> dateList = new ArrayList<>();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    }

    public static void UnonLine(final String s) {
        appExecutors.networkIO().execute(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date data = null;
                if (observerMaponLine != null) {
                    List<String> list = ReceiveBody.initialParse(s, ";");
                    for (final String s1 : list) {
                        if (data == null) {

                            List<String> list1 = ReceiveBody.initialParse(s1, "|");
                            try {
                                data = df.parse(list1.get(5));
                            } catch (ParseException e) {
                                e.printStackTrace();
                            }

                            appExecutors.mainThread().execute(new Runnable() {
                                @Override
                                public void run() {
                                    if (observerMaponLine != null) {
                                        observerMaponLine.dataGet(s1.getBytes());
                                    }
                                }
                            });
                        } else {
                            List<String> list1 = ReceiveBody.initialParse(s1, "|");
                            try {
                                Date d = df.parse(list1.get(5));
                                long diff = d.getTime() - data.getTime();
                                data = d;
                                Thread.sleep(diff);
                                appExecutors.mainThread().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (observerMaponLine != null) {
                                            observerMaponLine.dataGet(s1.getBytes());
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.d("xiao", "时间解析错误");
                            }
                        }
                    }
                }
            }
        });
    }


}
