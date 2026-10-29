import librosa, numpy as np, json, sys
y, sr = librosa.load(sys.argv[1], sr=44100, mono=True)
yl = librosa.effects.preemphasis(y, coef=-0.97)  # emphasize lows
S = np.abs(librosa.stft(y, n_fft=2048, hop_length=256)); f = librosa.fft_frequencies(sr=sr, n_fft=2048)
low = S[(f > 30) & (f < 130)].sum(0)
flux = np.maximum(0, np.diff(low, prepend=low[0])); flux /= flux.max()
t = librosa.frames_to_time(np.arange(len(flux)), sr=sr, hop_length=256)
pk = librosa.util.peak_pick(flux, pre_max=8, post_max=8, pre_avg=20, post_avg=20, delta=0.12, wait=30)
kicks = [(round(float(t[i]), 3), round(float(flux[i]), 2)) for i in pk]
print(len(kicks)); print(kicks)
hi = S[(f > 2000) & (f < 9000)].sum(0); hf = np.maximum(0, np.diff(hi, prepend=hi[0])); hf /= hf.max()
ph = librosa.util.peak_pick(hf, pre_max=6, post_max=6, pre_avg=15, post_avg=15, delta=0.2, wait=20)
snares = [(round(float(t[i]), 3), round(float(hf[i]), 2)) for i in ph]
print(len(snares)); print(snares)
json.dump({'kicks': kicks, 'snares': snares}, open(sys.argv[2], 'w'))
