package com.fk.smartpixelsposed;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.android.systemui.smartpixels.SmartPixelsService;

public class MainActivity extends Activity {
    private int dimPercent;
    private int alphaPercent;
    private int pattern;
    private int timeout;
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

        boolean enabledOnPowerSave = SafeValueGetter.getEnabledOnPowerSaver(this);
        Switch enableOnPowerSaveItem = findViewById(R.id.settings_enabled_on_power_saver);
        enableOnPowerSaveItem.setChecked(enabledOnPowerSave);
        enableOnPowerSaveItem.setOnCheckedChangeListener((v, checked) -> onOK(
                SettingsSystem.SMART_PIXELS_ON_POWER_SAVE,
                checked ? 1 : 0
        ));

        boolean enabledSystemBarsShift = SafeValueGetter.getSystemBarsShiftEnabled(this);
        Switch enabledSystemBarsShiftItem = findViewById(R.id.settings_enabled_system_bars_shifting);
        enabledSystemBarsShiftItem.setChecked(enabledSystemBarsShift);
        enabledSystemBarsShiftItem.setOnCheckedChangeListener((v, checked) -> onOK(
                SettingsSystem.SMART_PIXELS_SYSTEM_BARS_SHIFT,
                checked ? 1 : 0
        ));

        boolean enabledDimDrag = SafeValueGetter.isSetDimOnSBDragEnabled(this);
        Switch enabledDimDragItem = findViewById(R.id.settings_enabled_set_dim_on_sb_drag);
        enabledDimDragItem.setChecked(enabledDimDrag);
        enabledDimDragItem.setOnCheckedChangeListener((v, checked) -> onOK(
                SettingsSystem.SMART_PIXELS_DIM_DRAG,
                checked ? 1 : 0
        ));

        dimPercent = SafeValueGetter.getDimPercent(this);
        View dimView = findViewById(R.id.settings_dim);

        TextView percentTransTitle = dimView.findViewById(android.R.id.text1);
        percentTransTitle.setText(R.string.smart_pixels_dim_percent);

        TextView percentTransText = dimView.findViewById(android.R.id.text2);
        percentTransText.setText(String.format("%s%%", dimPercent));

        SeekBar percentDimSeekBar = dimView.findViewById(android.R.id.input);
        percentDimSeekBar.setProgress(dimPercent);
        percentDimSeekBar.setMax(SafeValueGetter.DIM_PERCENT_MAX);
        percentDimSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                dimPercent = seekBar.getProgress();
                percentTransText.setText(String.format("%s%%", dimPercent));
                onOK(SettingsSystem.SMART_PIXELS_DIM, dimPercent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        alphaPercent = SafeValueGetter.getBarsAlphaPercent(this);
        View alphaView = findViewById(R.id.settings_bars_alpha);

        TextView percentAlphaTitle = alphaView.findViewById(android.R.id.text1);
        percentAlphaTitle.setText(R.string.smart_pixels_alpha_bars);

        TextView percentAlphaText = alphaView.findViewById(android.R.id.text2);
        percentAlphaText.setText(String.format("%s%%", alphaPercent));

        SeekBar percentAlphaSeekBar = alphaView.findViewById(android.R.id.input);
        percentAlphaSeekBar.setEnabled(SafeValueGetter.getUsingAltLogic(this) < 1);
        percentAlphaSeekBar.setProgress(alphaPercent);
        percentAlphaSeekBar.setMax(SafeValueGetter.BARS_PERCENT_MAX);
        percentAlphaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                alphaPercent = seekBar.getProgress();
                percentAlphaText.setText(String.format("%s%%", alphaPercent));
                onOK(SettingsSystem.SMART_PIXELS_BARS_ALPHA, alphaPercent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        pattern = SafeValueGetter.getPattern(this);
        ViewGroup percentDisableView = findViewById(R.id.settings_pattern);

        TextView percentDisableTitle = percentDisableView.findViewById(android.R.id.text1);
        percentDisableTitle.setText(R.string.smart_pixels_percent);

        TextView percentDisableText = percentDisableView.findViewById(android.R.id.text2);
        percentDisableText.setText(percentStrs[pattern]);

        SeekBar percentDisableSeekBar = percentDisableView.findViewById(android.R.id.input);
        percentDisableSeekBar.setProgress(pattern);
        percentDisableSeekBar.setMax(percentStrs.length - 1);
        percentDisableSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pattern = seekBar.getProgress();
                percentDisableText.setText(percentStrs[pattern]);
                onOK(SettingsSystem.SMART_PIXELS_PATTERN, pattern);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        timeout = SafeValueGetter.getShiftTimeout(this);
        ViewGroup percentTimeoutView = findViewById(R.id.settings_shift_timeout);

        TextView percentTimeoutTitle = percentTimeoutView.findViewById(android.R.id.text1);
        percentTimeoutTitle.setText(R.string.smart_pixels_shift_title);

        TextView percentTimeoutText = percentTimeoutView.findViewById(android.R.id.text2);
        percentTimeoutText.setText(shiftStrs[timeout]);

        if (percentTimeoutView.findViewById(android.R.id.summary) == null) {
            TextView percentTimeoutSummary = new TextView(this);
            percentTimeoutSummary.setId(android.R.id.summary);
            percentTimeoutSummary.setTextAppearance(AttrUtils.getResourceFromAttr(this, android.R.attr.textAppearanceListItemSecondary));
            percentTimeoutSummary.setText(R.string.smart_pixels_shift_summary);
            percentTimeoutView.addView(percentTimeoutSummary, 1);
        }

        SeekBar percentTimeoutSeekBar = percentTimeoutView.findViewById(android.R.id.input);
        percentTimeoutSeekBar.setProgress(timeout);
        percentTimeoutSeekBar.setMax(shiftStrs.length - 1);
        percentTimeoutSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timeout = seekBar.getProgress();
                percentTimeoutText.setText(shiftStrs[timeout]);
                onOK(SettingsSystem.SMART_PIXELS_SHIFT_TIMEOUT, timeout);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void onOK(String key, int value) {
        Intent refreshIntent = new Intent(SmartPixelsService.INTENT_ACTION);
        refreshIntent.putExtra(key, value);
        sendBroadcast(refreshIntent);
    }
}
