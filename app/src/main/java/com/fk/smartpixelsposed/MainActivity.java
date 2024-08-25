package com.fk.smartpixelsposed;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import com.android.systemui.smartpixels.SmartPixelsService;

public class MainActivity extends Activity {
    private int dimPercent;
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
        boolean enabled = SafeValueGetter.getEnabled(this);
        Switch enableItem = findViewById(R.id.settings_enabled);
        enableItem.setChecked(enabled);
        enableItem.setOnCheckedChangeListener((v, checked) -> onOK(
                SettingsSystem.SMART_PIXELS_ENABLED,
                checked ? 1 : 0
        ));

        dimPercent = SafeValueGetter.getDimPercent(this);
        View dimView = findViewById(R.id.settings_dim);
        TextView textView = dimView.findViewById(android.R.id.text1);
        textView.setText(String.format("%s%%", dimPercent));

        SeekBar seekBar = dimView.findViewById(android.R.id.input);
        seekBar.setProgress(dimPercent);
        seekBar.setMax(SafeValueGetter.DIM_PERCENT_MAX);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                dimPercent = seekBar.getProgress();
                textView.setText(String.format("%s%%", dimPercent));
                onOK(SettingsSystem.SMART_PIXELS_DIM, dimPercent);
            }
        });

        int pattern = SafeValueGetter.getPattern(this);
        String patternStr = percentStrs[pattern];
        TwoLineListItem patternItem = findViewById(R.id.settings_pattern);
        patternItem.getText1().setText(R.string.smart_pixels_percent);
        patternItem.getText2().setText(patternStr);
        patternItem.setOnClickListener(v -> openSelectorDialog(
                SettingsSystem.SMART_PIXELS_PATTERN,
                patternItem.getText1().getText(),
                pattern,
                percentStrs
        ));

        int timeout = SafeValueGetter.getShiftTimeout(this);
        TwoLineListItem shiftTimeoutItem = findViewById(R.id.settings_shift_timeout);
        shiftTimeoutItem.getText1().setText(R.string.smart_pixels_shift_title);
        shiftTimeoutItem.getText2().setText(R.string.smart_pixels_shift_summary);
        shiftTimeoutItem.setOnClickListener(v -> openSelectorDialog(
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

    private void openSelectorDialog(String key, CharSequence title, int currentValue, String[] items) {
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
