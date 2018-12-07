package com.rtaksoft.ocr;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.baidu.ocr.sdk.OCR;
import com.baidu.ocr.sdk.OnResultListener;
import com.baidu.ocr.sdk.exception.OCRError;
import com.baidu.ocr.sdk.model.AccessToken;
import com.baidu.ocr.sdk.model.IDCardParams;
import com.baidu.ocr.sdk.model.IDCardResult;
import com.baidu.ocr.ui.camera.CameraActivity;

import org.json.JSONArray;

import java.io.File;

import io.dcloud.common.DHInterface.AbsMgr;
import io.dcloud.common.DHInterface.IApp;
import io.dcloud.common.DHInterface.ISysEventListener;
import io.dcloud.common.DHInterface.IWebview;
import io.dcloud.common.DHInterface.StandardFeature;
import io.dcloud.common.util.JSUtil;


public class OCRImpl extends StandardFeature {
    private static final int REQUEST_CODE_CAMERA = 102;
    private static final int REQUEST_CODE_DRIVING_LICENSE = 103;
    private static final int REQUEST_CODE_VEHICLE_LICENSE = 104;
    final Handler handler = new Handler();
    private IWebview pWebview;
    private String callbackId;

    @Override
    public void init(AbsMgr absMgr, String s) {
        super.init(absMgr, s);
        final Context context = absMgr.getContext();
        OCR.getInstance(context)
                .initAccessTokenWithAkSk(new OnResultListener<AccessToken>() {
                    @Override
                    public void onResult(AccessToken accessToken) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "初始化认证成功", Toast.LENGTH_SHORT).show();
                            }
                        });
                        Log.d("OCRImpl", "accessToken:" + accessToken);
                    }

                    @Override
                    public void onError(OCRError ocrError) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "初始化认证失败,请检查 key", Toast.LENGTH_SHORT).show();
                            }
                        });
                        Log.d("OCRImpl", "ocrError:" + ocrError);
                    }
                }, context, "Gc2eHOea6ZKTuGGsWsQLvaDP", "7ZVrz0ANm0kwAoCCM5KH8GO1L9ccbhiU");
    }

    public void idCard(IWebview pWebview, JSONArray array) {
        callbackId = array.optString(0);
        String type = array.optString(1);
        this.pWebview = pWebview;
        Intent intent = new Intent(pWebview.getActivity().getApplication(), CameraActivity.class);
        intent.putExtra(CameraActivity.KEY_OUTPUT_FILE_PATH,
                getSaveFile(getDPluginContext()).getAbsolutePath());
        intent.putExtra(CameraActivity.KEY_NATIVE_ENABLE, true);
        String CONTENT_TYPE = CameraActivity.CONTENT_TYPE_ID_CARD_FRONT;
        if (type.equals("back")) {
            CONTENT_TYPE = CameraActivity.CONTENT_TYPE_ID_CARD_BACK;
        }
        intent.putExtra(CameraActivity.KEY_CONTENT_TYPE, CONTENT_TYPE);
        intent.putExtra(CameraActivity.KEY_NATIVE_MANUAL, true);
        pWebview.getActivity().startActivityForResult(intent, REQUEST_CODE_CAMERA);
        final IApp iApp = pWebview.obtainFrameView().obtainApp();
        iApp.registerSysEventListener(new ISysEventListener() {
            @Override
            public boolean onExecute(SysEventType sysEventType, Object o) {
                Object[] _args = (Object[]) o;
                int requestCode = (Integer) _args[0];
                int resultCode = (Integer) _args[1];
                Intent data = (Intent) _args[2];
                _onActivityResult(requestCode, resultCode, data);
                if (sysEventType == SysEventType.onActivityResult) {
                    iApp.unregisterSysEventListener(this, SysEventType.onActivityResult);
                }
                return false;
            }
        }, SysEventType.onActivityResult);
    }


    private File getSaveFile(Context context) {
        return new File(context.getFilesDir(), "pic.jpg");
    }


    private void _onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String contentType = data.getStringExtra(CameraActivity.KEY_CONTENT_TYPE);
                String filePath = getSaveFile(getDPluginContext()).getAbsolutePath();
                if (!TextUtils.isEmpty(contentType)) {
                    if (CameraActivity.CONTENT_TYPE_ID_CARD_FRONT.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_FRONT, filePath);
                    } else if (CameraActivity.CONTENT_TYPE_ID_CARD_BACK.equals(contentType)) {
                        recIDCard(IDCardParams.ID_CARD_SIDE_BACK, filePath);
                    }
                }
            }
        }
    }

    private void recIDCard(String idCardSide, String filePath) {
        IDCardParams param = new IDCardParams();
        param.setImageFile(new File(filePath));
        param.setIdCardSide(idCardSide);
        param.setDetectDirection(true);
        OCR.getInstance(getDPluginContext()).recognizeIDCard(param, new OnResultListener<IDCardResult>() {
            @Override
            public void onResult(IDCardResult result) {
                if (result != null) {
                    Log.d("onResult", "result: " + result.toString());
                    JSUtil.execCallback(pWebview, callbackId, result.getJsonRes(), JSUtil.OK, false);
                }
            }

            @Override
            public void onError(OCRError error) {
                Log.d("onError", "error: " + error.getMessage());
            }
        });
    }
}
