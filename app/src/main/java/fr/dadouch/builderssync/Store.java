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

    // Identifiants « session seulement » (case « Se souvenir de moi » décochée) : en mémoire.
    private static volatile String memEmail = "", memPwd = "";

    public static String savedEmail(Context c) { return secure(c).getString("email", ""); }

    public static String email(Context c) {
        String s = savedEmail(c);
        return s.isEmpty() ? memEmail : s;
    }

    public static String password(Context c) {
        String s = secure(c).getString("pwd", "");
        return s.isEmpty() ? memPwd : s;
    }

    public static boolean hasCreds(Context c) { return !email(c).isEmpty() && !password(c).isEmpty(); }

    public static void saveCreds(Context c, String email, String pwd) {
        secure(c).edit().putString("email", email.trim()).putString("pwd", pwd).apply();
    }

    /** Connexion depuis l'écran d'accueil. */
    public static void login(Context c, String email, String pwd, boolean remember) {
        if (remember) {
            saveCreds(c, email, pwd);
            memEmail = "";
            memPwd = "";
        } else {
            secure(c).edit().clear().apply();
            memEmail = email.trim();
            memPwd = pwd;
        }
        prefs(c).edit().putBoolean("loggedOut", false).putBoolean("remember", remember)
                .putString("account", email.trim()).apply();
        clearError(c);
    }

    public static void logout(Context c) {
        secure(c).edit().clear().apply();
        memEmail = "";
        memPwd = "";
        prefs(c).edit().putBoolean("loggedOut", true).remove("mfa").remove("phase")
                .putString("status", "Déconnecté").apply();
    }

    public static boolean isLoggedOut(Context c) { return prefs(c).getBoolean("loggedOut", false); }
    public static boolean remember(Context c) { return prefs(c).getBoolean("remember", true); }
    public static String account(Context c) {
        String a = prefs(c).getString("account", "");
        return a.isEmpty() ? email(c) : a;
    }

    /** Connecté = pas déconnecté et au moins une synchro réussie avec ce compte. */
    public static boolean isLoggedIn(Context c) {
        return !isLoggedOut(c) && lastOk(c) > 0 && !account(c).isEmpty();
    }

    // --- Suivi en direct de la synchro (affiché par l'app) ---
    public static void setRunning(Context c, boolean r) {
        SharedPreferences.Editor e = prefs(c).edit().putBoolean("running", r);
        if (!r) e.remove("phase").remove("mfa");
        e.apply();
    }
    public static boolean running(Context c) { return prefs(c).getBoolean("running", false); }
    public static void setPhase(Context c, String p) {
        if (!p.equals(phase(c))) prefs(c).edit().putString("phase", p).apply();
    }
    public static String phase(Context c) { return prefs(c).getString("phase", ""); }
    public static void setMfa(Context c, String n) { prefs(c).edit().putString("mfa", n == null ? "" : n).apply(); }
    public static String mfa(Context c) { return prefs(c).getString("mfa", null); }

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
