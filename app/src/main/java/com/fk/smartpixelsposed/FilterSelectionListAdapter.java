package com.fk.smartpixelsposed;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;

import com.android.systemui.smartpixels.Grids;
import com.android.systemui.smartpixels.SmartPixelsService;

@SuppressLint("ViewHolder")
public class FilterSelectionListAdapter extends BaseAdapter {
    private final int mCurrentValue;
    private final String[] mObjects;

    public FilterSelectionListAdapter(int currentValue, String[] objects) {
        super();
        mCurrentValue = currentValue;
        mObjects = objects;
    }

    @Override
    public int getCount() {
        return mObjects.length;
    }

    @Override
    public String getItem(int position) {
        return mObjects[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        String item = getItem(position);

        LinearLayout mainView = new LinearLayout(parent.getContext());
        mainView.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));

        CheckedTextView superView = (CheckedTextView) LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_single_choice, parent, false);
        superView.setChecked(mCurrentValue == position);
        superView.setText(item);
        superView.setLayoutParams(new LinearLayout.LayoutParams(-1, -2, 1));

        View drawView = new View(parent.getContext());
        int drawSize = (int) (parent.getResources().getDisplayMetrics().density * 64);
        drawView.setLayoutParams(new LinearLayout.LayoutParams(drawSize, drawSize, 0));

        Bitmap bmp = Bitmap.createBitmap(Grids.GridSideSize, Grids.GridSideSize, Bitmap.Config.ARGB_4444);
        BitmapDrawable draw = new BitmapDrawable(parent.getResources(), bmp);
        draw.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        draw.setFilterBitmap(false);
        draw.setAntiAlias(false);
        draw.setTargetDensity(parent.getResources().getDisplayMetrics().densityDpi);
        SmartPixelsService.drawPattern(draw.getBitmap(), position, 0, 0);

        drawView.setBackgroundColor(Color.WHITE);
        drawView.setForeground(draw);

        mainView.addView(drawView);
        mainView.addView(superView);

        return mainView;
    }
}
