package fr.dadouch.builderssync;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String EXTRA_TAB = "tab";
    public static final int TAB_LOGIN = 0, TAB_PROGRAM = 1, TAB_CHANGES = 2;
    private static final int[] NAV_IDS = {R.id.nav_connexion, R.id.nav_programme, R.id.nav_changements};
    private static final String[] TITLES = {"Builders Sync", "Programme", "Changements"};

    private MaterialToolbar toolbar;
    private LinearProgressIndicator topProgress;
    private BottomNavigationView nav;
    private final View[] tabs = new View[3];
    private int current = -1;

    // Onglet Connexion
    private View hero, heroIcon;
    private TextView heroTitle, heroSubtitle;
    private MaterialCardView cardLogin, cardMfa, cardAccount, cardSync, cardCal;
    private TextInputLayout layoutEmail, layoutPassword;
    private TextInputEditText inputEmail, inputPassword;
    private MaterialCheckBox checkRemember;
    private MaterialButton btnLogin, btnSync, btnReconnect, btnLogout;
    private LinearProgressIndicator loginProgress, syncProgress;
    private TextView loginPhase, loginError, mfaNumber, mfaHint, avatar, accountEmail, accountState,
            syncStatus, syncLast, syncPhase, calName;

    // Autres onglets
    private SwipeRefreshLayout swipeProg;
    private LinearLayout progList, changeList;

    private boolean loginPending = false;
    private long okBeforeLogin = 0;
    private String lastLoginState = "";
    private ObjectAnimator floatAnim, pulseAnim;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_main);
        Notif.channels(this);

        toolbar = findViewById(R.id.toolbar);
        topProgress = findViewById(R.id.topProgress);
        nav = findViewById(R.id.bottomNav);
        tabs[0] = findViewById(R.id.tabConnexion);
        tabs[1] = findViewById(R.id.tabProgramme);
        tabs[2] = findViewById(R.id.tabChangements);

        hero = findViewById(R.id.hero);
        heroIcon = findViewById(R.id.heroIcon);
        heroTitle = findViewById(R.id.heroTitle);
        heroSubtitle = findViewById(R.id.heroSubtitle);
        cardLogin = findViewById(R.id.cardLogin);
        cardMfa = findViewById(R.id.cardMfa);
        cardAccount = findViewById(R.id.cardAccount);
        cardSync = findViewById(R.id.cardSync);
        cardCal = findViewById(R.id.cardCal);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPassword = findViewById(R.id.layoutPassword);
        inputEmail = findViewById(R.id.inputEmail);
        inputPassword = findViewById(R.id.inputPassword);
        checkRemember = findViewById(R.id.checkRemember);
        btnLogin = findViewById(R.id.btnLogin);
        btnSync = findViewById(R.id.btnSync);
        btnReconnect = findViewById(R.id.btnReconnect);
        btnLogout = findViewById(R.id.btnLogout);
        loginProgress = findViewById(R.id.loginProgress);
        syncProgress = findViewById(R.id.syncProgress);
        loginPhase = findViewById(R.id.loginPhase);
        loginError = findViewById(R.id.loginError);
        mfaNumber = findViewById(R.id.mfaNumber);
        mfaHint = findViewById(R.id.mfaHint);
        avatar = findViewById(R.id.avatar);
        accountEmail = findViewById(R.id.accountEmail);
        accountState = findViewById(R.id.accountState);
        syncStatus = findViewById(R.id.syncStatus);
        syncLast = findViewById(R.id.syncLast);
        syncPhase = findViewById(R.id.syncPhase);
        calName = findViewById(R.id.calName);
        swipeProg = findViewById(R.id.swipeProg);
        progList = findViewById(R.id.progList);
        changeList = findViewById(R.id.changeList);

        // Le processus a pu être tué pendant une synchro : on remet l'état à zéro.
        if (Store.running(this) && !SyncWorker.isRunning()) Store.setRunning(this, false);

        inputEmail.setText(Store.account(this));
        checkRemember.setChecked(Store.remember(this));
        inputPassword.setOnEditorActionListener((v, id, ev) -> {
            if (id == EditorInfo.IME_ACTION_DONE) { doLogin(); return true; }
            return false;
        });
        btnLogin.setOnClickListener(v -> doLogin());
        findViewById(R.id.btnOpenWeb).setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        btnReconnect.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
        btnSync.setOnClickListener(v -> {
            bounce(v);
            Store.setRunning(this, true);
            Store.setPhase(this, "Démarrage…");
            SyncWorker.runNow(this);
        });
        findViewById(R.id.btnCal).setOnClickListener(v -> chooseCalendar());
        btnLogout.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Se déconnecter ?")
                .setMessage("La synchronisation automatique s'arrête et tes identifiants sont effacés de ce téléphone. "
                        + "Les cours déjà dans ton agenda restent en place.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Se déconnecter", (d, w) -> doLogout())
                .show());

        swipeProg.setColorSchemeColors(color(androidx.appcompat.R.attr.colorPrimary));
        swipeProg.setOnRefreshListener(() -> {
            if (Store.isLoggedOut(this)) { swipeProg.setRefreshing(false); selectTab(TAB_LOGIN, true); return; }
            Store.setRunning(this, true);
            SyncWorker.runNow(this);
        });

        nav.setOnItemSelectedListener(item -> {
            for (int i = 0; i < NAV_IDS.length; i++) if (NAV_IDS[i] == item.getItemId()) showTab(i, true);
            return true;
        });

        askPermissions();
        SyncWorker.schedule(this);

        int start = Store.isLoggedIn(this) ? TAB_PROGRAM : TAB_LOGIN;
        start = getIntent().getIntExtra(EXTRA_TAB, start);
        selectTab(start, false);
        playIntro();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_TAB)) selectTab(intent.getIntExtra(EXTRA_TAB, TAB_PROGRAM), true);
    }

    private int color(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.resourceId != 0 ? ContextCompat.getColor(this, tv.resourceId) : tv.data;
    }

    // ------------------------------------------------------------------ Onglets & animations

    private void selectTab(int i, boolean animate) {
        showTab(i, animate);
        if (nav.getSelectedItemId() != NAV_IDS[i]) nav.getMenu().findItem(NAV_IDS[i]).setChecked(true);
    }

    private void showTab(int i, boolean animate) {
        if (i == current) return;
        View in = tabs[i];
        View out = current >= 0 ? tabs[current] : null;
        int dir = i > current ? 1 : -1;
        current = i;
        for (int k = 0; k < tabs.length; k++) {
            if (tabs[k] != in && tabs[k] != out) tabs[k].setVisibility(View.GONE);
        }
        if (!animate || out == null) {
            if (out != null) out.setVisibility(View.GONE);
            in.setVisibility(View.VISIBLE);
            in.setAlpha(1f);
            in.setTranslationX(0);
        } else {
            out.animate().cancel();
            out.animate().alpha(0f).translationX(-dir * dp(28)).setDuration(140)
                    .withEndAction(() -> {
                        if (tabs[current] != out) out.setVisibility(View.GONE);
                        out.setAlpha(1f);
                        out.setTranslationX(0);
                    }).start();
            in.animate().cancel();
            in.setVisibility(View.VISIBLE);
            in.setAlpha(0f);
            in.setTranslationX(dir * dp(40));
            in.animate().alpha(1f).translationX(0).setStartDelay(60).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
        toolbar.setTitle(TITLES[i]);
        renderToolbar();
        if (i == TAB_LOGIN) renderLogin(true);
        if (i == TAB_PROGRAM) renderProgram(true);
        if (i == TAB_CHANGES) renderChanges(true);
    }

    /** Apparition en cascade des éléments d'une liste. */
    private void cascade(View v, int index) {
        v.setAlpha(0f);
        v.setTranslationY(dp(18));
        v.animate().alpha(1f).translationY(0).setStartDelay(Math.min(index, 12) * 35L).setDuration(320)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    private void bounce(View v) {
        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start()
        ).start();
    }

    private void playIntro() {
        heroIcon.setScaleX(0.4f);
        heroIcon.setScaleY(0.4f);
        heroIcon.setAlpha(0f);
        heroIcon.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600)
                .setInterpolator(new OvershootInterpolator(1.8f)).start();
        heroTitle.setAlpha(0f);
        heroTitle.setTranslationY(dp(12));
        heroTitle.animate().alpha(1f).translationY(0).setStartDelay(200).setDuration(400).start();
        heroSubtitle.setAlpha(0f);
        heroSubtitle.animate().alpha(1f).setStartDelay(320).setDuration(400).start();
        floatAnim = ObjectAnimator.ofFloat(heroIcon, "translationY", 0f, -dp(6));
        floatAnim.setDuration(1800);
        floatAnim.setRepeatMode(ValueAnimator.REVERSE);
        floatAnim.setRepeatCount(ValueAnimator.INFINITE);
        floatAnim.setStartDelay(700);
        floatAnim.start();
    }

    private void renderToolbar() {
        boolean running = Store.running(this);
        topProgress.setVisibility(running && current != TAB_LOGIN ? View.VISIBLE : View.INVISIBLE);
        if (current == TAB_PROGRAM) {
            int n = 0;
            long now = System.currentTimeMillis();
            for (Session s : Store.upcoming(this)) if (s.endMs >= now) n++;
            toolbar.setSubtitle(n == 0 ? null : n + " cours à venir · " + Store.DAYS + " jours");
        } else if (current == TAB_CHANGES) {
            toolbar.setSubtitle("Ajouts, modifications et annulations");
        } else {
            toolbar.setSubtitle(Store.isLoggedIn(this) ? "Connecté" : null);
        }
        swipeProg.setRefreshing(running && current == TAB_PROGRAM);
    }

    // ------------------------------------------------------------------ Connexion

    private void doLogin() {
        String e = inputEmail.getText() == null ? "" : inputEmail.getText().toString().trim();
        String p = inputPassword.getText() == null ? "" : inputPassword.getText().toString();
        layoutEmail.setError(null);
        layoutPassword.setError(null);
        if (!e.contains("@") || e.length() < 5) {
            layoutEmail.setError("Saisis ton e-mail de l'école");
            shake(layoutEmail);
            return;
        }
        if (p.isEmpty()) {
            if (Store.email(this).equalsIgnoreCase(e) && !Store.password(this).isEmpty()) {
                p = Store.password(this); // mot de passe déjà enregistré
            } else {
                layoutPassword.setError("Saisis ton mot de passe");
                shake(layoutPassword);
                return;
            }
        }
        if (!e.equalsIgnoreCase(Store.account(this))) {
            // Autre compte : on repart d'une session propre.
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }
        hideKeyboard();
        bounce(btnLogin);
        Store.login(this, e, p, checkRemember.isChecked());
        inputPassword.setText("");
        loginPending = true;
        okBeforeLogin = Store.lastOk(this);
        Store.setRunning(this, true);
        Store.setPhase(this, "Connexion à Microsoft…");
        SyncWorker.runNow(this);
        renderLogin(false);
    }

    private void doLogout() {
        Store.logout(this);
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        loginPending = false;
        inputEmail.setText(Store.account(this));
        selectTab(TAB_LOGIN, true);
        renderLogin(false);
        snack("Déconnecté");
    }

    private void shake(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "translationX", 0, dp(10), -dp(10), dp(6), -dp(6), 0);
        a.setDuration(380);
        a.start();
    }

    private void hideKeyboard() {
        View f = getCurrentFocus();
        if (f != null) {
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
            f.clearFocus();
        }
    }

    private void renderLogin(boolean entering) {
        boolean loggedIn = Store.isLoggedIn(this);
        boolean running = Store.running(this);
        String mfa = Store.mfa(this);
        String phase = Store.phase(this);
        boolean showForm = !loggedIn;

        // Fin de la première connexion
        if (loginPending && !running) {
            loginPending = false;
            if (Store.lastOk(this) > okBeforeLogin) {
                snack("Connecté ✓ Planning synchronisé");
                selectTab(TAB_PROGRAM, true);
                return;
            }
        }

        String state = showForm + "|" + loggedIn;
        boolean changed = !state.equals(lastLoginState);
        lastLoginState = state;

        hero.setVisibility(showForm ? View.VISIBLE : View.GONE);
        setVisible(cardLogin, showForm, changed);
        setVisible(cardAccount, loggedIn, changed);
        setVisible(cardSync, loggedIn, changed);
        setVisible(cardCal, loggedIn, changed);
        setVisible(btnLogout, loggedIn, changed);

        // Formulaire
        boolean busy = running && (loginPending || showForm);
        btnLogin.setEnabled(!busy);
        btnLogin.setText(busy ? "Connexion en cours…" : "Se connecter");
        loginProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        loginPhase.setVisibility(busy && !phase.isEmpty() ? View.VISIBLE : View.GONE);
        loginPhase.setText(phase);
        String status = Store.status(this);
        boolean err = !running && status.startsWith("⚠") && showForm;
        loginError.setVisibility(err ? View.VISIBLE : View.GONE);
        loginError.setText(status);

        // Authenticator
        boolean showMfa = running && mfa != null;
        if (showMfa) {
            mfaNumber.setVisibility(mfa.isEmpty() ? View.GONE : View.VISIBLE);
            mfaNumber.setText(mfa);
            mfaHint.setText(mfa.isEmpty() ? "Ouvre Microsoft Authenticator et approuve la connexion."
                    : "Ouvre Microsoft Authenticator et saisis ce numéro.");
        }
        if (showMfa && cardMfa.getVisibility() != View.VISIBLE) {
            cardMfa.setVisibility(View.VISIBLE);
            cascade(cardMfa, 0);
            pulseAnim = ObjectAnimator.ofPropertyValuesHolder(cardMfa,
                    android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.03f),
                    android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.03f));
            pulseAnim.setDuration(700);
            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.start();
        } else if (!showMfa && cardMfa.getVisibility() == View.VISIBLE) {
            if (pulseAnim != null) pulseAnim.cancel();
            cardMfa.setScaleX(1f);
            cardMfa.setScaleY(1f);
            cardMfa.setVisibility(View.GONE);
        }

        // Compte + synchro
        String acc = Store.account(this);
        accountEmail.setText(acc);
        avatar.setText(acc.isEmpty() ? "?" : acc.substring(0, 1).toUpperCase(Locale.FRENCH));
        accountState.setText(Store.remember(this) ? "Connecté · mémorisé" : "Connecté · cette session");
        syncStatus.setText(status);
        long ok = Store.lastOk(this);
        syncLast.setText("Dernière synchro réussie : " + (ok == 0 ? "jamais"
                : new SimpleDateFormat("EEEE dd/MM 'à' HH:mm", Locale.FRENCH).format(new Date(ok)))
                + "\nAutomatique toutes les 6 h sur " + Store.DAYS + " jours");
        syncProgress.setVisibility(running && loggedIn ? View.VISIBLE : View.GONE);
        syncPhase.setVisibility(running && loggedIn && !phase.isEmpty() ? View.VISIBLE : View.GONE);
        syncPhase.setText(phase);
        btnSync.setEnabled(!running);
        btnSync.setText(running ? "Synchronisation…" : "Synchroniser maintenant");
        boolean needReconnect = !running && status.startsWith("⚠")
                && (status.contains("Authenticator") || status.contains("Connexion bloquée")
                || status.contains("Session expirée") || status.contains("code"));
        btnReconnect.setVisibility(needReconnect && loggedIn ? View.VISIBLE : View.GONE);
        String cn = Store.calendarName(this);
        calName.setText(cn.isEmpty() ? "Agenda principal Google" : cn);

        if (entering && current == TAB_LOGIN) {
            int k = 0;
            ViewGroup root = findViewById(R.id.connexionRoot);
            for (int i = 0; i < root.getChildCount(); i++) {
                View c = root.getChildAt(i);
                if (c.getVisibility() == View.VISIBLE && c != hero) cascade(c, k++);
            }
        }
    }

    private void setVisible(View v, boolean show, boolean animate) {
        int want = show ? View.VISIBLE : View.GONE;
        if (v.getVisibility() == want) return;
        v.setVisibility(want);
        if (show && animate) cascade(v, 1);
    }

    private void snack(String msg) {
        Snackbar s = Snackbar.make(nav, msg, Snackbar.LENGTH_LONG);
        s.setAnchorView(nav);
        s.show();
    }

    // ------------------------------------------------------------------ Programme

    private void renderProgram(boolean animate) {
        progList.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        long now = System.currentTimeMillis();
        List<Session> up = Store.upcoming(this);
        int idx = 0;
        Session next = null;
        for (Session s : up) if (s.endMs >= now) { next = s; break; }

        if (next == null) {
            View e = inf.inflate(R.layout.item_empty, progList, false);
            ((ImageView) e.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_event);
            ((TextView) e.findViewById(R.id.emptyTitle)).setText(
                    Store.isLoggedIn(this) ? "Aucun cours à venir" : "Pas encore de programme");
            ((TextView) e.findViewById(R.id.emptyText)).setText(Store.isLoggedIn(this)
                    ? "Tire vers le bas pour relancer une synchro."
                    : "Connecte-toi dans l'onglet Connexion pour récupérer ton planning.");
            progList.addView(e);
            if (animate) cascade(e, 0);
            return;
        }

        // Carte « Prochain cours »
        View n = inf.inflate(R.layout.item_next, progList, false);
        boolean live = next.startMs <= now;
        ((TextView) n.findViewById(R.id.nHead)).setText(live ? "EN COURS" : "PROCHAIN COURS · " + relDay(next.startMs).toUpperCase(Locale.FRENCH));
        ((TextView) n.findViewById(R.id.nTitle)).setText(next.summary());
        ((TextView) n.findViewById(R.id.nTime)).setText(Session.hm(next.startMs) + " – " + Session.hm(next.endMs)
                + (next.room.isEmpty() ? "" : "   ·   " + next.room));
        ((TextView) n.findViewById(R.id.nWho)).setText(next.intervenants);
        progList.addView(n);
        if (animate) cascade(n, idx++);

        LocalDate lastDay = null;
        for (Session s : up) {
            if (s.endMs < now) continue;
            LocalDate d = Session.z(s.startMs).toLocalDate();
            if (!d.equals(lastDay)) {
                TextView h = (TextView) inf.inflate(R.layout.item_day, progList, false);
                h.setText(relDayLong(s.startMs));
                progList.addView(h);
                if (animate) cascade(h, idx++);
                lastDay = d;
            }
            View c = inf.inflate(R.layout.item_course, progList, false);
            ((TextView) c.findViewById(R.id.cStart)).setText(Session.hm(s.startMs));
            ((TextView) c.findViewById(R.id.cEnd)).setText(Session.hm(s.endMs));
            ((TextView) c.findViewById(R.id.cTitle)).setText(s.matiere);
            TextView note = c.findViewById(R.id.cNote);
            if (!s.note.isEmpty()) { note.setVisibility(View.VISIBLE); note.setText(s.note); }
            TextView room = c.findViewById(R.id.cRoom);
            room.setText(s.room.isEmpty() ? "Lieu non précisé" : s.room);
            room.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.ic_place, 16), null, null, null);
            TextView who = c.findViewById(R.id.cWho);
            if (s.intervenants.isEmpty()) who.setVisibility(View.GONE);
            else {
                who.setText(s.intervenants);
                who.setCompoundDrawablesRelativeWithIntrinsicBounds(tinted(R.drawable.ic_person, 14), null, null, null);
            }
            if (s.startMs <= now && s.endMs >= now) {
                c.findViewById(R.id.cBadge).setVisibility(View.VISIBLE);
                c.findViewById(R.id.cBar).setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ok)));
            }
            progList.addView(c);
            if (animate) cascade(c, idx++);
        }
    }

    private android.graphics.drawable.Drawable tinted(int res, int sizeDp) {
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(this, res);
        if (d == null) return null;
        d = d.mutate();
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        d.setTint(color(android.R.attr.textColorSecondary));
        return scaled(d, sizeDp);
    }

    private android.graphics.drawable.Drawable scaled(android.graphics.drawable.Drawable d, int sizeDp) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(dp(sizeDp), dp(sizeDp), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(b);
        d.setBounds(0, 0, cv.getWidth(), cv.getHeight());
        d.draw(cv);
        return new android.graphics.drawable.BitmapDrawable(getResources(), b);
    }

    private String relDay(long ms) {
        LocalDate d = Session.z(ms).toLocalDate();
        LocalDate t = LocalDate.now(Session.PARIS);
        if (d.equals(t)) return "Aujourd'hui";
        if (d.equals(t.plusDays(1))) return "Demain";
        return Session.day(ms);
    }

    private String relDayLong(long ms) {
        LocalDate d = Session.z(ms).toLocalDate();
        String full = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH));
        LocalDate t = LocalDate.now(Session.PARIS);
        if (d.equals(t)) return "Aujourd'hui · " + full;
        if (d.equals(t.plusDays(1))) return "Demain · " + full;
        return full;
    }

    // ------------------------------------------------------------------ Changements

    private void renderChanges(boolean animate) {
        changeList.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        List<String> hist = Store.history(this);
        if (hist.isEmpty()) {
            View e = inf.inflate(R.layout.item_empty, changeList, false);
            ((ImageView) e.findViewById(R.id.emptyIcon)).setImageResource(R.drawable.ic_history);
            ((TextView) e.findViewById(R.id.emptyTitle)).setText("Aucun changement pour l'instant");
            ((TextView) e.findViewById(R.id.emptyText)).setText("Les cours ajoutés, déplacés ou annulés apparaîtront ici.");
            changeList.addView(e);
            if (animate) cascade(e, 0);
            return;
        }
        String today = new SimpleDateFormat("dd/MM", Locale.FRENCH).format(new Date());
        String yesterday = new SimpleDateFormat("dd/MM", Locale.FRENCH).format(new Date(System.currentTimeMillis() - 86_400_000L));
        String lastGroup = null;
        int idx = 0;
        for (int i = 0; i < Math.min(hist.size(), 80); i++) {
            String line = hist.get(i);
            int sep = line.indexOf(" · ");
            String stamp = sep > 0 ? line.substring(0, sep) : "";
            String text = sep > 0 ? line.substring(sep + 3) : line;
            String day = stamp.length() >= 5 ? stamp.substring(0, 5) : "";
            String time = stamp.length() > 6 ? stamp.substring(6) : stamp;
            if (!TextUtils.equals(day, lastGroup)) {
                TextView h = (TextView) inf.inflate(R.layout.item_day, changeList, false);
                h.setText(day.equals(today) ? "Aujourd'hui" : day.equals(yesterday) ? "Hier" : "Le " + day);
                changeList.addView(h);
                if (animate) cascade(h, idx++);
                lastGroup = day;
            }
            int icon, col;
            String kind;
            if (text.startsWith("Ajout : ")) {
                kind = "Cours ajouté"; icon = R.drawable.ic_add; col = R.color.ok; text = text.substring(8);
            } else if (text.startsWith("Annulé : ")) {
                kind = "Cours annulé"; icon = R.drawable.ic_close; col = R.color.cancel; text = text.substring(9);
            } else if (text.startsWith("Erreur : ")) {
                kind = "Synchro impossible"; icon = R.drawable.ic_warning; col = R.color.error; text = text.substring(9);
            } else if (text.startsWith("Synchro OK")) {
                kind = "Synchro réussie"; icon = R.drawable.ic_check; col = R.color.neutral;
                int p = text.indexOf(": ");
                text = p > 0 ? text.substring(p + 2) : text;
            } else if (text.contains("doublon") || text.contains("aucune suppression")) {
                kind = "Avertissement"; icon = R.drawable.ic_warning; col = R.color.edit;
            } else {
                kind = "Cours modifié"; icon = R.drawable.ic_edit; col = R.color.edit;
            }
            View c = inf.inflate(R.layout.item_change, changeList, false);
            FrameLayout bg = c.findViewById(R.id.chIconBg);
            bg.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, col)));
            ((ImageView) c.findViewById(R.id.chIcon)).setImageResource(icon);
            TextView k = c.findViewById(R.id.chKind);
            k.setText(kind);
            k.setTextColor(ContextCompat.getColor(this, col));
            ((TextView) c.findViewById(R.id.chText)).setText(text);
            ((TextView) c.findViewById(R.id.chTime)).setText(time);
            changeList.addView(c);
            if (animate) cascade(c, idx++);
        }
    }

    // ------------------------------------------------------------------ Agenda & permissions

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
        if (current == TAB_LOGIN) renderLogin(false);
    }

    private void ensureCalendar() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return;
        if (CalendarSync.byId(this, Store.calendarId(this)) == null) {
            CalendarSync.Cal k = CalendarSync.defaultCalendar(this);
            if (k != null) Store.setCalendarId(this, k.id, k.toString());
        }
    }

    private void chooseCalendar() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            askPermissions();
            return;
        }
        List<CalendarSync.Cal> cals = CalendarSync.calendars(this);
        if (cals.isEmpty()) {
            snack("Aucun agenda modifiable trouvé");
            return;
        }
        String[] names = new String[cals.size()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = cals.get(i).toString();
            if (cals.get(i).id == Store.calendarId(this)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Agenda de destination")
                .setSingleChoiceItems(names, checked, (d, i) -> {
                    Store.setCalendarId(this, cals.get(i).id, names[i]);
                    d.dismiss();
                    snack("Agenda choisi : " + names[i]);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ------------------------------------------------------------------ Cycle de vie

    @Override
    protected void onResume() {
        super.onResume();
        Store.prefs(this).registerOnSharedPreferenceChangeListener(this);
        ensureCalendar();
        renderToolbar();
        if (current == TAB_LOGIN) renderLogin(false);
        if (current == TAB_PROGRAM) renderProgram(false);
        if (current == TAB_CHANGES) renderChanges(false);
        NextCourseWidget.updateAll(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Store.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        if (floatAnim != null) floatAnim.cancel();
        if (pulseAnim != null) pulseAnim.cancel();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String key) {
        if (key == null) return;
        runOnUiThread(() -> {
            renderToolbar();
            switch (key) {
                case "upcoming":
                    if (current == TAB_PROGRAM) renderProgram(true);
                    NextCourseWidget.updateAll(this);
                    break;
                case "history":
                    if (current == TAB_CHANGES) renderChanges(true);
                    break;
                case "running":
                case "phase":
                case "mfa":
                case "status":
                case "lastOk":
                case "calName":
                case "loggedOut":
                    if (current == TAB_LOGIN || loginPending) renderLogin(false);
                    break;
                default:
                    break;
            }
        });
    }
}
