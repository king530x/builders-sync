package fr.dadouch.builderssync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Une séance du planning Builders. */
public class Session {
    public static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    public static final String TAG = "[Planning Builders]";

    public String id = "";
    public long startMs, endMs;
    public String room = "", matiere = "", type = "", intervenants = "", note = "";

    /** Construit une séance à partir d'un objet {id,start,end,lines[]} extrait du portail. */
    public static Session fromPortal(JSONObject o) throws JSONException {
        Session s = new Session();
        JSONArray l = o.getJSONArray("lines");
        s.id = o.optString("id", line(l, 5)).trim();
        s.startMs = parseLocal(o.getString("start"));
        s.endMs = parseLocal(o.getString("end"));
        String l0 = line(l, 0);
        int dash = l0.indexOf(" - ");
        if (dash >= 0) s.room = l0.substring(dash + 3).trim();
        else if (l0.endsWith(" -")) s.room = "";
        s.matiere = line(l, 1).trim();
        s.type = line(l, 2).trim();
        s.intervenants = line(l, 3).trim();
        s.note = line(l, 6).trim();
        return s;
    }

    private static String line(JSONArray a, int i) {
        return i < a.length() ? a.optString(i, "") : "";
    }

    static long parseLocal(String iso) {
        return LocalDateTime.parse(iso).atZone(PARIS).toInstant().toEpochMilli();
    }

    public String summary() {
        return note.isEmpty() ? matiere : matiere + " – " + note;
    }

    public String description() {
        StringBuilder b = new StringBuilder();
        b.append(TAG).append(" séance ").append(id);
        if (!type.isEmpty()) b.append("\n").append(type);
        if (!intervenants.isEmpty()) {
            b.append("\n").append(intervenants.contains(",") ? "Intervenants : " : "Intervenant : ")
                    .append(intervenants);
        }
        return b.toString();
    }

    public static ZonedDateTime z(long ms) {
        return Instant.ofEpochMilli(ms).atZone(PARIS);
    }

    public static String hm(long ms) {
        return z(ms).format(DateTimeFormatter.ofPattern("HH'h'mm"));
    }

    public static String day(long ms) {
        String d = z(ms).format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.FRENCH)).replace(".", "");
        return d.isEmpty() ? d : Character.toUpperCase(d.charAt(0)) + d.substring(1);
    }

    /** Ex. « Mar 10/11 08h00–09h30 Project Management (S202) ». */
    public String label() {
        return day(startMs) + " " + hm(startMs) + "–" + hm(endMs) + " " + summary()
                + (room.isEmpty() ? "" : " (" + room + ")");
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id).put("s", startMs).put("e", endMs).put("room", room).put("mat", matiere)
                    .put("type", type).put("int", intervenants).put("note", note);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Session fromJson(JSONObject o) {
        Session s = new Session();
        s.id = o.optString("id");
        s.startMs = o.optLong("s");
        s.endMs = o.optLong("e");
        s.room = o.optString("room");
        s.matiere = o.optString("mat");
        s.type = o.optString("type");
        s.intervenants = o.optString("int");
        s.note = o.optString("note");
        return s;
    }
}
