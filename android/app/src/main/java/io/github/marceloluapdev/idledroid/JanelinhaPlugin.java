package io.github.marceloluapdev.idledroid;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Ponte entre o jogo (JS) e a janelinha nativa. No JS: Capacitor.nativePromise("Janelinha", "entrar"). */
@CapacitorPlugin(name = "Janelinha")
public class JanelinhaPlugin extends Plugin {

    @PluginMethod
    public void suportada(PluginCall call) {
        JSObject r = new JSObject();
        r.put("suportada", ((MainActivity) getActivity()).janelinhaSuportada());
        call.resolve(r);
    }

    @PluginMethod
    public void entrar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            JSObject r = new JSObject();
            r.put("entrou", ((MainActivity) getActivity()).entrarNaJanelinha());
            call.resolve(r);
        });
    }
}
