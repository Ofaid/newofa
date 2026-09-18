/*
 * Copyright (C) 2014 Andrew Comminos
 * Modif By Ofaid/Ahmad 2026 — SERVER TETAP & ID TERKUNCI
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
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

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
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

    // 🔒 SERVER UTAMA — TETAP, TIDAK DIUBAH
    public static final String SERVER_PATEN_HOST = "ahmad.cleanvoice.ru";
    public static final int SERVER_PATEN_PORT = 65202;
    public static final String SERVER_PATEN_NAMA = "OFA PTT";

    // 📍 DAFTAR SERVER TAMBAHAN — SIAP UNTUK NANTI
    private static final String OFA_DAFTAR_SERVER_URL = "https://jz13gri.liveblog365.com/server/daftar.cgi";

    private EditText mNameEdit;
    private EditText mHostEdit;
    private EditText mPortEdit;
    private EditText mUsernameEdit;
    private EditText mPasswordEdit;

    private ServerEditListener mListener;

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
        ((AlertDialog)getDialog()).getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (validate()) {
                Server server = createServer();
                mListener.onServerEdited(getAction(), server);
                dismiss();
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

        // =============================================
        // ✅ INISIALISASI SEMUA KOLOM
        // =============================================
        TextView titleLabel = view.findViewById(R.id.server_edit_name_title);
        mNameEdit = view.findViewById(R.id.server_edit_name);
        mHostEdit = view.findViewById(R.id.server_edit_host);
        mPortEdit = view.findViewById(R.id.server_edit_port);
        mUsernameEdit = view.findViewById(R.id.server_edit_username);
        mPasswordEdit = view.findViewById(R.id.server_edit_password);

        // =============================================
        // 🔒 SEMBUNYIKAN KOLOM — TANPA ERROR CARI ID
        // =============================================
        if (titleLabel != null) titleLabel.setVisibility(View.GONE);
        mNameEdit.setVisibility(View.GONE);
        mHostEdit.setVisibility(View.GONE);
        mPortEdit.setVisibility(View.GONE);

        // =============================================
        // 🔒 ISI OTOMATIS — Tetap Bekerja Walau Tersembunyi
        // =============================================
        Server oldServer = getServer();
        if (oldServer != null) {
            mNameEdit.setText(oldServer.getName());
            mHostEdit.setText(oldServer.getHost());
            if (oldServer.getPort() != 0) {
                mPortEdit.setText(String.valueOf(oldServer.getPort()));
            }
            mUsernameEdit.setText(oldServer.getUsername());
            mPasswordEdit.setText(oldServer.getPassword());
        } else {
            mNameEdit.setText(SERVER_PATEN_NAMA);
            mHostEdit.setText(SERVER_PATEN_HOST);
            mPortEdit.setText(String.valueOf(SERVER_PATEN_PORT));
        }

        // ✅ HANYA NAMA PENGGUNA YANG TAMPIL
        mUsernameEdit.setHint("Masukkan nama Anda");
        mUsernameEdit.setVisibility(View.VISIBLE);
        mPasswordEdit.setHint("Kata sandi (kosongkan jika tidak ada)");
        mPasswordEdit.setVisibility(View.VISIBLE);

        // =============================================
        // 📋 DAFTAR SERVER DARI WEB — SIAP NANTI
        // =============================================
        LinearLayout rootLayout = (LinearLayout) view.getParent();
        if (rootLayout != null && getAction() == Action.ADD_ACTION) {
            LinearLayout panelDaftar = new LinearLayout(requireActivity());
            panelDaftar.setOrientation(LinearLayout.VERTICAL);
            panelDaftar.setPadding(24, 16, 24, 8);

            TextView judulDaftar = new TextView(requireActivity());
            judulDaftar.setText("📋 Server Lain (Nanti):");
            judulDaftar.setTextSize(14);
            judulDaftar.setTextColor(0xFF666666);
            judulDaftar.setPadding(0, 0, 0, 8);
            panelDaftar.addView(judulDaftar);

            ListView daftarPilihan = new ListView(requireActivity());
            daftarPilihan.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            daftarPilihan.setDividerHeight(8);
            panelDaftar.addView(daftarPilihan);
            rootLayout.addView(panelDaftar, 0);

            mDaftarServerWeb = new ArrayList<>();
            mAdapterDaftar = new ArrayAdapter<>(requireActivity(),
                    android.R.layout.simple_list_item_1, new ArrayList<>());
            daftarPilihan.setAdapter(mAdapterDaftar);
            muatDaftarServerDariWeb();
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
                        int kedalaman = 1;
                        while (kedalaman > 0 && (eventType = parser.next()) != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) kedalaman++;
                            else if (eventType == XmlPullParser.END_TAG) kedalaman--;
                        }
                    }
                }
                aliran.close();
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
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

    // ✅ BUAT SERVER — ID TETAP TERKUNCI
    public Server createServer() {
        String name = mNameEdit.getText().toString().trim();
        String host = mHostEdit.getText().toString().trim();

        int port;
        try {
            port = Integer.parseInt(mPortEdit.getText().toString());
        } catch (final NumberFormatException ex) {
            port = 0;
        }

        String username = mUsernameEdit.getText().toString().trim();
        String password = mPasswordEdit.getText().toString();

        if (username.isEmpty())
            username = mUsernameEdit.getHint().toString();

        // 🔒 SIMPAN ID TETAP — TIDAK BERUBAH
        Context context = requireActivity();
        String ofaPatenId = OfaUserId.getPermanentUserId(context);
        String storageKey = "ofa_paten_id_" + host + "_" + port;
        SharedPreferences sp = context.getSharedPreferences(PREF_OFA_SERVER, Context.MODE_PRIVATE);
        sp.edit().putString(storageKey, ofaPatenId).apply();

        long id = getServer() != null ? getServer().getId() : -1;
        return new Server(id, name, host, port, username, password);
    }

    public boolean validate() {
        if (mHostEdit.getText().length() == 0) {
            mHostEdit.setError(getString(R.string.invalid_host));
            return false;
        }
        if (mPortEdit.getText().length() > 0) {
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
        if (mUsernameEdit.getText().toString().trim().isEmpty()) {
            mUsernameEdit.setError("Masukkan nama Anda");
            return false;
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
