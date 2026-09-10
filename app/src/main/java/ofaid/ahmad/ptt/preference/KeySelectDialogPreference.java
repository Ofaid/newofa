package ofaid.ahmad.ptt.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import ofaid.ahmad.ptt.R;

public class KeySelectDialogPreference extends DialogPreference {
    public KeySelectDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_keyselect_preference);
    }
}
