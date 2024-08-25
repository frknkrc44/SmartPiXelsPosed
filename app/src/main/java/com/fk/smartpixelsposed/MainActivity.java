package com.fk.smartpixelsposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Switch;
import android.widget.TwoLineListItem;

import com.android.systemui.smartpixels.SmartPixelsService;

public class MainActivity extends Activity {
    private boolean enabled;
    private String[] percentStrs;
    private String[] shiftStrs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        percentStrs = getResources().getStringArray(R.array.smart_pixels_percent_strings);
        shiftStrs = getResources().getStringArray(R.array.smart_pixels_shift_times);

        setContentView(R.layout.activity_main);

        setValues();
    }

    @Override
    protected void onResume() {
        super.onResume();

        handler.postDelayed(this::setValues, 200);
    }

    private void setValues() {
        enabled = SafeValueGetter.getEnabled(this);
        Switch enableItem = findViewById(R.id.settings_enabled);
        enableItem.setChecked(enabled);
        enableItem.setOnCheckedChangeListener((v, checked) -> onOK(
                SettingsSystem.SMART_PIXELS_ENABLED,
                checked ? 1 : 0
        ));

        int pattern = SafeValueGetter.getPattern(this);
        String patternStr = percentStrs[pattern];
        TwoLineListItem patternItem = findViewById(R.id.settings_pattern);
        patternItem.getText1().setText(R.string.smart_pixels_percent);
        patternItem.getText2().setText(patternStr);
        patternItem.setOnClickListener(v -> openDialog(
                SettingsSystem.SMART_PIXELS_PATTERN,
                patternItem.getText1().getText(),
                pattern,
                percentStrs
        ));

        int timeout = SafeValueGetter.getShiftTimeout(this);
        TwoLineListItem shiftTimeoutItem = findViewById(R.id.settings_shift_timeout);
        shiftTimeoutItem.getText1().setText(R.string.smart_pixels_shift_title);
        shiftTimeoutItem.getText2().setText(R.string.smart_pixels_shift_summary);
        shiftTimeoutItem.setOnClickListener(v -> openDialog(
                SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT,
                shiftTimeoutItem.getText1().getText(),
                timeout,
                shiftStrs
        ));
    }

    private void onOK(String key, int value) {
        Intent refreshIntent = new Intent(SmartPixelsService.INTENT_ACTION);
        refreshIntent.putExtra(key, value);
        sendBroadcast(refreshIntent);
    }

    private void openDialog(String key, CharSequence title, int currentValue, String[] items) {
        ArrayAdapter<String> dialogAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_single_choice, items);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(
                        dialogAdapter,
                        currentValue,
                        (v, value) -> {
                            v.dismiss();
                            onOK(key, value);
                        }
                )
                .setOnDismissListener((v) -> handler.postDelayed(this::setValues, 200))
                .show();
    }
}
