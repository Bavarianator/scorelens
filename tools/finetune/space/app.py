"""Gradio-Space, der train_hf.py im Hintergrund startet und das Log anzeigt (kostenloser CPU-Space).
Der Space hat keinen dauerhaften Speicher: train_hf.py lädt last.pt regelmäßig ins OUTPUT_REPO und setzt nach
einem Neustart dort fort. Ist metrics.json im OUTPUT_REPO vorhanden, wird nicht erneut trainiert."""
import os, subprocess, threading
import gradio as gr

LOG = "/tmp/train.log"


def run():
    with open(LOG, "w") as f:
        f.write("Training startet …\n"); f.flush()
        subprocess.run(["python", "-u", "train_hf.py"], stdout=f, stderr=subprocess.STDOUT)
        f.write("\n[train_hf.py beendet]\n")


threading.Thread(target=run, daemon=True).start()


def tail():
    try:
        with open(LOG) as f:
            return f.read()[-20000:]
    except FileNotFoundError:
        return "startet …"


with gr.Blocks(title="Scorelens Finetune") as demo:
    gr.Markdown(f"# Scorelens Finetune\nDatensatz `{os.environ.get('DATASET_REPO', '?')}` → Modell `{os.environ.get('OUTPUT_REPO', '?')}`")
    out = gr.Textbox(label="Trainingslog", lines=30, value=tail)
    timer = gr.Timer(15)
    timer.tick(tail, None, out)
    gr.Button("Aktualisieren").click(tail, None, out)

demo.launch(server_name="0.0.0.0", server_port=int(os.environ.get("PORT", 7860)))
