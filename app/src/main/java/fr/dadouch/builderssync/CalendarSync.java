package fr.dadouch.builderssync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compare le planning avec l'agenda du téléphone et applique uniquement les différences. */
public class CalendarSync {
    private static final Pattern SEANCE = Pattern.compile("séance\\s+(\\d+)");
    private static final int REMINDER_MIN = 30; // rappel par défaut de l'agenda Google

    public static class Cal {
        public long id;
        public String name, account, type;
        public boolean primary;
        public String toString() { return name + (name.equals(account) ? "" : " (" + account + ")"); }
    }

    static class Ev {
        long id, start, end;
        String title, location, desc;
    }

    public static class Result {
        public final List<String> changes = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    /** Agendas modifiables du téléphone. */
    public static List<Cal> calendars(Context c) {
        List<Cal> out = new ArrayList<>();
        String[] proj = {Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE, Calendars.IS_PRIMARY, Calendars.CALENDAR_ACCESS_LEVEL};
        try (Cursor cur = c.getContentResolver().query(Calendars.CONTENT_URI, proj,
                Calendars.CALENDAR_ACCESS_LEVEL + ">=?",
                new String[]{String.valueOf(Calendars.CAL_ACCESS_CONTRIBUTOR)}, null)) {
            if (cur == null) return out;
            while (cur.moveToNext()) {
                Cal k = new Cal();
                k.id = cur.getLong(0);
                k.name = nz(cur.getString(1));
                k.account = nz(cur.getString(2));
                k.type = nz(cur.getString(3));
                k.primary = cur.getInt(4) == 1;
                out.add(k);
            }
        }
        return out;
    }

    /** Agenda par défaut : l'agenda principal Google (dadouch530@gmail.com de préférence). */
    public static Cal defaultCalendar(Context c) {
        Cal best = null;
        for (Cal k : calendars(c)) {
            if (k.name.equalsIgnoreCase("dadouch530@gmail.com") && "com.google".equals(k.type)) return k;
            if (best == null && k.primary && "com.google".equals(k.type)) best = k;
        }
        return best;
    }

    public static Cal byId(Context c, long id) {
        for (Cal k : calendars(c)) if (k.id == id) return k;
        return null;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static Map<String, Ev> existing(Context c, long calId, long from, long to, List<Ev> dupes) {
        Map<String, Ev> map = new HashMap<>();
        String[] proj = {Events._ID, Events.DTSTART, Events.DTEND, Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION};
        String sel = Events.CALENDAR_ID + "=? AND " + Events.DTSTART + ">=? AND " + Events.DTSTART + "<? AND "
                + Events.DELETED + "=0 AND " + Events.DESCRIPTION + " LIKE ?";
        String[] args = {String.valueOf(calId), String.valueOf(from), String.valueOf(to), "%" + Session.TAG + "%"};
        try (Cursor cur = c.getContentResolver().query(Events.CONTENT_URI, proj, sel, args, Events.DTSTART)) {
            if (cur == null) return map;
            while (cur.moveToNext()) {
                Ev e = new Ev();
                e.id = cur.getLong(0);
                e.start = cur.getLong(1);
                e.end = cur.isNull(2) ? e.start : cur.getLong(2);
                e.title = nz(cur.getString(3));
                e.location = nz(cur.getString(4));
                e.desc = nz(cur.getString(5));
                if (!e.desc.contains(Session.TAG)) continue;
                Matcher m = SEANCE.matcher(e.desc);
                if (!m.find()) continue;
                String sid = m.group(1);
                if (map.containsKey(sid)) dupes.add(e); else map.put(sid, e);
            }
        }
        return map;
    }

    public static Result sync(Context c, Cal cal, List<Session> portal, long from, long to) {
        Result r = new Result();
        ContentResolver cr = c.getContentResolver();
        long now = System.currentTimeMillis();
        List<Ev> dupes = new ArrayList<>();
        Map<String, Ev> ex = existing(c, cal.id, from, to, dupes);
        JSONObject snap = Store.snapshot(c);
        JSONObject newSnap = new JSONObject();
        Map<String, Session> byId = new HashMap<>();
        for (Session s : portal) byId.put(s.id, s);

        for (Session s : portal) {
            try { newSnap.put(s.id, new JSONObject().put("t", s.summary()).put("d", s.description())); }
            catch (Exception ignored) { }
            Ev e = ex.get(s.id);
            if (e == null) {
                insert(cr, cal.id, s);
                r.changes.add("Ajout : " + s.label());
                continue;
            }
            if (s.endMs < now) continue; // séance passée : on ne touche pas
            ContentValues cv = new ContentValues();
            List<String> what = new ArrayList<>();
            if (e.start != s.startMs || e.end != s.endMs) {
                cv.put(Events.DTSTART, s.startMs);
                cv.put(Events.DTEND, s.endMs);
                cv.put(Events.EVENT_TIMEZONE, Session.PARIS.getId());
                what.add(Session.day(e.start) + " " + Session.hm(e.start) + "–" + Session.hm(e.end) + " → "
                        + Session.day(s.startMs) + " " + Session.hm(s.startMs) + "–" + Session.hm(s.endMs));
            }
            if (!e.location.trim().equals(s.room)) {
                cv.put(Events.EVENT_LOCATION, s.room);
                what.add("salle " + (e.location.isEmpty() ? "—" : e.location) + " → " + (s.room.isEmpty() ? "—" : s.room));
            }
            JSONObject old = snap.optJSONObject(s.id);
            // Titre / intervenants : comparés au dernier relevé du portail, pour ne pas
            // écraser une correction faite à la main dans l'agenda.
            if (old != null && !old.optString("t").equals(s.summary())) {
                cv.put(Events.TITLE, s.summary());
                what.add("titre « " + old.optString("t") + " » → « " + s.summary() + " »");
            }
            if (old != null && !old.optString("d").equals(s.description())) {
                cv.put(Events.DESCRIPTION, s.description());
                what.add("intervenant/type mis à jour (" + s.intervenants + ")");
            }
            if (cv.size() > 0) {
                cr.update(ContentUris.withAppendedId(Events.CONTENT_URI, e.id), cv, null, null);
                r.changes.add(Session.day(s.startMs) + " " + s.summary() + " : " + String.join(", ", what));
            }
        }

        // Séances disparues du planning = annulées (seulement si elles ne sont pas commencées).
        if (portal.isEmpty() && !ex.isEmpty()) {
            r.warnings.add("Le planning est vide sur la période : aucune suppression effectuée par sécurité.");
        } else {
            for (Map.Entry<String, Ev> en : ex.entrySet()) {
                if (byId.containsKey(en.getKey())) continue;
                Ev e = en.getValue();
                if (e.start <= now) continue;
                cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, e.id), null, null);
                r.changes.add("Annulé : " + Session.day(e.start) + " " + Session.hm(e.start) + "–" + Session.hm(e.end)
                        + " " + e.title + (e.location.isEmpty() ? "" : " (" + e.location + ")"));
            }
        }
        if (!dupes.isEmpty()) r.warnings.add(dupes.size() + " doublon(s) « Planning Builders » repéré(s), laissés tels quels.");

        // Conserver l'instantané des séances hors période déjà connues.
        java.util.Iterator<String> it = snap.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (!newSnap.has(k) && !ex.containsKey(k)) {
                try { newSnap.put(k, snap.get(k)); } catch (Exception ignored) { }
            }
        }
        Store.saveSnapshot(c, newSnap);

        if (!r.changes.isEmpty()) requestSync(cal);
        return r;
    }

    private static void insert(ContentResolver cr, long calId, Session s) {
        ContentValues cv = new ContentValues();
        cv.put(Events.CALENDAR_ID, calId);
        cv.put(Events.TITLE, s.summary());
        cv.put(Events.DESCRIPTION, s.description());
        cv.put(Events.EVENT_LOCATION, s.room);
        cv.put(Events.DTSTART, s.startMs);
        cv.put(Events.DTEND, s.endMs);
        cv.put(Events.EVENT_TIMEZONE, Session.PARIS.getId());
        cv.put(Events.HAS_ALARM, 1);
        Uri u = cr.insert(Events.CONTENT_URI, cv);
        if (u == null) return;
        ContentValues rv = new ContentValues();
        rv.put(Reminders.EVENT_ID, ContentUris.parseId(u));
        rv.put(Reminders.MINUTES, REMINDER_MIN);
        rv.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        cr.insert(Reminders.CONTENT_URI, rv);
    }

    /** Demande à Android d'envoyer tout de suite les modifications vers Google Agenda. */
    public static void requestSync(Cal cal) {
        try {
            Bundle b = new Bundle();
            b.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            b.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
            ContentResolver.requestSync(new Account(cal.account, cal.type), CalendarContract.AUTHORITY, b);
        } catch (Exception ignored) { }
    }
}
