package com.poscolearningsupport;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {
    //웹뷰를 위한 변수
    public WebView mWebView;
    public WebSettings mWebSettings;
    String hostName="http://app.poscohrd.com:8000";
    String AppDate="200304";
    String firstPageUrl=hostName+"/?ca=main&from=androidAppDate"+AppDate;
    String hostNameNotHttp="app.poscohrd.com";

    //BLE를 위한 변수
    public final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    public BluetoothManager bluetoothManager;
    public BluetoothAdapter bluetoothAdapter;
    ArrayList<String> MacList = new ArrayList<String>();
    ArrayList<String> beaconList = new ArrayList<String>();

    //GPS를 위한 변수
    String gpsResult = "";
    Float gpsAccuracy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.webview_main);
        mWebView.setWebViewClient(new WebViewClient());
        mWebSettings = mWebView.getSettings();
        mWebSettings.setJavaScriptEnabled(true);
        mWebView.loadUrl(firstPageUrl);
        mWebView.setWebViewClient(new WebViewClientClass());

        //쿠키 동기화
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.createInstance(this);
        }

        Toast.makeText(getApplicationContext(), "포스코인재창조원 학습지원 앱", Toast.LENGTH_SHORT).show();

        //BLE 가능여부 체크 후 스위치 On
        //BLE를 위한 초기세팅
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        requestEnableBLE(bluetoothAdapter);

        //비콘감지 후 서버로 정보 보낼 때 StrictMode로 인한 오류 방지
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        //위치 퍼미션 강제 발생코드
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
    }

    @Override
    protected void onResume() {
        super.onResume();

        //쿠키 동기화
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().startSync();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        //쿠키 동기화
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            CookieSyncManager.getInstance().stopSync();
        }
    }

    //뒤로가기 눌렀을 때 앱종료 NO 뒤로가게
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack() ){
            WebBackForwardList webBackForwardList = mWebView.copyBackForwardList();
            String backUrl = webBackForwardList.getItemAtIndex(webBackForwardList.getCurrentIndex() - 1).getUrl();
            String nowUrl = mWebView.getUrl();
            if (backUrl.equals(hostName+"/?ca=login")) { //로그인 화면으로 돌아가는 것이라면 프로그램 종료
                closeApp();
            } else if (nowUrl.equals(hostName+"/?ca=main") || nowUrl.equals(hostName+"/?ca=login")) { //현재 페이지가 메인화면이거나 로그인화면이라면 프로그램 종료
                closeApp();
            } else if ((keyCode == KeyEvent.KEYCODE_BACK) && (mWebView.canGoBack() == false)) { //백할 페이지가 없다면 프로그램 종료
                closeApp();
            } else if (nowUrl.equals(hostName+"/?ca=main")==false) { //세부메뉴 화면에서 백하는 것이라면 무조건 메인화면으로
                mWebView.loadUrl(hostName+"/?ca=main");
            } else { //다른 상황이 있을려나... 있다면 페이지 뒤로가기 허용
                mWebView.goBack();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //앱종료 메쏘드
    public void closeApp() {
        //다이아로그박스 출력
        new AlertDialog.Builder(this)
                .setTitle("앱종료")
                .setMessage("학습지원 앱을 종료하시겠습니까?")
                .setPositiveButton("예", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                })
                .setNegativeButton("아니오",  null).show();
    }

    //BLE 스캔시작 메쏘드
    public void startScan() {
        boolean permissionCheck = false;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //위치 퍼미션 강제 발생코드
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        } else {
            permissionCheck = true;
        }

        if (permissionCheck) {
            //BLE 가능여부 체크 후 스위치 On
            //BLE를 위한 초기세팅
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            boolean setBluetoothOn = requestEnableBLE(bluetoothAdapter);

            //블루투스를 지원하는 기기이며 스위치 On 요청 후 스캔 시작
            if (setBluetoothOn) {
                boolean SwitchOn = checkBLEswithOn(bluetoothAdapter);
                if (SwitchOn) {
                    //MAC 리스트 초기화
                    MacList.clear();

                    //스캔시작 메세지 출력
                    Toast.makeText(getApplicationContext(), "교육장 신호를 감지합니다.", Toast.LENGTH_LONG).show();

                    //스캔 시작
                    bluetoothAdapter.startLeScan(leScanCallback);

//                    //GPS 켜져 있는지 확인
//                    final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//                    final boolean gpsSwitchOn = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
//
//                    if (gpsSwitchOn) {
//                        //GPS 좌표 따기
//                        String locationProvider = LocationManager.NETWORK_PROVIDER;
//                        Location location = lm.getLastKnownLocation(locationProvider);
//                        if (location==null) {
//                            Log.d("NETWORK_PROVIDER","마지막 위치 없음. GPS 위치 사용");
//                            locationProvider = LocationManager.GPS_PROVIDER;
//                            location = lm.getLastKnownLocation(locationProvider);
//                        }
//                        if (location==null) {
//                            Log.d("GPS_PROVIDER","마지막 위치 없음. 어떻게 하지??");
//                        } else {
//                            String latitude = Double.toString(location.getLatitude());
//                            String longitude = Double.toString(location.getLongitude());
//                            String altitude = Double.toString(location.getAltitude());
//                            String accuracy = Float.toString(location.getAccuracy());
//
//                            //위치정보_경도_위도_고도_정확도(meter)
//                            gpsResult = latitude + "_" + longitude + "_" + altitude + "_" + accuracy;
//                            gpsAccuracy = location.getAccuracy();
//                        }
//                        Log.d("GPS", "시작");
//                        Log.d("GPS결과", gpsResult);
//                        lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1, 10, gpsLocationListener);
//                        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 10, gpsLocationListener);
//                    } else {
//                        //Toast.makeText(getApplicationContext(), "GPS 관련 오류가 발생하였습니다.", Toast.LENGTH_LONG).show();
//                        gpsResult = "gpsSwitchOff";
//                        Log.d("GPS 스위치", "켜져 있지 않음");
//                    }

                    //10초 뒤 모든 스캔 스톱
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopScan();
                            Log.d("BLE", "중지");
//                            if (gpsSwitchOn) {
//                                lm.removeUpdates(gpsLocationListener);
//                                Log.d("GPS", "중지");
//                            }
                        }
                    }, 10000);
                } else {
                    Toast.makeText(getApplicationContext(), "블루투스 스위치를 켠 다음 시도해 주십시오.", Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(getApplicationContext(), "위치 권한 관련 오류가 발생하였습니다. 권한을 허용한 후 다시 시도해 주세요.", Toast.LENGTH_LONG).show();
        }
    }

    //BLE 스캔종료 메쏘드
    public void stopScan() {
        //스캔을 먼저 중지하고
        bluetoothAdapter.stopLeScan(leScanCallback);

        String MacResults = null;
        HashSet<String> OnlyMacList = new HashSet<String>(MacList);
        MacList = new ArrayList<String>(OnlyMacList);
        for (String list : MacList) {
            if (MacResults == null) {
                MacResults = list;
            } else {
                MacResults = MacResults + "|" + list;
            }
        }

        //Toast.makeText(getApplicationContext(), MacResults, Toast.LENGTH_LONG).show();
        //맥리스트 서버로 전송
        int resultsMsg=sendMacListToServer(MacResults);
        if (resultsMsg!=200) {
            Toast.makeText(getApplicationContext(), "서버통신 실패("+resultsMsg+")", Toast.LENGTH_LONG).show();
        }
    }

    //해당 과정에 등록된 비콘 리스트 가져오기
    public String getBeaconListByString() {
        int responseCode=0;
        String c=getCookie(hostName,"c");
        String i=getCookie(hostName,"i");
        String selectedCuriNo=getCookie(hostName,"selectedCuriNo");
        String selectedChaNo=getCookie(hostName,"selectedChaNo");
        //String setUrl=hostName+"/?ca=getBeaconList&c=Y&i=ZlQxTW1PZVk5azJ2MXBlK2FGS2dJQT09&selectedCuriNo=522966&selectedChaNo=665710";
        String setUrl=hostName+"/?ca=getBeaconList&c="+c+"&i="+i+"&selectedCuriNo="+selectedCuriNo+"&selectedChaNo="+selectedChaNo;
        String responseResults=null;
        try {
            URL httpURL = new URL(setUrl); // 요청을 보내기 위한 준비를 한다.
            HttpURLConnection conn = (HttpURLConnection) httpURL.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                //Stream을 처리해줘야 하는 귀찮음이 있음.
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "utf-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                responseResults=sb.toString();
                try {
                    JSONObject jsonObject  = new JSONObject(responseResults);
                    JSONArray arr = jsonObject.getJSONArray("beaconInfo");
                    for(int b=0; b<arr.length(); b++) {
                        JSONObject beaconObject = arr.getJSONObject(b);
                        Log.d("비콘 맥 리스트 ", beaconObject.getString("macAddress"));
                        beaconList.add(beaconObject.getString("macAddress"));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            conn.disconnect();
        } catch (MalformedURLException e) {
            responseCode=1;
        } catch (IOException e) {
            responseCode=2;
        }
        if (responseCode!=200) {
            return "error";
        } else {
            return responseResults;
        }
    }

    //맥리스트 서버로 전송하는 메쏘드
    public int sendMacListToServer(String MacResults) {
        int responseCode=0;
        String c=getCookie(hostName,"c");
        String i=getCookie(hostName,"i");
        String selectedCuriNo=getCookie(hostName,"selectedCuriNo");
        String selectedChaNo=getCookie(hostName,"selectedChaNo");
        SimpleDateFormat thisDateFormat = new SimpleDateFormat ( "yyyy-MM-dd");
        Date time = new Date();
        String chulDate = thisDateFormat.format(time);
        String setUrl=hostName+"/?ca=setChulSign&c="+c+"&i="+i+"&selectedCuriNo="+selectedCuriNo+"&selectedChaNo="+selectedChaNo+"&chulDate="+chulDate+"&macResults="+MacResults+"&gps="+gpsResult;
        Log.d("최종 결과", setUrl);
        try {
            URL httpURL = new URL(setUrl); // 요청을 보내기 위한 준비를 한다.
            HttpURLConnection conn = (HttpURLConnection) httpURL.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setRequestMethod("GET");
            responseCode = conn.getResponseCode();
            conn.disconnect();
            Log.d("통신결과", Integer.toString(responseCode));
            String responseResults=null;
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder sb = new StringBuilder();
                //Stream을 처리해줘야 하는 귀찮음이 있음.
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "utf-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                br.close();
                responseResults=sb.toString();
                Log.d("통신응답", responseResults);
            }
        } catch (MalformedURLException e) {
            responseCode=1;
        } catch (IOException e) {
            responseCode=2;
        }
        return responseCode;
    }

    //스캔 이후 장치 발견 이벤트
    public BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        runOnUiThread(new Runnable() {
            public void run() {
                //스캔한 내용 출력
                String DeviceAddress = device.getAddress();
                if (beaconList.contains(DeviceAddress)) {
                    MacList.add(DeviceAddress);
                }
            }
        });
        }
    };

    //블루투스 켜져있는지 체크
    public boolean checkBLEswithOn(BluetoothAdapter bluetoothAdapter) {
        if (!bluetoothAdapter.isEnabled()) { //연결 안되었을 때
            return false;
        } else {
            return true;
        }
    }

    //블루투스 자동 on 메쏘드
    public boolean requestEnableBLE(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null) { //블루투스를 지원하지 않으면 토스트 메세지
            Toast.makeText(this, "블루투스를 지원하지 않는 장치입니다.", Toast.LENGTH_SHORT).show();
            return false;
        } else {
            if (!bluetoothAdapter.isEnabled()) { //연결 안되었을 때
                Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //블루투스 연결
                startActivity(i);
            }
            return true;
        }
    }

    //GPS리스너
    final LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            String latitude = Double.toString(location.getLatitude());
            String longitude = Double.toString(location.getLongitude());
            String altitude = Double.toString(location.getAltitude());
            String accuracy = Float.toString(location.getAccuracy());

            if (gpsAccuracy!=null) {
                if (location.getAccuracy() < gpsAccuracy) {
                    //위치정보_경도_위도_고도_정확도(meter)
                    gpsResult = latitude + "_" + longitude + "_" + altitude + "_" + accuracy;
                    Log.d("GPS결과", gpsResult);
                } else {
                    Log.d("GPS결과 - 정확도가 낮아 변경하지 않음", gpsResult);
                }
            } else {
                //위치정보_경도_위도_고도_정확도(meter)
                gpsAccuracy = location.getAccuracy();
                gpsResult = latitude + "_" + longitude + "_" + altitude + "_" + accuracy;
                Log.d("GPS결과", gpsResult);
            }
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onProviderDisabled(String provider) {
        }
    };

    //웹뷰 화면에서의 이벤트 클래스
    public class WebViewClientClass extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            //Log.d("URL 체크 : ", Uri.parse(url).getHost());
            //외부 URL로 연결하는 경우(카훗, 이플 등) 스마트폰 자체 브라우저로 실행
            if (Uri.parse(url).getHost().equals(hostNameNotHttp)) {
                // This is my website, so do not override; let my WebView load the page
                view.loadUrl(url);
                return false;
            } else {
                // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                return true;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String chulSign = uri.getQueryParameter("chulSign");
                    if (chulSign != null) {
                        //BLE를 위한 초기세팅
                        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                        bluetoothAdapter = bluetoothManager.getAdapter();

                        //BLE 가능여부 체크 후 스위치 On
                        requestEnableBLE(bluetoothAdapter);
                    }
                    int sharpPos = url.indexOf('#');
                    String chulStartSign = null;
                    if (chulSign != null && sharpPos >= 0) {
                        chulStartSign = url.substring(sharpPos);
                    }
                    if (chulStartSign != null) {
                        Toast.makeText(getApplicationContext(), "출석신호를 감지합니다", Toast.LENGTH_LONG).show();
                        //해당 과정에 등록된 비콘 리스트 가져오기
                        String beaconList=getBeaconListByString();
                        if (beaconList=="error") {
                            Toast.makeText(getApplicationContext(), "서버통신 실패", Toast.LENGTH_LONG).show();
                        } else {
                            //여기부터 비콘감지 및 URL보내기 작동!
                            startScan();
                        }
                    }
                }
            }

            //쿠키값 유지
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                CookieSyncManager.getInstance().sync();
            } else {
                CookieManager.getInstance().flush();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            switch(errorCode) {
                case ERROR_AUTHENTICATION:               // 서버에서 사용자 인증 실패
                case ERROR_BAD_URL:                            // 잘못된 URL
                case ERROR_CONNECT:                           // 서버로 연결 실패
                case ERROR_FAILED_SSL_HANDSHAKE:     // SSL handshake 수행 실패
                case ERROR_FILE:                                   // 일반 파일 오류
                case ERROR_FILE_NOT_FOUND:                // 파일을 찾을 수 없습니다
                case ERROR_HOST_LOOKUP:            // 서버 또는 프록시 호스트 이름 조회 실패
                case ERROR_IO:                               // 서버에서 읽거나 서버로 쓰기 실패
                case ERROR_PROXY_AUTHENTICATION:    // 프록시에서 사용자 인증 실패
                case ERROR_REDIRECT_LOOP:                // 너무 많은 리디렉션
                case ERROR_TIMEOUT:                          // 연결 시간 초과
                case ERROR_TOO_MANY_REQUESTS:            // 페이지 로드중 너무 많은 요청 발생
                case ERROR_UNKNOWN:                         // 일반 오류
                case ERROR_UNSUPPORTED_AUTH_SCHEME:  // 지원되지 않는 인증 체계
                case ERROR_UNSUPPORTED_SCHEME:

                    Toast.makeText(getApplicationContext(), "네트워크에 이상이 있습니다.", Toast.LENGTH_LONG).show();

                    break; //URI가 지원되지 않는 방식
            }
        }
    }

    //웹뷰에 저장된 쿠키 가져오는 메쏘드
    public String getCookie(String siteName,String CookieName){
        String CookieValue = null;
        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(siteName);
        String[] temp=cookies.split(";");
        for (String ar1 : temp ){
            String[] temp1=ar1.split("=");
            //Log.d("쿠키네임", temp1[0].trim());
            //Log.d("쿠키값", temp1[1].trim());
            if (temp1[0].trim().equals(CookieName.trim())) {
                CookieValue = temp1[1].trim();
                break;
            }
        }
        return CookieValue;
    }
}