package io.github.marceloluapdev.idledroid;

import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ponte entre o jogo (JS) e as partes nativas: janelinha (PiP) e modo flutuante.
 * No JS: Capacitor.nativePromise("Janelinha", "<método>", {...}).
 */
@CapacitorPlugin(name = "Janelinha")
public class JanelinhaPlugin extends Plugin {

    private MainActivity tela() {
        return (MainActivity) getActivity();
    }

    @PluginMethod
    public void suportada(PluginCall call) {
        JSObject r = new JSObject();
        r.put("suportada", tela().janelinhaSuportada());
        call.resolve(r);
    }

    @PluginMethod
    public void entrar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            JSObject r = new JSObject();
            r.put("entrou", tela().entrarNaJanelinha());
            call.resolve(r);
        });
    }

    /** Guarda o save para o modo flutuante ler (o jogo chama a cada salvamento). */
    @PluginMethod
    public void guardar(PluginCall call) {
        FlutuanteService.guardar(getContext(), call.getString("save"), call.getString("relogio"));
        call.resolve();
    }

    @PluginMethod
    public void flutuantePermitido(PluginCall call) {
        JSObject r = new JSObject();
        r.put("permitido", FlutuanteService.permitido(getContext()));
        call.resolve(r);
    }

    @PluginMethod
    public void pedirNotificacao(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            tela().pedirNotificacao();
            call.resolve();
        });
    }

    /** Abre a tela do sistema para aplicar o papel de parede animado do IdleDroid. */
    @PluginMethod
    public void papelDeParede(PluginCall call) {
        Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(getContext(), IdleWallpaperService.class));
        try {
            getActivity().startActivity(i);
        } catch (ActivityNotFoundException e) {
            // Alguns celulares só têm a lista geral de papéis de parede animados
            try {
                getActivity().startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (ActivityNotFoundException e2) {
                call.reject("Este celular não abriu a tela de papel de parede animado.");
                return;
            }
        }
        call.resolve();
    }

    /** Abre a tela do sistema "Sobrepor a outros apps" deste app. */
    @PluginMethod
    public void pedirPermissaoFlutuante(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getContext().getPackageName()));
        getActivity().startActivity(i);
        call.resolve();
    }

    /** Guarda o save, liga o modo flutuante e manda o jogo para trás. */
    @PluginMethod
    public void flutuar(PluginCall call) {
        FlutuanteService.guardar(getContext(), call.getString("save"), call.getString("relogio"));
        getActivity().runOnUiThread(() -> {
            JSObject r = new JSObject();
            r.put("flutuando", tela().flutuar());
            call.resolve(r);
        });
    }
}
