"""t2m_gen.py — run MoMask's gen_t2m.py without its matplotlib preview videos (that code breaks on current matplotlib
and would abort before the joint arrays are saved). Run with the MoMask venv, cwd = vendor/momask-codes, same args
as gen_t2m.py (see text2motion.sh)."""
import os, runpy, sys

sys.path.insert(0, os.getcwd())
import utils.plot_script as ps  # noqa: E402  (MoMask module)

ps.plot_3d_motion = lambda *a, **k: None
sys.argv = ['gen_t2m.py'] + sys.argv[1:]
runpy.run_path('gen_t2m.py', run_name='__main__')
