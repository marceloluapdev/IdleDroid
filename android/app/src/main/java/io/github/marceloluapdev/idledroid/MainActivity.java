package io.github.marceloluapdev.idledroid;

import android.app.PictureInPictureParams;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

/**
 * Tela do app. Além do jogo (WebView do Capacitor), cuida da janelinha (Picture-in-Picture):
 * - ao sair do app (botão home ou gesto), entra na janelinha sozinho;
 * - o botão "Janelinha" do jogo também entra (ver JanelinhaPlugin);
 * - avisa o jogo com o evento "janelinha" ({ativa: true/false}) para ele esconder os menus;
 * - mantém o WebView desenhando enquanto está na janelinha.
 */
public class MainActivity extends BridgeActivity {

    // Mesma proporção do palco do jogo (160x100)
    private static final Rational PROPORCAO = new Rational(16, 10);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(JanelinhaPlugin.class);
        super.onCreate(savedInstanceState);
        atualizarJanelinha();
    }

    boolean janelinhaSuportada() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private PictureInPictureParams parametros() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(PROPORCAO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: entra na janelinha suavemente ao sair do app
            b.setAutoEnterEnabled(true);
            b.setSeamlessResizeEnabled(false);
        }
        return b.build();
    }

    private void atualizarJanelinha() {
        if (janelinhaSuportada()) setPictureInPictureParams(parametros());
    }

    /** Chamado pelo botão "Janelinha" do jogo. */
    boolean entrarNaJanelinha() {
        if (!janelinhaSuportada()) return false;
        try {
            return enterPictureInPictureMode(parametros());
        } catch (IllegalStateException e) {
            return false;
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Até o Android 11 não existe entrada automática: entra aqui ao apertar home
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) entrarNaJanelinha();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean ativa, Configuration novaConfig) {
        super.onPictureInPictureModeChanged(ativa, novaConfig);
        if (bridge != null) bridge.triggerWindowJSEvent("janelinha", "{ \"ativa\": " + ativa + " }");
        if (ativa) manterDesenhando();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Na janelinha a tela fica "pausada" para o Android, mas continua visível:
        // o jogo precisa seguir desenhando a luta.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) manterDesenhando();
    }

    private void manterDesenhando() {
        if (bridge == null) return;
        WebView w = bridge.getWebView();
        if (w == null) return;
        w.onResume();
        w.resumeTimers();
    }
}
