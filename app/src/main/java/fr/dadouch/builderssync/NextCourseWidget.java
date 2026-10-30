package fr.dadouch.builderssync;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.util.List;

public class NextCourseWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        render(c, m, ids);
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, NextCourseWidget.class));
        if (ids != null && ids.length > 0) render(c, m, ids);
    }

    private static void render(Context c, AppWidgetManager m, int[] ids) {
        long now = System.currentTimeMillis();
        Session next = null;
        for (Session s : Store.upcoming(c)) {
            if (s.endMs > now) { next = s; break; }
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        if (next == null) {
            v.setTextViewText(R.id.w_head, "Prochain cours");
            v.setTextViewText(R.id.w_title, "Aucun cours prévu");
            v.setTextViewText(R.id.w_line, "");
        } else {
            boolean live = next.startMs <= now;
            LocalDate d = Session.z(next.startMs).toLocalDate();
            LocalDate today = LocalDate.now(Session.PARIS);
            String when = d.equals(today) ? "Aujourd'hui" : d.equals(today.plusDays(1)) ? "Demain" : Session.day(next.startMs);
            v.setTextViewText(R.id.w_head, live ? "En cours" : "Prochain cours · " + when);
            v.setTextViewText(R.id.w_title, next.summary());
            v.setTextViewText(R.id.w_line, Session.hm(next.startMs) + "–" + Session.hm(next.endMs)
                    + (next.room.isEmpty() ? "" : " · " + next.room));
        }
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        for (int id : ids) m.updateAppWidget(id, v);
    }
}
