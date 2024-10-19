package com.fk.smartpixelsposed;

import android.content.Context;
import android.content.res.TypedArray;

public class AttrUtils {
    private AttrUtils() {}

    public static int getResourceFromAttr(Context context, int attrId) {
        try (TypedArray typedArray = context.obtainStyledAttributes(new int[]{attrId})) {
            return typedArray.getResourceId(0, android.R.style.TextAppearance_DeviceDefault_Small);
        }
    }
}
