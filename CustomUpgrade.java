package com.cvte.tv.api;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import com.cvte.tv.api.at.api.SysAPI;
import com.cvte.tv.api.impl.C0118R;
import com.cvte.tv.tvwebclient.WebProxyConstant;
import com.cvte.tv.util.FileUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hisilicon.android.tvapi.HitvManager;
import java.io.File;

public class CustomUpgrade {
    private static final String CFG_BOOTANIM = "burn-bootanim";
    private static final String CFG_BURN_INI = "burn-ini";
    private static final String CFG_BURN_PQ = "burn-pq";
    private static final String CFG_EXPORT_INI = "export-ini";
    private static final String CFG_EXPORT_LOG = "export-log";
    private static final String CFG_EXPORT_PQ = "export-pq";
    private static final String CFG_KEY_XML = "burn-key-xml";
    private static final String CFG_LOGO = "burn-logo";
    private static final String CFG_PANELPARAM = "burn-panelparam";
    private static final String CFG_PANELPIN15 = "panelPin15";
    private static final String CFG_PANELPIN16 = "panelPin16";
    private static final String CFG_PANELPIN22 = "panelPin22";
    private static final String CFG_PANELPINTYPE = "panelPinType";
    private static final int CFG_VALID_FLAG = 1;
    private static final String DIR_NAME = "custom_upgrade";
    private static final String DIR_NAME_PQ = "pq";
    private static final String ENABLE_PROP = "1";
    private static final String FILE_NAME_BOOTANIM = "bootanimation.zip";
    private static final String FILE_NAME_CONFIG = "custom_upgrade_cfg.json";
    private static final String FILE_NAME_INI = "cvte_yk06_v600.ini";
    private static final String FILE_NAME_KEY_XML = "key.xml";
    private static final String FILE_NAME_LOGO = "logo.img";
    private static final String FILE_NAME_PANEL = "panel.img";
    private static final String FILE_NAME_PIN = "custom_upgrade_pin.json";
    private static final int GPIO_HIGH = 1;
    private static final int GPIO_INPUT = 2;
    private static final int GPIO_LOW = 0;
    private static final String PROP_BOOTANIM = "sys.cvt.burn-bootanim";
    private static final String PROP_BURN_INI = "sys.cvt.burn-ini";
    private static final String PROP_BURN_PQ = "sys.cvt.burn-pq";
    private static final String PROP_EXPORT_INI = "sys.cvt.export-ini";
    private static final String PROP_EXPORT_LOG = "sys.cvt.export-log";
    private static final String PROP_EXPORT_PQ = "sys.cvt.export-pq";
    private static final String PROP_KEY_XML = "sys.cvt.burn-key-xml";
    private static final String PROP_LOGO = "sys.cvt.burn-logo";
    private static final String PROP_PANELPARAM = "sys.cvt.burn-panelparam";
    private static final String TAG = "CustomUpgrade";
    private static CustomUpgrade instance;
    private String mCfgDirPath;
    private Context mContext;

    public static CustomUpgrade getInstance() {
        if (instance == null) {
            instance = new CustomUpgrade();
        }
        return instance;
    }

    public static CustomUpgrade getInstanceValue() {
        return instance;
    }

    private CustomUpgrade() {
    }

    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        String volumnPath = intent.getDataString();
        String action = intent.getAction();
        boolean isMounted = false;
        if (action.equals("android.intent.action.MEDIA_MOUNTED")) {
            isMounted = true;
        } else if (action.equals("android.intent.action.MEDIA_EJECT") || action.equals("android.intent.action.MEDIA_UNMOUNTED")) {
            isMounted = false;
        }
        Log.i(TAG, "volumnPath=" + volumnPath + "  mount=" + isMounted);
        if (isMounted && volumnPath != null && volumnPath.startsWith("file://")) {
            this.mCfgDirPath = volumnPath.substring("file://".length()) + WebProxyConstant.URL_WEBSITE + DIR_NAME;
            JsonObject jsonObjOfCfg = getJsonObjectFromFilePath(this.mCfgDirPath + WebProxyConstant.URL_WEBSITE + FILE_NAME_CONFIG);
            if (jsonObjOfCfg != null) {
                Log.i(TAG, "jsonObjOfCfg:" + jsonObjOfCfg);
                JsonElement jeBootAnim = jsonObjOfCfg.get(CFG_BOOTANIM);
                JsonElement jeLogo = jsonObjOfCfg.get(CFG_LOGO);
                JsonElement jePanel = jsonObjOfCfg.get(CFG_PANELPARAM);
                JsonElement jeKeyXml = jsonObjOfCfg.get(CFG_KEY_XML);
                JsonElement jeExportLog = jsonObjOfCfg.get(CFG_EXPORT_LOG);
                JsonElement jeBurnINI = jsonObjOfCfg.get(CFG_BURN_INI);
                JsonElement jeBurnPQ = jsonObjOfCfg.get(CFG_BURN_PQ);
                JsonElement jeExportINI = jsonObjOfCfg.get(CFG_EXPORT_INI);
                JsonElement jeExportPQ = jsonObjOfCfg.get(CFG_EXPORT_PQ);
                if (jeExportINI != null && jeExportINI.getAsInt() == 1 && jeBurnINI != null && jeBurnINI.getAsInt() == 1) {
                    showToast(this.mContext.getResources().getString(C0118R.string.toast_ini_conflict));
                    return;
                } else if (jeBurnPQ == null || jeBurnPQ.getAsInt() != 1 || jeExportPQ == null || jeExportPQ.getAsInt() != 1) {
                    executeTask(jeExportINI, PROP_EXPORT_INI, C0118R.string.toast_export_ini);
                    executeTask(jeExportPQ, PROP_EXPORT_PQ, C0118R.string.toast_export_pq);
                    executeTask(jeBootAnim, PROP_BOOTANIM, C0118R.string.toast_bootanim, FILE_NAME_BOOTANIM);
                    executeTask(jeLogo, PROP_LOGO, C0118R.string.toast_logo, FILE_NAME_LOGO);
                    executeTask(jePanel, PROP_PANELPARAM, C0118R.string.toast_panel, FILE_NAME_PANEL);
                    executeTask(jeKeyXml, PROP_KEY_XML, C0118R.string.toast_keyxml, FILE_NAME_KEY_XML);
                    executeTask(jeBurnINI, PROP_BURN_INI, C0118R.string.toast_burn_ini, FILE_NAME_INI);
                    executeTask(jeBurnPQ, PROP_BURN_PQ, C0118R.string.toast_burn_pq, DIR_NAME_PQ);
                    executeTask(jeExportLog, PROP_EXPORT_LOG, C0118R.string.toast_export_log);
                    showToast(this.mContext.getResources().getString(C0118R.string.toast_upgrade_complete));
                } else {
                    showToast(this.mContext.getResources().getString(C0118R.string.toast_pq_conflict));
                    return;
                }
            }
            JsonObject jsonObjOfPin = getJsonObjectFromFilePath(this.mCfgDirPath + WebProxyConstant.URL_WEBSITE + FILE_NAME_PIN);
            if (jsonObjOfPin != null) {
                Log.i(TAG, "jsonObjOfPin:" + jsonObjOfPin);
                try {
                    setPanelParamByPinType(jsonObjOfPin.get(CFG_PANELPINTYPE).getAsInt(), jsonObjOfPin.get(CFG_PANELPIN22).getAsInt(), jsonObjOfPin.get(CFG_PANELPIN15).getAsInt(), jsonObjOfPin.get(CFG_PANELPIN16).getAsInt());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setPanelParamByPinType(int type, int pin22, int pin15, int pin16) {
        switch (type) {
            case 0:
                HitvManager.getInstance().getCusEx().cusSetPanelPin22Status(pin22);
                HitvManager.getInstance().getCusEx().cusSetPanelPin15Status(pin15);
                HitvManager.getInstance().getCusEx().cusSetPanelPin16Status(pin16);
                return;
            case 1:
                HitvManager.getInstance().getCusEx().cusSetPanelPin22Status(2);
                HitvManager.getInstance().getCusEx().cusSetPanelPin15Status(2);
                HitvManager.getInstance().getCusEx().cusSetPanelPin16Status(2);
                return;
            case 2:
                HitvManager.getInstance().getCusEx().cusSetPanelPin22Status(1);
                HitvManager.getInstance().getCusEx().cusSetPanelPin15Status(2);
                HitvManager.getInstance().getCusEx().cusSetPanelPin16Status(2);
                return;
            case 3:
                HitvManager.getInstance().getCusEx().cusSetPanelPin22Status(0);
                HitvManager.getInstance().getCusEx().cusSetPanelPin15Status(2);
                HitvManager.getInstance().getCusEx().cusSetPanelPin16Status(2);
                return;
            case 4:
                HitvManager.getInstance().getCusEx().cusSetPanelPin22Status(2);
                HitvManager.getInstance().getCusEx().cusSetPanelPin15Status(0);
                HitvManager.getInstance().getCusEx().cusSetPanelPin16Status(1);
                return;
            default:
                return;
        }
    }

    private JsonObject getJsonObjectFromFilePath(String filePath) {
        File file = new File(filePath);
        Log.i(TAG, "filePath=" + filePath + "  file.exists()=" + file.exists());
        if (file.exists()) {
            StringBuilder strBuilder = FileUtils.readFile(filePath, "utf-8");
            if (strBuilder != null) {
                JsonElement jsonElement = new JsonParser().parse(strBuilder.toString());
                if (jsonElement != null && jsonElement.isJsonObject()) {
                    return jsonElement.getAsJsonObject();
                }
            }
        }
        return null;
    }

    private void executeTask(JsonElement cfgJsonElement, String property, int tipId, String fileName) {
        if (cfgJsonElement == null) {
            Log.w(TAG, "executeTask cfgJsonElement is null");
            return;
        }
        Log.i(TAG, "executeTask JsonElement=" + cfgJsonElement + "  cfgJsonElement.getAsInt()=" + cfgJsonElement.getAsInt());
        if (cfgJsonElement != null && cfgJsonElement.getAsInt() == 1) {
            showToast(this.mContext.getResources().getString(tipId));
            File targetFile = new File(this.mCfgDirPath + WebProxyConstant.URL_WEBSITE + fileName);
            Log.i(TAG, "targetFile=" + this.mCfgDirPath + WebProxyConstant.URL_WEBSITE + fileName + "  file.exists()=" + targetFile.exists());
            if (targetFile.exists()) {
                Log.i(TAG, "burn " + targetFile + " start");
                SysAPI.SysProp_Set(property, "1");
                Log.i(TAG, "burn " + targetFile + " end");
            }
        }
    }

    private void executeTask(JsonElement cfgJsonElement, String property, int tipId) {
        if (cfgJsonElement == null) {
            Log.w(TAG, "executeTask cfgJsonElement is null");
            return;
        }
        Log.i(TAG, "executeTask JsonElement=" + cfgJsonElement + "  cfgJsonElement.getAsInt()=" + cfgJsonElement.getAsInt());
        if (cfgJsonElement != null && cfgJsonElement.getAsInt() == 1) {
            showToast(this.mContext.getResources().getString(tipId));
            Log.i(TAG, "export " + property + " start");
            SysAPI.SysProp_Set(property, "1");
            Log.i(TAG, "export " + property + " end");
        }
    }

    private void showToast(String tipStr) {
        Toast.makeText(this.mContext, tipStr, 1).show();
        Log.i(TAG, tipStr);
    }
}
