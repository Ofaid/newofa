package ofaid.ahmad.ptt.preference;

import android.os.Bundle;
import ofaid.ahmad.ptt.R;

public class AuthenticationSettingsFragment extends MumlaPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.settings_authentication, rootKey);
    }
}
