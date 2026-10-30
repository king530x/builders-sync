package fr.dadouch.builderssync;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/** Connexion visible : l'app remplit les identifiants, tu valides Authenticator si demandé. */
public class LoginActivity extends Activity {
    private PortalSession ps;
    private WebView wv;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        TextView status = new TextView(this);
        int p = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        status.setPadding(p, p, p, p);
        status.setTextSize(15);
        status.setText("Connexion…");
        wv = new WebView(this);
        root.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(wv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        ps = new PortalSession(this, wv, false, 15 * 60_000L, 0, new PortalSession.Listener() {
            public void onStatus(String s) { status.setText(s); }
            public void onMfa(String n) {
                status.setText(n.isEmpty() ? "Approuve la connexion dans Microsoft Authenticator."
                        : "Dans Microsoft Authenticator, saisis le numéro " + n + ".");
            }
            public void onReady() {
                Toast.makeText(LoginActivity.this, "Connecté ! Synchronisation lancée.", Toast.LENGTH_LONG).show();
                Store.clearError(LoginActivity.this);
                Notif.cancelError(LoginActivity.this);
                SyncWorker.runNow(LoginActivity.this);
                finish();
            }
            public void onResult(JSONArray a) { }
            public void onError(String code, String m) { status.setText("⚠ " + m); }
        });
        ps.start();
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (ps != null) ps.cancel();
        if (wv != null) wv.destroy();
        super.onDestroy();
    }
}
