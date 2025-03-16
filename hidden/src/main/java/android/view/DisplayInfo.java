// Copyright (C) 2025 Furkan Karcıoğlu <https://github.com/frknkrc44>
//
// This file is part of DisplayManager project,
// and licensed under GNU Affero General Public License v3.
// See the GNU Affero General Public License for more details.
//
// All rights reserved. See COPYING, AUTHORS.
//

package android.view;

import android.os.Parcel;
import android.os.Parcelable;

public class DisplayInfo implements Parcelable {
    /**
     * The state of the display, such as {@link android.view.Display#STATE_ON}.
     */
    public int state;

    /**
     * The current committed state of the display. For example, this becomes
     * {@link android.view.Display#STATE_ON} only after the power state ON is fully committed.
     */
    public int committedState;

    public static final Parcelable.Creator<DisplayInfo> CREATOR = new Parcelable.Creator<>() {
        @Override
        public DisplayInfo createFromParcel(Parcel source) {
            return new DisplayInfo(source);
        }

        @Override
        public DisplayInfo[] newArray(int size) {
            return new DisplayInfo[size];
        }
    };

    public DisplayInfo(Parcel in) {
        throw new RuntimeException("Stub!");
    }

    @Override
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }
}
