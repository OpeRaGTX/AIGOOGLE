import librosa, numpy as np, json, sys
y, sr = librosa.load(sys.argv[1], sr=44100, mono=True)
tempo, beats = librosa.beat.beat_track(y=y, sr=sr, units='time')
on_env = librosa.onset.onset_strength(y=y, sr=sr)
onsets = librosa.onset.onset_detect(onset_envelope=on_env, sr=sr, units='time')
# low-end (kick) energy
S = np.abs(librosa.stft(y)); f = librosa.fft_frequencies(sr=sr)
low = S[f < 150].sum(0); low /= low.max()
t = librosa.frames_to_time(np.arange(len(low)), sr=sr)
rms = librosa.feature.rms(y=y)[0]; rms /= rms.max()
print('tempo', tempo, 'n beats', len(beats))
print('beats', np.round(beats, 3).tolist())
# loudness per 0.5s
for i in range(0, 60):
    a, b = i*0.5, i*0.5+0.5
    m = (t >= a) & (t < b)
    if m.any(): print(f'{a:5.1f} rms {rms[m].mean():.2f} low {low[m].mean():.2f}', '#'*int(rms[m].mean()*40))
strong = onsets[np.argsort(-on_env[librosa.time_to_frames(onsets, sr=sr)])[:40]]
print('strong onsets', sorted(np.round(strong, 2).tolist()))
