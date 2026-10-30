package fr.dadouch.builderssync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private LinearLayout upcomingBox, historyBox;
    private TextView status, calLabel;
    private final Handler h = new Handler(Looper.getMainLooper());

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, float sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Notif.channels(this);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        sv.addView(root);

        root.addView(text("Builders Sync", 26, true));
        status = text("", 14, false);
        root.addView(status);

        root.addView(button("🔄  Synchroniser maintenant", v -> {
            if (!Store.hasCreds(this)) { askCreds(); return; }
            SyncWorker.runNow(this);
            Toast.makeText(this, "Synchronisation lancée…", Toast.LENGTH_SHORT).show();
        }));
        root.addView(button("🔑  Identifiants Microsoft", v -> askCreds()));
        root.addView(button("🌐  Se connecter / valider Authenticator", v ->
                startActivity(new Intent(this, LoginActivity.class))));
        calLabel = text("", 13, false);
        root.addView(button("📅  Choisir l'agenda", v -> chooseCalendar()));
        root.addView(calLabel);

        root.addView(text("Prochains cours", 20, true));
        upcomingBox = new LinearLayout(this);
        upcomingBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(upcomingBox);

        root.addView(text("Historique", 20, true));
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyBox);

        setContentView(sv);

        askPermissions();
        SyncWorker.schedule(this);
        if (!Store.hasCreds(this)) askCreds();
    }

    private void askPermissions() {
        String[] perms = {Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR,
                Manifest.permission.POST_NOTIFICATIONS};
        boolean need = false;
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need = true;
        if (need) requestPermissions(perms, 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        ensureCalendar();
        refresh();
    }

    private void ensureCalendar() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        if (CalendarSync.byId(this, Store.calendarId(this)) == null) {
            CalendarSync.Cal k = CalendarSync.defaultCalendar(this);
            if (k != null) Store.setCalendarId(this, k.id, k.toString());
        }
    }

    private void askCreds() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText em = new EditText(this);
        em.setHint("E-mail de l'école (prenom.nom@…)");
        em.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        em.setText(Store.email(this));
        EditText pw = new EditText(this);
        pw.setHint("Mot de passe Microsoft");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView info = new TextView(this);
        info.setText("Enregistré chiffré sur ce téléphone uniquement, et utilisé seulement sur la page de connexion Microsoft.");
        info.setTextSize(12);
        box.addView(em);
        box.addView(pw);
        box.addView(info);
        new AlertDialog.Builder(this)
                .setTitle("Identifiants Microsoft")
                .setView(box)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String e = em.getText().toString().trim();
                    String p = pw.getText().toString();
                    if (p.isEmpty()) p = Store.password(this);
                    if (e.isEmpty() || p.isEmpty()) {
                        Toast.makeText(this, "E-mail et mot de passe requis", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Store.saveCreds(this, e, p);
                    Store.clearError(this);
                    startActivity(new Intent(this, LoginActivity.class));
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void chooseCalendar() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            askPermissions();
            return;
        }
        List<CalendarSync.Cal> cals = CalendarSync.calendars(this);
        if (cals.isEmpty()) {
            Toast.makeText(this, "Aucun agenda modifiable trouvé", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[cals.size()];
        for (int i = 0; i < names.length; i++) names[i] = cals.get(i).toString();
        new AlertDialog.Builder(this)
                .setTitle("Agenda de destination")
                .setItems(names, (d, i) -> {
                    Store.setCalendarId(this, cals.get(i).id, names[i]);
                    refresh();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Store.prefs(this).registerOnSharedPreferenceChangeListener(this);
        ensureCalendar();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        h.post(this::refresh);
    }

    private void refresh() {
        String last = Store.lastOk(this) == 0 ? "jamais"
                : new SimpleDateFormat("EEE dd/MM HH:mm", Locale.FRENCH).format(new Date(Store.lastOk(this)));
        status.setText(Store.status(this) + "\nDernière synchro réussie : " + last
                + "\nSynchro automatique toutes les 6 h · " + Store.DAYS + " jours");
        String cn = Store.calendarName(this);
        calLabel.setText("Agenda : " + (cn.isEmpty() ? "agenda principal Google" : cn));

        upcomingBox.removeAllViews();
        List<Session> up = Store.upcoming(this);
        long now = System.currentTimeMillis();
        LocalDate lastDay = null;
        int shown = 0;
        for (Session s : up) {
            if (s.endMs < now) continue;
            LocalDate d = Session.z(s.startMs).toLocalDate();
            if (!d.equals(lastDay)) {
                TextView dh = text(Session.day(s.startMs), 15, true);
                dh.setPadding(0, dp(10), 0, dp(2));
                upcomingBox.addView(dh);
                lastDay = d;
            }
            String who = s.intervenants.isEmpty() ? "" : "\n" + s.intervenants;
            upcomingBox.addView(text(Session.hm(s.startMs) + "–" + Session.hm(s.endMs) + "  " + s.summary()
                    + (s.room.isEmpty() ? "" : "  · " + s.room) + who, 14, false));
            shown++;
        }
        if (shown == 0) upcomingBox.addView(text("Aucun cours à venir (lance une synchro).", 14, false));

        historyBox.removeAllViews();
        List<String> hist = Store.history(this);
        if (hist.isEmpty()) historyBox.addView(text("Rien pour l'instant.", 14, false));
        for (int i = 0; i < Math.min(hist.size(), 60); i++) historyBox.addView(text(hist.get(i), 13, false));
        NextCourseWidget.updateAll(this);
    }
}
