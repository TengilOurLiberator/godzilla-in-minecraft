"""Starts this instance's flat test client with the frame-rate cap removed (testclient.py caps it at 60), for measuring.

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\population\\fastclient.py        (stop the client first: dev.ps1 down is
    fine; the server keeps running when you only run `testclient.py stop`)
The population module logs "GODZILLA-POP client fps=... people=... vehicles=..." every 5 s into the client log.
"""
import os
import sys

TOOLS = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, TOOLS)
import testclient  # noqa: E402

_orig = testclient.options
testclient.options = lambda w, h: _orig(w, h).replace("maxFps:60", "maxFps:1000")
sys.argv = ["testclient.py"] + (sys.argv[1:] or ["start"])
testclient.main()
