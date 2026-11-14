#!/usr/bin/env python3
"""Entry point: python3 -I tools/build_dictionary.py [--limit N] [--out path]"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from pipeline.build import main  # noqa: E402

main()
