package fr.dadouch.builderssync;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.List;

public class Notif {
    static final String CH_CHANGES = "changes", CH_ERRORS = "errors";
    static final int ID_CHANGES = 1, ID_ERROR = 2, ID_MFA = 3;

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_CHANGES, "Changements de planning",
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_ERRORS, "Connexion et erreurs",
                NotificationManager.IMPORTANCE_HIGH));
    }

    private static PendingIntent open(Context c, Class<?> cls) {
        Intent i = new Intent(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, cls.hashCode(), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void post(Context c, int id, Notification n) {
        try {
            c.getSystemService(NotificationManager.class).notify(id, n);
        } catch (SecurityException ignored) { } // notifications refusées
    }

    static void changes(Context c, List<String> lines) {
        channels(c);
        Notification.InboxStyle st = new Notification.InboxStyle();
        for (String l : lines) st.addLine(l);
        String title = lines.size() == 1 ? "1 changement de planning" : lines.size() + " changements de planning";
        post(c, ID_CHANGES, new Notification.Builder(c, CH_CHANGES)
                .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(title)
                .setContentText(lines.get(0))
                .setStyle(st)
                .setContentIntent(open(c, MainActivity.class))
                .setAutoCancel(true)
                .build());
    }

    static void error(Context c, String message, boolean openLogin) {
        channels(c);
        post(c, ID_ERROR, new Notification.Builder(c, CH_ERRORS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Builders Sync : synchro impossible")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message
                        + (openLogin ? "\nTouche ici pour te reconnecter." : "")))
                .setContentIntent(open(c, openLogin ? LoginActivity.class : MainActivity.class))
                .setAutoCancel(true)
                .build());
    }

    static void mfa(Context c, String number) {
        channels(c);
        String txt = number.isEmpty() ? "Ouvre Microsoft Authenticator et approuve la connexion."
                : "Ouvre Microsoft Authenticator et saisis le numéro " + number + ".";
        post(c, ID_MFA, new Notification.Builder(c, CH_ERRORS)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("Validation Microsoft demandée")
                .setContentText(txt)
                .setStyle(new Notification.BigTextStyle().bigText(txt))
                .setTimeoutAfter(4 * 60_000)
                .setAutoCancel(true)
                .build());
    }

    static void cancelMfa(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID_MFA);
    }

    static void cancelError(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID_ERROR);
    }
}
