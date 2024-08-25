package com.fk.smartpixelsposed;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.android.systemui.smartpixels.SmartPixelsService;

public class SmartPixelsTile extends TileService {
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private int getTileState() {
        boolean enabled = SafeValueGetter.getEnabled(this);
        return enabled
                ? Tile.STATE_ACTIVE
                : Tile.STATE_INACTIVE;
    }

    private void setTileState() {
        getQsTile().setState(getTileState());
        getQsTile().updateTile();
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        setTileState();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        setTileState();
    }

    @Override
    public void onClick() {
        super.onClick();

        Intent refreshIntent = new Intent(SmartPixelsService.INTENT_ACTION);
        refreshIntent.putExtra(
                SettingsSystem.SMART_PIXELS_ENABLED,
                !SafeValueGetter.getEnabled(this) ? 1 : 0
        );
        sendBroadcast(refreshIntent);

        mHandler.postDelayed(this::setTileState, 50);
    }
}
