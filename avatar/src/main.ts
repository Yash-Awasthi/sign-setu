// Entry for the app's WebView: window.signsetu takes commands, window.Android hears back.
import { AvatarStage, type Clip, type Play, type View } from "./stage";

declare global {
  interface Window {
    signsetu: { play(p: Play): void; clip(c: Clip): void; view(v: View): void };
    Android?: { ready(): void; finished(token: number): void };
  }
}

const canvas = document.querySelector("canvas")!;
const stage = new AvatarStage(canvas, (token) => window.Android?.finished(token));
window.signsetu = { play: (p) => stage.play(p), clip: (c) => stage.clip(c), view: (v) => stage.setView(v) };
const fit = () => stage.resize(canvas.clientWidth, canvas.clientHeight);
new ResizeObserver(fit).observe(canvas);
fit();
void stage.load("model.vrm").then(() => window.Android?.ready());
