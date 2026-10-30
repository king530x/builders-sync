package fr.dadouch.builderssync;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncWorker extends Worker {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final long LOGIN_TIMEOUT = 150_000;

    public SyncWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    private static Constraints net() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    public static void schedule(Context c) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(SyncWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(net()).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void runNow(Context c) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SyncWorker.class).setConstraints(net()).build();
        WorkManager.getInstance(c).enqueueUniqueWork("now", ExistingWorkPolicy.KEEP, req);
    }

    public static boolean isRunning() { return RUNNING.get(); }

    @NonNull
    @Override
    public Result doWork() {
        if (!RUNNING.compareAndSet(false, true)) return Result.success();
        try {
            run(getApplicationContext());
        } catch (Exception e) {
            fail(getApplicationContext(), "crash", "Erreur inattendue : " + e.getMessage(), false);
        } finally {
            Store.setRunning(getApplicationContext(), false);
            RUNNING.set(false);
        }
        return Result.success();
    }

    private static void fail(Context c, String code, String msg, boolean login) {
        Store.setStatus(c, "⚠ " + msg);
        Store.addHistory(c, java.util.Collections.singletonList("Erreur : " + msg));
        if (Store.shouldNotifyError(c, code)) Notif.error(c, msg, login);
    }

    private void run(Context c) throws InterruptedException {
        if (c.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            fail(c, "perm", "Autorisation d'accès à l'agenda manquante : ouvre l'app.", false);
            return;
        }
        if (Store.isLoggedOut(c)) return; // l'utilisateur s'est déconnecté
        CalendarSync.Cal cal = CalendarSync.byId(c, Store.calendarId(c));
        if (cal == null) cal = CalendarSync.defaultCalendar(c);
        if (cal == null) {
            fail(c, "nocal", "Aucun agenda Google trouvé sur le téléphone.", false);
            return;
        }

        Store.setStatus(c, "Synchronisation en cours…");
        Store.setRunning(c, true);
        try {
            runSync(c, cal);
        } finally {
            Store.setRunning(c, false);
        }
    }

    private void runSync(Context c, CalendarSync.Cal cal) throws InterruptedException {
        final JSONArray[] result = {null};
        final String[] err = {null, null};
        final CountDownLatch latch = new CountDownLatch(1);
        final WebView[] holder = {null};
        final PortalSession[] ps = {null};
        int weeks = Store.DAYS / 7 + 2;
        Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> {
            try {
                holder[0] = PortalSession.headless(c);
                ps[0] = new PortalSession(c, holder[0], true, LOGIN_TIMEOUT, weeks, new PortalSession.Listener() {
                    public void onStatus(String s) { Store.setPhase(c, s); }
                    public void onMfa(String n) { Store.setMfa(c, n); Notif.mfa(c, n); }
                    public void onReady() { }
                    public void onResult(JSONArray a) { result[0] = a; latch.countDown(); }
                    public void onError(String code, String m) { err[0] = code; err[1] = m; latch.countDown(); }
                });
                ps[0].start();
            } catch (Exception e) {
                err[0] = "webview";
                err[1] = "Navigateur interne indisponible : " + e.getMessage();
                latch.countDown();
            }
        });
        boolean finished = latch.await(9, TimeUnit.MINUTES);
        main.post(() -> {
            if (ps[0] != null) ps[0].cancel();
            if (holder[0] != null) { holder[0].stopLoading(); holder[0].destroy(); }
        });
        Notif.cancelMfa(c);
        if (!finished) { fail(c, "timeout", "Délai dépassé pendant la synchro.", false); return; }
        if (err[0] != null) {
            boolean login = err[0].startsWith("mfa") || err[0].equals("timeout") || err[0].equals("need_user")
                    || err[0].equals("bad_credentials");
            if (err[0].equals("need_user") && !Store.hasCreds(c))
                err[1] = "Session expirée : reconnecte-toi dans l'app.";
            fail(c, err[0], err[1] + " Rien n'a été modifié dans l'agenda.", login);
            return;
        }

        // Filtrer la période : aujourd'hui 00:00 → aujourd'hui + DAYS jours inclus.
        LocalDate today = LocalDate.now(Session.PARIS);
        long from = today.atStartOfDay(Session.PARIS).toInstant().toEpochMilli();
        long to = today.plusDays(Store.DAYS + 1).atStartOfDay(Session.PARIS).toInstant().toEpochMilli();
        List<Session> list = new ArrayList<>();
        try {
            for (int i = 0; i < result[0].length(); i++) {
                Session s = Session.fromPortal(result[0].getJSONObject(i));
                if (s.startMs >= from && s.startMs < to && !s.id.isEmpty()) list.add(s);
            }
        } catch (Exception e) {
            fail(c, "format", "Format du planning inattendu (" + e.getMessage() + ").", false);
            return;
        }
        list.sort(Comparator.comparingLong(s -> s.startMs));

        CalendarSync.Result r = CalendarSync.sync(c, cal, list, from, to);
        Store.saveUpcoming(c, list);
        Store.setLastOk(c);
        Store.clearError(c);
        Notif.cancelError(c);
        List<String> hist = new ArrayList<>(r.changes);
        hist.addAll(r.warnings);
        Store.addHistory(c, hist.isEmpty()
                ? java.util.Collections.singletonList("Synchro OK : aucun changement (" + list.size() + " séances)")
                : hist);
        Store.setStatus(c, r.changes.isEmpty()
                ? "À jour : " + list.size() + " séances, aucun changement"
                : "À jour : " + r.changes.size() + " changement(s) appliqué(s)");
        if (!r.changes.isEmpty()) Notif.changes(c, r.changes);
        NextCourseWidget.updateAll(c);
    }
}
