package io.github.marceloluapdev.idledroid;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.core.app.NotificationCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * Modo flutuante: a luta por cima dos outros apps, com fundo transparente.
 * - Precisa da permissão "Sobrepor a outros apps" (SYSTEM_ALERT_WINDOW).
 * - Roda como serviço em primeiro plano, com notificação (tocar abre o jogo; "Fechar" encerra).
 * - Mostra flutuante.html num WebView transparente; o save chega pela "Ponte" (SharedPreferences),
 *   gravado pelo jogo. A luta é recalculada a partir dele (simulação determinística).
 * - Arrastar move a janela; um toque abre o jogo.
 */
public class FlutuanteService extends Service {

    static final String ACAO_FECHAR = "io.github.marceloluapdev.idledroid.FECHAR_FLUTUANTE";
    private static final String CANAL = "flutuante";
    private static final String PREFS = "idledroid";
    private static final int NOTIFICACAO = 1;

    private WindowManager wm;
    private FrameLayout caixa;
    private WebView web;
    private WindowManager.LayoutParams lp;

    static boolean permitido(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c);
    }

    static void guardar(Context c, String save, String relogio) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString("save", save)
            .putString("relogio", relogio == null ? "0" : relogio)
            .apply();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACAO_FECHAR.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        iniciarPrimeiroPlano();
        if (caixa == null && permitido(this)) mostrar();
        if (caixa == null) stopSelf();
        return START_NOT_STICKY;
    }

    private void iniciarPrimeiroPlano() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CANAL) == null) {
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Herói flutuante", NotificationManager.IMPORTANCE_LOW));
        }
        int imutavel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent abrir = PendingIntent.getActivity(this, 0, intentAbrirJogo(),
            imutavel | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent fechar = PendingIntent.getService(this, 1,
            new Intent(this, FlutuanteService.class).setAction(ACAO_FECHAR), imutavel);
        Notification n = new NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("IdleDroid")
            .setContentText("O herói está lutando. Toque para abrir o jogo.")
            .setOngoing(true)
            .setContentIntent(abrir)
            .addAction(0, "Fechar", fechar)
            .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICACAO, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICACAO, n);
        }
    }

    private Intent intentAbrirJogo() {
        return new Intent(this, MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
    }

    @SuppressLint({ "SetJavaScriptEnabled", "ClickableViewAccessibility" })
    private void mostrar() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int largura = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 0.9f);
        int altura = largura * 52 / 160; // proporção do palco flutuante (160x52, Player.H_FLUTUANTE)
        int tipo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(largura, altura, tipo,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = (dm.widthPixels - largura) / 2;
        lp.y = dm.heightPixels / 2;

        // A caixa recebe todos os toques (o WebView só desenha)
        caixa = new FrameLayout(this) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                return true;
            }
        };
        caixa.setOnTouchListener(new View.OnTouchListener() {
            private float iniX, iniY;
            private int posX, posY;
            private boolean moveu;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        iniX = e.getRawX(); iniY = e.getRawY();
                        posX = lp.x; posY = lp.y; moveu = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - iniX, dy = e.getRawY() - iniY;
                        if (Math.abs(dx) + Math.abs(dy) > 24) moveu = true;
                        if (moveu) {
                            lp.x = posX + (int) dx;
                            lp.y = posY + (int) dy;
                            wm.updateViewLayout(caixa, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moveu) startActivity(intentAbrirJogo());
                        return true;
                    default:
                        return false;
                }
            }
        });

        final WebViewAssetLoader assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assets.shouldInterceptRequest(request.getUrl());
            }
        });
        web.addJavascriptInterface(new Ponte(getSharedPreferences(PREFS, MODE_PRIVATE)), "Ponte");
        web.loadUrl("https://appassets.androidplatform.net/assets/public/flutuante.html");

        caixa.addView(web, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        wm.addView(caixa, lp);
    }

    @Override
    public void onDestroy() {
        if (caixa != null) {
            try { wm.removeView(caixa); } catch (Exception ignored) { }
            caixa = null;
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** O que o flutuante.html pode ler do app: só o save e o ajuste de relógio do menu de teste. */
    static class Ponte {
        private final SharedPreferences prefs;

        Ponte(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @JavascriptInterface
        public String save() {
            return prefs.getString("save", null);
        }

        @JavascriptInterface
        public String relogio() {
            return prefs.getString("relogio", "0");
        }
    }
}
