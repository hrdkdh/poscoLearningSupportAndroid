package com.poscolearningsupport;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    //웹뷰를 위한 변수
    public WebView mWebView;
    public WebSettings mWebSettings;
    String hostName="http://app.poscohrd.com:8000";
    String AppVersion="001.004";
    String firstPageUrl=hostName+"/?ca=main&from=androidAppVersion_"+AppVersion;
    String hostNameNotHttp="app.poscohrd.com";

    //BLE를 위한 변수
    public final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    public final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1;
    public BluetoothManager bluetoothManager;
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bluetoothLeScanner;

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

        Toast.makeText(getApplicationContext(), "포스코인재창조원 학습지원 앱", Toast.LENGTH_SHORT).show();

        //BLE 가능여부 체크 후 스위치 On
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;
        bluetoothAdapter = bluetoothManager.getAdapter();
        requestEnableBLE(bluetoothAdapter);

        //비콘감지 후 서버로 정보 보낼 때 StrictMode로 인한 오류 방지
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        requestLocationPermission();
    }

    //위치 퍼미션 체크 메쏘드
    public int checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION);
            return permissionCheck;
        } else {
            int permissionCheck = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION);
            return permissionCheck;
        }
    }

    //위치 퍼미션 요청 메쏘드
    public void requestLocationPermission() {
        int permissionCheck = checkLocationPermission();
        if (permissionCheck<0) {
            Log.d("위치 퍼미션", "권한 허용 필요");
            Log.d("퍼미션 상태", String.valueOf(permissionCheck));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (permissionCheck<0) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            }
        } else {
            if (permissionCheck<0) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
            } else if (!mWebView.canGoBack()) { //백할 페이지가 없다면 프로그램 종료
                closeApp();
            } else if (!nowUrl.equals(hostName + "/?ca=main")) { //세부메뉴 화면에서 백하는 것이라면 무조건 메인화면으로
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
        //BLE 가능여부 체크 후 스위치 On
        try {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            assert bluetoothManager != null;
            bluetoothAdapter = bluetoothManager.getAdapter();
        } catch(Exception e) {
            Toast.makeText(getApplicationContext(), "블루투스 미지원 기기이므로 출석이 불가능합니다.", Toast.LENGTH_LONG).show();
        } finally {
            boolean setBluetoothOn = requestEnableBLE(bluetoothAdapter);
            //블루투스를 지원하는 기기이면 스위치 On 요청 후 스캔 시작
            if (setBluetoothOn) {
                boolean SwitchOn = checkBLEswithOn(bluetoothAdapter);
                if (SwitchOn) {
                    //MAC 리스트 초기화
                    MacList.clear();

                    //스캔시작 메세지 출력
                    Toast.makeText(getApplicationContext(), "교육장 신호를 감지합니다.", Toast.LENGTH_LONG).show();

                    //스캔 시작
                    bluetoothLeScanner.startScan(ScanCallback);

                    //10초 뒤 모든 스캔 스톱
                    final Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            stopScan();
                            Log.d("BLE", "중지");
                        }
                    }, 10000);
                } else {
                    Toast.makeText(getApplicationContext(), "블루투스 스위치를 켠 다음 시도해 주십시오.", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "블루투스 미지원 기기이므로 출석이 불가능합니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    //BLE 스캔종료 후 실행
    public void stopScan() {
        //스캔을 먼저 중지하고
        bluetoothLeScanner.stopScan(ScanCallback);

        String MacResults = "";
        HashSet<String> OnlyMacList = new HashSet<String>(MacList);
        MacList = new ArrayList<String>(OnlyMacList);
        for (String list : MacList) {
            if (MacResults.equals("")) {
                MacResults = list;
            } else {
                MacResults = MacResults + "|" + list;
            }
        }

        //맥리스트 서버로 전송
        int resultsMsg= 0;
        Log.d("맥결과", "결과 - "+MacResults);

        if (!MacResults.equals("")) { //매칭된 Mac이 있다면
            resultsMsg = sendMacListToServer(MacResults);
            if (resultsMsg!=200) {
                Toast.makeText(getApplicationContext(), "교육장 신호 감지에 성공하였으나 서버통신에 실패하였습니다. (오류코드 : "+String.valueOf(resultsMsg)+")", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "교육장 신호 감지에 실패하였습니다.", Toast.LENGTH_LONG).show();
        }
    }

    //스캔 이후 장치 발견 이벤트 - new version
    public ScanCallback ScanCallback = new ScanCallback () {
        public void onScanResult(int callbackType, ScanResult result) {
            processResult(result);
        }
        public void onScanFailed(int errorCode) {
        }

        private void processResult(final ScanResult result) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String DeviceAddress = result.getDevice().toString();
                    Log.d("발견한 BLE", "디바이스 - " + DeviceAddress);
                    if (beaconList.contains(DeviceAddress)) {
                        MacList.add(DeviceAddress);
                    }
                }
            });
        }
    };

    //해당 과정에 등록된 비콘 리스트 가져오기
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public String getBeaconListByString() {
        int responseCode=0;
        String c=getCookie(hostName,"c");
        String i=getCookie(hostName,"i");
        String selectedCuriNo=getCookie(hostName,"selectedCuriNo");
        String selectedChaNo=getCookie(hostName,"selectedChaNo");
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
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
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
                    Toast.makeText(getApplicationContext(), "비콘 리스트 정렬에 실패했습니다.", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
            conn.disconnect();
        } catch (MalformedURLException e) {
            responseCode=1;
            beaconLoadFailureToastMessage(responseCode);
        } catch (IOException e) {
            responseCode=2;
            beaconLoadFailureToastMessage(responseCode);
        }
        if (responseCode!=200) {
            beaconLoadFailureToastMessage(responseCode);
            return "error";
        } else {
            return responseResults;
        }
    }

    public void beaconLoadFailureToastMessage(int responsecode) {
        Toast.makeText(getApplicationContext(), "서버에서 비콘 정보를 로드하는데 실패했습니다. (오류코드 : "+String.valueOf(responsecode)+")", Toast.LENGTH_LONG).show();
    }

    //맥리스트 서버로 전송하는 메쏘드
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public int sendMacListToServer(String MacResults) {
        int responseCode=0;
        String c=getCookie(hostName,"c");
        String i=getCookie(hostName,"i");
        String selectedCuriNo=getCookie(hostName,"selectedCuriNo");
        String selectedChaNo=getCookie(hostName,"selectedChaNo");
        @SuppressLint("SimpleDateFormat") SimpleDateFormat thisDateFormat = new SimpleDateFormat ( "yyyy-MM-dd");
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
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
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
            sendMacListToServerFailureToastMessage(responseCode);
        } catch (IOException e) {
            responseCode=2;
            sendMacListToServerFailureToastMessage(responseCode);
        }
        return responseCode;
    }

    public void sendMacListToServerFailureToastMessage(int responseCode) {
        Toast.makeText(getApplicationContext(), "서버로 출석 정보를 전송하는데 실패했습니다. (오류코드 : "+String.valueOf(responseCode)+")", Toast.LENGTH_LONG).show();
    }

    //블루투스 켜져있는지 체크
    public boolean checkBLEswithOn(BluetoothAdapter bluetoothAdapter) {
        //연결 안되었을 때
        return bluetoothAdapter.isEnabled();
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

    //웹뷰 화면에서의 이벤트 클래스
    public class WebViewClientClass extends WebViewClient {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            //Log.d("URL 체크 : ", Uri.parse(url).getHost());
            //외부 URL로 연결하는 경우(카훗, 이플 등) 스마트폰 자체 브라우저로 실행
            if (Objects.equals(Uri.parse(url).getHost(), hostNameNotHttp)) {
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
                        //위치 권한 체크
                        requestLocationPermission();

                        //BLE 가능여부 체크 후 스위치 On
                        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                        assert bluetoothManager != null;
                        bluetoothAdapter = bluetoothManager.getAdapter();
                        requestEnableBLE(bluetoothAdapter);
                    }
                    int sharpPos = url.indexOf('#');
                    String chulStartSign = null;
                    if (chulSign != null && sharpPos >= 0) {
                        chulStartSign = url.substring(sharpPos);
                    }
                    if (chulStartSign != null) {
                        int permissionCheck = checkLocationPermission();
                        if (permissionCheck<0) {
                            Log.d("위치 퍼미션", "권한 허용 필요");
                            Log.d("퍼미션 상태", String.valueOf(permissionCheck));
                            //이 경우라면 출석권한 deny 버튼을 누른 다음 출석버튼(아이콘)을 클릭한 경우임. 따라서 경고창 출력
                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle("출석처리 불가").setMessage("위치 권한을 허용하지 않아 출석처리가 불가능합니다. 위치 권한 확인 메세지가 뜰 경우 '허용'을 눌러 주십시오. 만약 위치 권한 확인 메세지가 뜨지 않을 경우 스마트폰 설정 메뉴로 들어가 권한을 직접 허용해 주십시오. (폰설정 -> 앱 -> 학습지원앱 -> 권한 -> 위치권한 허용)");
                            AlertDialog alertDialog = builder.create();
                            alertDialog.show();
                            mWebView.loadUrl(firstPageUrl);
                        } else {
                            Toast.makeText(getApplicationContext(), "출석신호를 감지합니다", Toast.LENGTH_LONG).show();
                            //해당 과정에 등록된 비콘 리스트 가져오기
                            String beaconList = getBeaconListByString();
                            if (beaconList.equals("error")) {
                                Toast.makeText(getApplicationContext(), "서버통신 실패", Toast.LENGTH_LONG).show();
                            } else {
                                //여기부터 비콘감지 및 URL보내기 작동!
                                startScan();
                            }
                        }
                    }
                }
            }

            //쿠키값 유지
            CookieManager.getInstance().flush();
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