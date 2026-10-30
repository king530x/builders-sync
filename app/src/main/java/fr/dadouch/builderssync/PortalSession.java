package fr.dadouch.builderssync;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Pilote un WebView (visible ou non) : connexion Microsoft automatique avec les identifiants
 * enregistrés, ouverture de « Mon Planning », puis extraction des séances.
 * À utiliser uniquement depuis le thread principal.
 */
public class PortalSession {
    public static final String URL = "https://connect.builders-ingenieurs.fr/faces/Planning.xhtml";

    public interface Listener {
        void onStatus(String status);
        void onMfa(String number);
        void onReady();
        void onResult(JSONArray sessions);
        void onError(String code, String message);
    }

    private final Context ctx;
    private final WebView wv;
    private final Listener l;
    private final boolean extract;
    private final long timeoutMs;
    private final int weeks;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final String email, pwd, driverJs, extractJs;

    private long startedAt;
    private boolean done = false, extracting = false, mfaSeen = false;
    private long extractStartedAt;
    private int pwdSubmits = 0, emailSubmits = 0;
    private String lastHost = "";

    public PortalSession(Context ctx, WebView wv, boolean extract, long timeoutMs, int weeks, Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.wv = wv;
        this.extract = extract;
        this.timeoutMs = timeoutMs;
        this.weeks = weeks;
        this.l = l;
        this.email = Store.email(ctx);
        this.pwd = Store.password(ctx);
        this.driverJs = asset("driver.js");
        this.extractJs = asset("extract.js");
    }

    private String asset(String name) {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static void configure(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);
    }

    public void start() {
        configure(wv);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String scheme = req.getUrl().getScheme();
                // Les liens non web (ex. msauth://, intent://) sont ignorés.
                return scheme == null || !(scheme.equals("https") || scheme.equals("http"));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                status("Chargement de " + host(url) + "…");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
            }
        });
        startedAt = System.currentTimeMillis();
        status("Ouverture du portail Builders…");
        wv.loadUrl(URL);
        h.postDelayed(this::tick, 3500);
    }

    public void cancel() {
        done = true;
        h.removeCallbacksAndMessages(null);
    }

    private static String host(String url) {
        try {
            String hh = Uri.parse(url).getHost();
            return hh == null ? "" : hh;
        } catch (Exception e) {
            return "";
        }
    }

    private void status(String s) {
        if (!done) l.onStatus(s);
    }

    private void fail(String code, String msg) {
        if (done) return;
        done = true;
        h.removeCallbacksAndMessages(null);
        CookieManager.getInstance().flush();
        l.onError(code, msg);
    }

    private void next(long ms) {
        if (!done) h.postDelayed(this::tick, ms);
    }

    private static String decode(String v) {
        if (v == null || v.equals("null")) return "";
        try {
            Object o = new JSONTokener(v).nextValue();
            return o instanceof String ? (String) o : String.valueOf(o);
        } catch (Exception e) {
            return v;
        }
    }

    private void tick() {
        if (done) return;
        long now = System.currentTimeMillis();
        if (extracting) {
            if (now - extractStartedAt > 120_000) {
                fail("extract_timeout", "La lecture du planning a pris trop de temps.");
                return;
            }
            wv.evaluateJavascript("window.__bsResult||''", v -> {
                String r = decode(v);
                if (r.isEmpty()) { next(2000); return; }
                if (r.startsWith("ERR:")) { fail("extract_error", "Lecture du planning impossible : " + r.substring(4)); return; }
                try {
                    JSONArray a = new JSONArray(r);
                    done = true;
                    h.removeCallbacksAndMessages(null);
                    CookieManager.getInstance().flush();
                    l.onResult(a);
                } catch (Exception e) {
                    fail("extract_error", "Réponse du planning illisible.");
                }
            });
            return;
        }
        if (now - startedAt > timeoutMs) {
            fail(mfaSeen ? "mfa_timeout" : "timeout",
                    mfaSeen ? "La validation Authenticator n'a pas été faite à temps."
                            : "Connexion bloquée sur " + (lastHost.isEmpty() ? "le portail" : lastHost) + ".");
            return;
        }
        String url = wv.getUrl() == null ? "" : wv.getUrl();
        lastHost = host(url);
        boolean msLogin = lastHost.endsWith("login.microsoftonline.com") || lastHost.endsWith("login.live.com");
        String prefix = msLogin
                ? "var __E=" + JSONObject.quote(email) + ",__P=" + JSONObject.quote(pwd) + ";"
                : "var __E='',__P='';";
        wv.evaluateJavascript(prefix + driverJs, v -> handle(decode(v)));
    }

    private void handle(String json) {
        if (done) return;
        String s = "wait", n = "", m = "";
        try {
            JSONObject o = new JSONObject(json);
            s = o.optString("s", "wait");
            n = o.optString("n", "");
            m = o.optString("m", "");
        } catch (Exception ignored) { }
        switch (s) {
            case "ready":
                if (!extract) {
                    done = true;
                    h.removeCallbacksAndMessages(null);
                    CookieManager.getInstance().flush();
                    l.onReady();
                    return;
                }
                status("Lecture du planning…");
                extracting = true;
                extractStartedAt = System.currentTimeMillis();
                wv.evaluateJavascript(extractJs + "(" + weeks + ")", null);
                next(4000);
                return;
            case "sso":
                status("Connexion Microsoft…");
                break;
            case "menu":
                status("Ouverture de « Mon Planning »…");
                break;
            case "email_sub":
                emailSubmits++;
                status("Saisie de l'e-mail…");
                if (emailSubmits > 3) { fail("bad_credentials", "E-mail refusé par Microsoft."); return; }
                break;
            case "pwd_sub":
                pwdSubmits++;
                status("Saisie du mot de passe…");
                if (pwdSubmits > 3) { fail("bad_credentials", "Mot de passe refusé par Microsoft."); return; }
                break;
            case "bad":
                fail("bad_credentials", "Microsoft : " + m);
                return;
            case "mfa":
                if (!mfaSeen) {
                    mfaSeen = true;
                    // On laisse au moins 3 minutes pour approuver.
                    startedAt = Math.max(startedAt, System.currentTimeMillis() - timeoutMs + 180_000);
                    l.onMfa(n);
                }
                status(n.isEmpty() ? "Approuve la connexion dans Authenticator…"
                        : "Approuve dans Authenticator : numéro " + n);
                break;
            case "need_user":
                if (extract) {
                    fail("need_user", m.equals("code")
                            ? "Microsoft demande un code de vérification."
                            : "Identifiants manquants : ouvre l'app pour les enregistrer.");
                    return;
                }
                status(m.equals("code") ? "Saisis le code demandé par Microsoft." : "Saisis tes identifiants.");
                break;
            case "kmsi":
                status("Connexion presque terminée…");
                break;
            default:
                break;
        }
        next(2000);
    }

    /** Crée un WebView invisible dimensionné (nécessaire au rendu du calendrier). */
    public static WebView headless(Context c) {
        WebView w = new WebView(c.getApplicationContext());
        int wd = 1280, ht = 2000;
        w.measure(View.MeasureSpec.makeMeasureSpec(wd, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ht, View.MeasureSpec.EXACTLY));
        w.layout(0, 0, wd, ht);
        return w;
    }
}
