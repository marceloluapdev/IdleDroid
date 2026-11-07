package io.github.marceloluapdev.idledroid;

import android.Manifest;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.webkit.WebView;

import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;

/**
 * Tela do app. Além do jogo (WebView do Capacitor), cuida da janelinha (Picture-in-Picture):
 * - ao sair do app (botão home ou gesto), entra na janelinha sozinho;
 * - o botão "Janelinha" do jogo também entra (ver JanelinhaPlugin);
 * - avisa o jogo com o evento "janelinha" ({ativa: true/false}) para ele esconder os menus;
 * - mantém o WebView desenhando enquanto está na janelinha.
 * Também liga/desliga o modo flutuante (FlutuanteService): ao voltar para o jogo, ele fecha.
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

    // Enquanto o modo flutuante está ligado, sair do app não abre a janelinha
    private boolean flutuando = false;

    private PictureInPictureParams parametros() {
        PictureInPictureParams.Builder b = new PictureInPictureParams.Builder().setAspectRatio(PROPORCAO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: entra na janelinha suavemente ao sair do app
            b.setAutoEnterEnabled(!flutuando);
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !flutuando) entrarNaJanelinha();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean ativa, Configuration novaConfig) {
        super.onPictureInPictureModeChanged(ativa, novaConfig);
        if (bridge != null) bridge.triggerWindowJSEvent("janelinha", "{ \"ativa\": " + ativa + " }");
        if (ativa) manterDesenhando();
    }

    /** Pede a permissão de notificação (Android 13+). Sem ela o modo flutuante funciona, só sem notificação. */
    void pedirNotificacao() {
        if (Build.VERSION.SDK_INT >= 33
            && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 7);
        }
    }

    /** Liga o modo flutuante e manda o jogo para trás. */
    boolean flutuar() {
        if (!FlutuanteService.permitido(this)) return false;
        flutuando = true;
        atualizarJanelinha();
        ContextCompat.startForegroundService(this, new Intent(this, FlutuanteService.class));
        moveTaskToBack(true);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Voltou para o jogo: fecha o modo flutuante e reativa a janelinha automática
        if (flutuando) {
            flutuando = false;
            atualizarJanelinha();
        }
        stopService(new Intent(this, FlutuanteService.class));
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
