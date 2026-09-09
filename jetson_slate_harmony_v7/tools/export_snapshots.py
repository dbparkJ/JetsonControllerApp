"""Compatibility entry point: render_screens.py now renders and exports in one pass."""
from pathlib import Path
import subprocess,sys
if __name__=='__main__':
 subprocess.run([sys.executable,str(Path(__file__).with_name('render_screens.py'))],check=True)
