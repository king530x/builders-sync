package fr.dadouch.builderssync;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Stockage local : identifiants chiffrés + état de la synchro. */
public class Store {
    public static final int DAYS = 30;
    private static final int HISTORY_MAX = 150;

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("state", Context.MODE_PRIVATE);
    }

    private static SharedPreferences secure(Context c) {
        try {
            MasterKey key = new MasterKey.Builder(c.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            return EncryptedSharedPreferences.create(c.getApplicationContext(), "creds", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new RuntimeException("Stockage chiffré indisponible", e);
        }
    }

    public static String email(Context c) { return secure(c).getString("email", ""); }
    public static String password(Context c) { return secure(c).getString("pwd", ""); }
    public static boolean hasCreds(Context c) { return !email(c).isEmpty() && !password(c).isEmpty(); }

    public static void saveCreds(Context c, String email, String pwd) {
        secure(c).edit().putString("email", email.trim()).putString("pwd", pwd).apply();
    }

    public static long calendarId(Context c) { return prefs(c).getLong("calId", -1); }
    public static void setCalendarId(Context c, long id, String name) {
        prefs(c).edit().putLong("calId", id).putString("calName", name).apply();
    }
    public static String calendarName(Context c) { return prefs(c).getString("calName", ""); }

    public static void setStatus(Context c, String status) {
        prefs(c).edit().putString("status", status).putLong("statusAt", System.currentTimeMillis()).apply();
    }
    public static String status(Context c) { return prefs(c).getString("status", "Jamais synchronisé"); }
    public static long statusAt(Context c) { return prefs(c).getLong("statusAt", 0); }

    public static void setLastOk(Context c) { prefs(c).edit().putLong("lastOk", System.currentTimeMillis()).apply(); }
    public static long lastOk(Context c) { return prefs(c).getLong("lastOk", 0); }

    // --- Instantané du portail (pour détecter titre/intervenant modifiés) ---
    public static JSONObject snapshot(Context c) {
        try { return new JSONObject(prefs(c).getString("snapshot", "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }
    public static void saveSnapshot(Context c, JSONObject o) {
        prefs(c).edit().putString("snapshot", o.toString()).apply();
    }

    // --- Prochains cours ---
    public static void saveUpcoming(Context c, List<Session> list) {
        JSONArray a = new JSONArray();
        for (Session s : list) a.put(s.toJson());
        prefs(c).edit().putString("upcoming", a.toString()).apply();
    }
    public static List<Session> upcoming(Context c) {
        List<Session> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("upcoming", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Session.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) { }
        return out;
    }

    // --- Historique ---
    public static void addHistory(Context c, List<String> lines) {
        if (lines.isEmpty()) return;
        String stamp = new SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH).format(new Date());
        JSONArray old;
        try { old = new JSONArray(prefs(c).getString("history", "[]")); }
        catch (JSONException e) { old = new JSONArray(); }
        JSONArray a = new JSONArray();
        for (String l : lines) a.put(stamp + " · " + l);
        for (int i = 0; i < old.length() && a.length() < HISTORY_MAX; i++) a.put(old.optString(i));
        prefs(c).edit().putString("history", a.toString()).apply();
    }
    public static List<String> history(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("history", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (JSONException ignored) { }
        return out;
    }

    // --- Anti-spam des notifications d'erreur ---
    public static boolean shouldNotifyError(Context c, String code) {
        SharedPreferences p = prefs(c);
        String last = p.getString("errCode", "");
        long at = p.getLong("errAt", 0);
        boolean notify = !code.equals(last) || System.currentTimeMillis() - at > 12L * 3600_000L;
        if (notify) p.edit().putString("errCode", code).putLong("errAt", System.currentTimeMillis()).apply();
        return notify;
    }
    public static void clearError(Context c) {
        prefs(c).edit().remove("errCode").remove("errAt").apply();
    }
}
