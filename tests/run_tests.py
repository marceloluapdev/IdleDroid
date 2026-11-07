"""Testes no navegador (Playwright). Rode: python3 tests/run_tests.py [--fotos]

Também roda os testes da simulação (node tests/sim.test.js).
Adicione um caso_... para cada regra nova ou bug corrigido.
"""
import functools, http.server, os, shutil, subprocess, sys, threading
from playwright.sync_api import sync_playwright

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FOTOS = "--fotos" in sys.argv


def servidor():
    class Quieto(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a):
            pass
    h = functools.partial(Quieto, directory=RAIZ)
    s = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h)
    threading.Thread(target=s.serve_forever, daemon=True).start()
    return s, f"http://127.0.0.1:{s.server_address[1]}/"


def abrir(ctx, url, erros):
    p = ctx.new_page()
    p.on("pageerror", lambda e: erros.append(str(e)))
    # fontes externas podem falhar sem internet; não contam como erro do jogo
    p.on("console", lambda m: m.type == "error" and "Failed to load resource" not in m.text and erros.append(m.text))
    p.goto(url)
    p.wait_for_function("window.IdleAdmin && IdleAdmin.fight")
    return p


def view(p):
    return p.evaluate("IdleAdmin.fight.view")


def caso_luta_anda_sozinha(p):
    a = view(p)
    p.evaluate("IdleAdmin.skip(120e3)")
    p.wait_for_timeout(300)
    b = view(p)
    assert b["kills"] > a["kills"] and b["gold"] > a["gold"], (a, b)


def caso_troca_de_fase(p):
    p.evaluate("IdleAdmin.skip(20 * 60e3)")
    p.wait_for_timeout(200)
    assert view(p)["level"] >= 4, view(p)
    p.click("#btn-stages")
    p.click(".stage-btn >> nth=1")
    assert view(p)["stageId"] == "caverna"
    assert p.is_hidden("#stages")


def caso_tempo_fora_mostra_resumo(p):
    p.evaluate("IdleAdmin.away(2 * 3600e3)")
    p.wait_for_function("window.IdleAdmin && IdleAdmin.fight")
    assert p.is_visible("#away"), "resumo não apareceu"
    assert "monstros" in p.inner_text("#away-text")
    p.click("#away-ok")


def caso_save_sobrevive_ao_recarregar(p):
    p.evaluate("IdleAdmin.skip(5 * 60e3)")
    p.wait_for_timeout(200)
    antes = view(p)
    p.reload()
    p.wait_for_function("window.IdleAdmin && IdleAdmin.fight")
    depois = view(p)
    assert depois["gold"] >= antes["gold"] and depois["level"] >= antes["level"], (antes, depois)


def caso_sete_toques_em_nivel_ligam_o_admin(p):
    p.evaluate("localStorage.removeItem('idle.admin')")
    p.goto(p.url.split("?")[0])
    p.wait_for_function("window.IdleAdmin && IdleAdmin.fight")
    assert p.locator(".admin").count() == 0
    for _ in range(7):
        p.click(".hud-row.small span >> nth=0")
    p.wait_for_function("window.IdleAdmin && IdleAdmin.fight")
    p.wait_for_selector(".admin")


def main():
    r = subprocess.run(["node", os.path.join(RAIZ, "tests/sim.test.js")], capture_output=True, text=True)
    print(r.stdout.strip().splitlines()[-1])
    falhas = 0 if r.returncode == 0 else 1
    if falhas:
        print(r.stdout)

    s, url = servidor()
    exe = shutil.which("google-chrome")
    with sync_playwright() as pw:
        browser = pw.chromium.launch(executable_path=exe) if exe else pw.chromium.launch()
        casos = [(n, f) for n, f in globals().items() if n.startswith("caso_")]
        for nome, fn in casos:
            erros = []
            ctx = browser.new_context(viewport={"width": 400, "height": 760}, device_scale_factor=2)
            p = abrir(ctx, url + "?admin", erros)
            try:
                fn(p)
                assert not erros, erros
                print("ok   ", nome)
            except AssertionError as e:
                falhas += 1
                print("FALHA", nome, e)
            if FOTOS:
                p.wait_for_timeout(1200)
                p.screenshot(path=f"/tmp/{nome}.png")
            ctx.close()
        browser.close()
    s.shutdown()
    print(f"{falhas} falha(s)" if falhas else "tudo passou")
    sys.exit(1 if falhas else 0)


if __name__ == "__main__":
    main()
