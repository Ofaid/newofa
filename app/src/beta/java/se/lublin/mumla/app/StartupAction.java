package ofaid.ahmad.ptt.app;

import static ofaid.ahmad.ptt.app.DialogUtils.maybeShowNewsDialog;

import android.app.Activity;

import androidx.annotation.NonNull;

public class StartupAction implements IStartupAction {
    @Override
    public void execute(@NonNull Activity activity) {
        maybeShowNewsDialog(activity);
    }
}
