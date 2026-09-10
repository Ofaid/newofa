/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ofaid.ahmad.ptt.servers;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mumla.ofa.model.OfaUserId;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import se.lublin.humla.model.Server;
import ofaid.ahmad.ptt.R;
import ofaid.ahmad.ptt.Settings;

public class ServerEditFragment extends DialogFragment {
    private static final String ARGUMENT_SERVER = "server";
    private static final String ARGUMENT_ACTION = "action";
    private static final String ARGUMENT_IGNORE_TITLE = "ignore_title";

    private static final String PREF_OFA_SERVER = "OfaServerPrefs";

    // 📍 ALAMAT DAFTAR SERVER KITA — SATU-SATUNYA SUMBER DATA!
    private static final String OFA_DAFTAR_SERVER_URL = "https://jz13gri.liveblog365.com/server/daftar.cgi";

    private EditText mNameEdit;
    private EditText mHostEdit;
    private EditText mPortEdit;
    private EditText mUsernameEdit;
    private EditText mPasswordEdit;

    private ServerEditListener mListener;

    // 📦 PENAMPUNG DATA SENDIRI — TIDAK BERGANTUNG SISTEM LAIN!
    static class OfaServerItem {
        final String name;
        final String ip;
        final int port;
        final String country;
        final String region;

        OfaServerItem(String name, String ip, int port, String country, String region) {
            this.name = name;
            this.ip = ip;
            this.port = port;
            this.country = country;
            this.region = region;
        }

        public String getName() { return name; }
        public String getIp() { return ip; }
        public int getPort() { return port; }
    }

    private List<OfaServerItem> mDaftarServerWeb;
    private ArrayAdapter<String> mAdapterDaftar;

    public static DialogFragment createServerEditDialog(Context context, Server server,
                                                        Action action,
                                                        boolean ignoreTitle) {
        Bundle args = new Bundle();
        args.putParcelable(ARGUMENT_SERVER, server);
        args.putInt(ARGUMENT_ACTION, action.ordinal());
        args.putBoolean(ARGUMENT_IGNORE_TITLE, ignoreTitle);
        return (DialogFragment) Fragment.instantiate(context, ServerEditFragment.class.getName(), args);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (ServerEditListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ServerEditListener!");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AlertDialog)getDialog()).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (validate()) {
                    Server server = createServer();
                    mListener.onServerEdited(getAction(), server);
                    dismiss();
                }
            }
        });
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Settings settings = Settings.getInstance(getActivity());

        String actionName;
        switch (getAction()) {
            case ADD_ACTION:
                actionName = getString(R.string.add);
                break;
            case EDIT_ACTION:
                actionName = getString(android.R.string.ok);
                break;
            case CONNECT_ACTION:
                actionName = getString(R.string.connect);
                break;
            default:
                throw new RuntimeException("Unknown action " + getAction());
        }

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.dialog_server_edit, null, false);

        // 📋 TAMBAHKAN DAFTAR PILIHAN SERVER DI ATAS FORMULIR
        LinearLayout rootLayout = (LinearLayout) view.getParent();
        LinearLayout panelDaftar = new LinearLayout(requireActivity());
        panelDaftar.setOrientation(LinearLayout.VERTICAL);
        panelDaftar.setPadding(24, 16, 24, 8);

        TextView judulDaftar = new TextView(requireActivity());
        judulDaftar.setText("📋 Pilih Server — klik untuk isi otomatis:");
        judulDaftar.setTextSize(15);
        judulDaftar.setTextColor(0xFF666666);
        judulDaftar.setPadding(0, 0, 0, 8);
        panelDaftar.addView(judulDaftar);

        ListView daftarPilihan = new ListView(requireActivity());
        daftarPilihan.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        daftarPilihan.setDividerHeight(8);
        panelDaftar.addView(daftarPilihan);

        if (rootLayout != null && rootLayout.getChildCount() > 0) {
            rootLayout.addView(panelDaftar, 0);
        }

        // Inisialisasi daftar & pemuat — MURNI DARI ALAMAT KITA SENDIRI!
        mDaftarServerWeb = new ArrayList<>();
        mAdapterDaftar = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_list_item_1, new ArrayList<>());
        daftarPilihan.setAdapter(mAdapterDaftar);
        muatDaftarServerDariWeb();

        // ✅ KLIK → ISI ALAMAT & PORT OTOMATIS — NAMA PENGGUNA TETAP DIISI TANGAN!
        daftarPilihan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int posisi, long id) {
                OfaServerItem serverTerpilih = mDaftarServerWeb.get(posisi);
                mHostEdit.setText(serverTerpilih.getIp());
                mPortEdit.setText(String.valueOf(serverTerpilih.getPort()));
                if (mNameEdit.getText().length() == 0) {
                    mNameEdit.setText(serverTerpilih.getName());
                }
            }
        });

        TextView titleLabel = view.findViewById(R.id.server_edit_name_title);
        mNameEdit = view.findViewById(R.id.server_edit_name);
        mHostEdit = view.findViewById(R.id.server_edit_host);
        mPortEdit = view.findViewById(R.id.server_edit_port);
        mUsernameEdit = view.findViewById(R.id.server_edit_username);
        mUsernameEdit.setHint(settings.getDefaultUsername());
        mPasswordEdit = view.findViewById(R.id.server_edit_password);

        Server oldServer = getServer();
        if (oldServer != null) {
            mNameEdit.setText(oldServer.getName());
            mHostEdit.setText(oldServer.getHost());
            if (oldServer.getPort() != 0) {
                mPortEdit.setText(String.valueOf(oldServer.getPort()));
            }
            mUsernameEdit.setText(oldServer.getUsername());
            mPasswordEdit.setText(oldServer.getPassword());
        }

        if (shouldIgnoreTitle()) {
            titleLabel.setVisibility(View.GONE);
            mNameEdit.setVisibility(View.GONE);
        }

        return new MaterialAlertDialogBuilder(requireActivity())
                .setPositiveButton(actionName, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setView(view)
                .create();
    }

    // 🌐 MUAT & URAI XML — SENDIRI, PERSIS FORMAT KAU! TIDAK PAKAI KODE SISTEM LAIN!
    private void muatDaftarServerDariWeb() {
        new MuatDaftarServerWebTask(this).execute();
    }

    private static class MuatDaftarServerWebTask extends AsyncTask<Void, Void, List<OfaServerItem>> {
        private final WeakReference<ServerEditFragment> fragRef;

        MuatDaftarServerWebTask(ServerEditFragment frag) {
            fragRef = new WeakReference<>(frag);
        }

        @Override
        protected List<OfaServerItem> doInBackground(Void... nada) {
            ServerEditFragment frag = fragRef.get();
            if (frag == null || !frag.isAdded()) return null;
            List<OfaServerItem> hasil = new ArrayList<>();
            try {
                URL url = new URL(OFA_DAFTAR_SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.addRequestProperty("version", se.lublin.humla.Constants.PROTOCOL_STRING);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.connect();
                InputStream aliran = conn.getInputStream();

                XmlPullParser parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(aliran, "UTF-8");
                parser.nextTag();

                parser.require(XmlPullParser.START_TAG, null, "servers");
                int eventType;
                while ((eventType = parser.next()) != XmlPullParser.END_TAG) {
                    if (eventType != XmlPullParser.START_TAG) continue;
                    if ("server".equals(parser.getName())) {
                        // ✅ BACA PERSIS ATRIBUT XML YANG KAU BUAT — TIDAK ADA YANG DIUBAH!
                        String nama = parser.getAttributeValue(null, "name");
                        String ip = parser.getAttributeValue(null, "ip");
                        String portStr = parser.getAttributeValue(null, "port");
                        String negara = parser.getAttributeValue(null, "country");
                        String wilayah = parser.getAttributeValue(null, "region");

                        int port = 64738;
                        try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}

                        hasil.add(new OfaServerItem(nama, ip, port, negara, wilayah));
                        parser.nextTag();
                    } else {
                        // ✅ Lewati tag lain dengan aman — cara kompatibel SEMUA versi Android!
                        int kedalaman = 1;
                        while (kedalaman > 0 && (eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) kedalaman++;
                            else if (eventType == XmlPullParser.END_TAG) kedalaman--;
                        }
                    }
                }
                parser.require(XmlPullParser.END_TAG, null, "servers");
                aliran.close();
                conn.disconnect();
            } catch (MalformedURLException e) {}
            catch (ProtocolException e) {}
            catch (XmlPullParserException e) {}
            catch (IOException e) {}
            catch (Exception e) { e.printStackTrace(); }
            return hasil;
        }

        @Override
        protected void onPostExecute(List<OfaServerItem> daftar) {
            ServerEditFragment frag = fragRef.get();
            if (frag == null || !frag.isAdded()) return;
            frag.mDaftarServerWeb.clear();
            if (daftar != null) frag.mDaftarServerWeb.addAll(daftar);
            frag.mAdapterDaftar.clear();
            for (OfaServerItem s : frag.mDaftarServerWeb) {
                frag.mAdapterDaftar.add(s.getName() + " — " + s.getIp() + ":" + s.getPort());
            }
            frag.mAdapterDaftar.notifyDataSetChanged();
        }
    }

    // ⚠️ DI BAWAH INI SEMUA KODE ASLI — TIDAK DIUBAH SATU PUN! 🛡️
    public Server createServer() {
        String name = (mNameEdit).getText().toString().trim();
        String host = (mHostEdit).getText().toString().trim();

        int port;
        try {
            port = Integer.parseInt((mPortEdit).getText().toString());
        } catch (final NumberFormatException ex) {
            port = 0;
        }

        String username = (mUsernameEdit).getText().toString().trim();
        String password = mPasswordEdit.getText().toString();

        if (username.equals(""))
            username = mUsernameEdit.getHint().toString();

        Context context = requireActivity();
        String ofaPatenId = OfaUserId.getPermanentUserId(context);
        String storageKey = "ofa_paten_id_" + host + "_" + port;
        SharedPreferences sp = context.getSharedPreferences(PREF_OFA_SERVER, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putString(storageKey, ofaPatenId);
        ed.apply();

        long id;
        if (getServer() != null) {
            id = getServer().getId();
        } else {
            id = -1;
        }

        return new Server(id, name, host, port, username, password);
    }

    public boolean validate() {
        if (mHostEdit.getText().length() == 0) {
            mHostEdit.setError(getString(R.string.invalid_host));
            return false;
        } else if (mPortEdit.getText().length() > 0) {
            try {
                int port = Integer.parseInt(mPortEdit.getText().toString());
                if (port < 1 || port > 65535) {
                    mPortEdit.setError(getString(R.string.invalid_port_range));
                    return false;
                }
            } catch (NumberFormatException nfe) {
                mPortEdit.setError(getString(R.string.invalid_port_range));
                return false;
            }
        }
        return true;
    }

    private Server getServer() {
        return getArguments().getParcelable(ARGUMENT_SERVER);
    }

    private Action getAction() {
        return Action.values()[getArguments().getInt(ARGUMENT_ACTION)];
    }

    private boolean shouldIgnoreTitle() {
        return getArguments().getBoolean(ARGUMENT_IGNORE_TITLE);
    }

    public interface ServerEditListener {
        void onServerEdited(Action action, Server server);
    }

    public enum Action {
        CONNECT_ACTION,
        EDIT_ACTION,
        ADD_ACTION
    }
}
