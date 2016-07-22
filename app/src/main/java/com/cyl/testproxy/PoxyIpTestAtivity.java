package com.cyl.testproxy;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by CYL on 16-7-19.
 * email:670654904@qq.com
 */
public class PoxyIpTestAtivity extends Activity {
    private TextView contentContainer;
    private WorkHandler workHandler;
    private SeekBar seekBar;

    private ExecutorService executorService = Executors.newFixedThreadPool(50);

    private final static int STARTRUN = 1;
    private final static int STARTREADSERVERS = 2;
    private int progress;
    private List<String> servers = new ArrayList<>();
    private Handler mainHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            String message = (String) msg.obj;
           synchronized (contentContainer){
               if(message != null){
                   if(contentContainer.getLineCount() > 50){
                       contentContainer.setText(message + "\n");
                   }
                   else{
                       contentContainer.setText(contentContainer.getText().toString()+message+"\n");
                   }

               }
               if(msg.what == 1){
                   progress ++;
                   seekBar.setProgress(progress);
               }
               if(msg.what == -1){
                   seekBar.setMax(servers.size());
               }
           }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.proxy_test_activity_layout);
        HandlerThread handlerThread = new HandlerThread("getserver");
        handlerThread.start();
        workHandler = new WorkHandler(handlerThread.getLooper());
        contentContainer = (TextView) findViewById(R.id.content_contianer);
        seekBar = (SeekBar) findViewById(R.id.seekbar);


        if(Build.VERSION.SDK_INT >= 23){

            if(!checkPermission()){
                granted();
            }
        }

    }
    @TargetApi(23)
    public boolean checkPermission(){
        String[] permissions = {"android.permission.WRITE_EXTERNAL_STORAGE"
                ,"android.permission.READ_EXTERNAL_STORAGE"};
        boolean flag = true;
        for (String permission:permissions){
            flag = checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
            if(!flag){
                break;
            }
        }
        return flag;
    }

    public void granted(){
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(!checkPermission()){
            granted();
        }
    }

    public class WorkHandler extends Handler{
        public WorkHandler(Looper looper){
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int what = msg.what;
            switch (what){
                case STARTRUN:
                    synchronized (servers){
                        List<String> tem = servers;
                        for (String host:tem){
                            handleServer(host);
                        }
                    }
                    break;
                case STARTREADSERVERS:
                    readServers();
                    break;
            }
        }
    }
    private final static String SERVERS = "server.txt";
    private void readServers() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                File file = new File(Environment.getExternalStorageDirectory(),SERVERS);
                if(file.exists()){
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                        String line = null;
                        while((line = reader.readLine()) != null){
                            if(line.contains(":")){
                                String[] data = line.split(":");
                                if(data != null && data.length == 2){
                                    servers.add(line.trim());
                                }
                            }
                        }
                        reader.close();
                        workHandler.sendEmptyMessage(STARTRUN);
                        mainHandler.obtainMessage(-1,"待测试ip：" +servers.size()+
                                "个").sendToTarget();
                    } catch (Exception e) {
                        e.printStackTrace();
                        mainHandler.obtainMessage(0,"read error:"+e.getMessage()).sendToTarget();
                    }
                }

            }
        });
    }

    private void handleServer(final String host) {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try{
                    if(host.contains(":")){
                        String data[] = host.split(":");
                        Proxy proxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress(data[0],Integer.valueOf(data[1].trim())));
                        HttpURLConnection connection = (HttpURLConnection) new URL("http://blog.csdn.net/wirelessqa/article/details/10131847").openConnection(proxy);
                        connection.setConnectTimeout(2000);
                        connection.setReadTimeout(1000);
                        int responseCode = connection.getResponseCode() ;
                        if(responseCode == 200){
                            BufferedWriter writer = new BufferedWriter( new OutputStreamWriter(new FileOutputStream(new File(Environment.getExternalStorageDirectory(),"proxy.txt"),true)));
                            writer.write(host+"\n");
                            writer.close();
                            mainHandler.obtainMessage(1,"可代理ip: "+host).sendToTarget();
                        }
                        else{
                            mainHandler.obtainMessage(1,"errorCode: "+responseCode+"==========>"+host).sendToTarget();
                        }
                    }
                }catch (Exception e){
                    e.printStackTrace();
                    mainHandler.obtainMessage(1,"Exception: "+e.getMessage()+"==========>"+host).sendToTarget();
                }

            }
        });
    }

    public void start(View view){
        findViewById(R.id.start).setEnabled(false);
        mainHandler.obtainMessage(0,"test is start").sendToTarget();
        workHandler.sendEmptyMessage(STARTREADSERVERS);
    }
}
