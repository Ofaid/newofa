package ofaid.ahmad.ptt.servers;

import android.app.Activity;
import android.util.Log;
import android.webkit.JavascriptInterface;

import java.util.List;
import se.lublin.humla.model.Server;
import ofaid.ahmad.ptt.db.DatabaseProvider;

public class OfaWebInterface {
    private static final String TAG = "OfaWebInterface";
    private final Activity mActivity;
    private final FavouriteServerListFragment mFragment;
    private final DatabaseProvider mDatabaseProvider;

    public OfaWebInterface(Activity activity, FavouriteServerListFragment fragment) {
        this.mActivity = activity;
        this.mFragment = fragment;
        this.mDatabaseProvider = (DatabaseProvider) activity;
    }

    @JavascriptInterface
    public void showToast(String message) {
        mActivity.runOnUiThread(() ->
            android.widget.Toast.makeText(mActivity, message, android.widget.Toast.LENGTH_LONG).show()
        );
    }

    @JavascriptInterface
    public void connectToServer(String host, int port) {
        Log.d(TAG, "📥 DARI HTML — Host: " + host + ", Port: " + port);

        List<Server> servers = mDatabaseProvider.getDatabase().getServers();
        Server targetServer = null;

        for (Server s : servers) {
            if (s.getHost().equals(host) && s.getPort() == port) {
                targetServer = s;
                Log.d(TAG, "✅ Server ditemukan di database!");
                break;
            }
        }

        if (targetServer == null) {
            targetServer = new Server(
                    0,
                    host,
                    host,
                    port,
                    "",
                    ""
            );
            Log.d(TAG, "⚠️ Server belum tersimpan — buat objek sementara");
        }

        // ==================================================
        // ✅ LANGSUNG KE MUMLAACTIVITY — PASTI SAMPAI!
        // ==================================================
        if (mActivity instanceof ofaid.ahmad.ptt.app.MumlaActivity) {
            ((ofaid.ahmad.ptt.app.MumlaActivity) mActivity).connectToServer(targetServer);
            Log.d(TAG, "✅ BERHASIL — Sertifikat akan muncul sekarang!");
        } else {
            Log.e(TAG, "❌ Bukan MumlaActivity! Class: " + mActivity.getClass().getName());
        }
    }
}
